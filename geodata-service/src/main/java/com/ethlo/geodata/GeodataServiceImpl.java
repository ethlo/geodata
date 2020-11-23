package com.ethlo.geodata;

/*-
 * #%L
 * geodata
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.dao.BoundaryDao;
import com.ethlo.geodata.dao.CountryDao;
import com.ethlo.geodata.dao.FeatureCodeDao;
import com.ethlo.geodata.dao.HierarchyDao;
import com.ethlo.geodata.dao.IpDao;
import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.dao.MetaDao;
import com.ethlo.geodata.dao.TimeZoneDao;
import com.ethlo.geodata.dao.file.RtreeRepository;
import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountrySummary;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.MapFeature;
import com.ethlo.geodata.model.RawLocation;
import com.ethlo.geodata.model.View;
import com.ethlo.geodata.progress.StatefulProgressListener;
import com.ethlo.geodata.progress.StepProgressListener;
import com.ethlo.geodata.util.GeometryUtil;
import com.ethlo.time.Chronograph;
import com.google.common.base.Stopwatch;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.SmartArrayBasedNodeFactory;
import com.illucit.util.ASCIIUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

@Lazy
@Service
public class GeodataServiceImpl implements GeodataService
{
    private final Logger logger = LoggerFactory.getLogger(GeodataServiceImpl.class);
    private final RadixTree<int[]> locationsByName = new ConcurrentRadixTree<>(new SmartArrayBasedNodeFactory());
    private final LocationDao locationDao;
    private final IpDao ipDao;
    private final HierarchyDao hierarchyDao;
    private final FeatureCodeDao featureCodeDao;
    private final TimeZoneDao timeZoneDao;
    private final CountryDao countryDao;
    private final BoundaryDao boundaryDao;
    private final MetaDao metaDao;
    private final List<String> additionalIndexedFeatures;
    private final int qualityConstant;
    private RtreeRepository rtreeRepository;
    // Loaded data
    private Map<Integer, Node> nodes = new Int2ObjectOpenHashMap<>();
    private BiMap<String, Integer> timezones;
    private Map<String, Country> countries;
    private List<Continent> continents = new LinkedList<>();
    private Map<Integer, MapFeature> featureCodes = new HashMap<>();

    public GeodataServiceImpl(final LocationDao locationDao, final IpDao ipDao, final HierarchyDao hierarchyDao,
                              final FeatureCodeDao featureCodeDao, final TimeZoneDao timeZoneDao, final CountryDao countryDao,
                              final BoundaryDao boundaryDao, final MetaDao metaDao,
                              @Value("${geodata.search.index-features}") final List<String> additionalIndexedFeatures,
                              @Value("${geodata.boundaries.quality}") final int qualityConstant)
    {
        this.locationDao = locationDao;
        this.ipDao = ipDao;
        this.hierarchyDao = hierarchyDao;
        this.featureCodeDao = featureCodeDao;
        this.timeZoneDao = timeZoneDao;
        this.countryDao = countryDao;
        this.boundaryDao = boundaryDao;
        this.metaDao = metaDao;
        this.additionalIndexedFeatures = additionalIndexedFeatures;
        this.qualityConstant = qualityConstant;
    }

    @Override
    public GeoLocation findByIp(InetAddress ip)
    {
        return ipDao.findByIp(ip).map(this::findById).orElseThrow(() -> new EmptyResultDataAccessException("Cannot find location for ip " + ip, 1));
    }

    @Override
    public GeoLocation findById(int id)
    {
        return locationDao.get(id).map(this::populate).orElseThrow(() -> new EmptyResultDataAccessException("No location with id " + id, 1));
    }

    @Override
    public Optional<LookupMetadata> findWithin(Coordinates coordinate, int maxDistanceInKilometers)
    {
        return Optional.ofNullable(rtreeRepository.find(coordinate)).map(e ->
                new LookupMetadata(findById(e.value().getId()), e.value().getSubdivideIndex(), e.value().getEnvelope()));
    }

    @Override
    public Page<GeoLocationDistance> findNear(Coordinates point, int maxDistanceInKilometers, Pageable pageable)
    {
        final Map<Integer, Double> locations = rtreeRepository.getNearest(point, maxDistanceInKilometers, pageable);
        final List<GeoLocationDistance> content = locations.entrySet().stream().map(e -> new GeoLocationDistance().setLocation(findById(e.getKey())).setDistance(e.getValue())).collect(Collectors.toList());
        return new PageImpl<>(content, pageable, locations.size());
    }

    @Override
    public List<GeoLocation> findByIds(Collection<Integer> ids)
    {
        if (ids.isEmpty())
        {
            return Collections.emptyList();
        }
        return (ids).stream().map(this::findById).collect(Collectors.toList());
    }

    @Override
    public Optional<Geometry> findBoundaries(int id)
    {
        return boundaryDao.findGeometryById(id);
    }

    @Override
    public Page<GeoLocation> findChildren(int locationId, final boolean matchLevel, Pageable pageable)
    {
        final GeoLocation self = findById(locationId);
        final List<Integer> all = getChildIds(locationId);
        final Optional<List<String>> subLevel = getSubLevel(self.getFeatureKey());
        final List<GeoLocation> locations = all
                .stream()
                .map(this::findById)
                .filter(l -> subLevel.map(s -> s.contains(l.getFeatureKey())).orElse(false))
                .collect(Collectors.toList());

        final long total = locations.size();

        final List<GeoLocation> content = locations
                .stream()
                .sorted(Comparator.comparing(GeoLocation::getName))
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public boolean hasRealChildren(final int id)
    {
        final GeoLocation self = findById(id);
        final List<Integer> all = getChildIds(id);
        final Optional<List<String>> subLevel = getSubLevel(self.getFeatureKey());

        return all
                .stream()
                .map(this::findById)
                .anyMatch(l -> subLevel.map(s -> s.contains(l.getFeatureKey())).orElse(false));
    }

    @Override
    public boolean hasBoundary(final int id)
    {
        return boundaryDao.hasGeometry(id);
    }

    private List<Integer> getChildIds(final int id)
    {
        final Node node = nodes.get(id);
        if (node == null)
        {
            throw new EmptyResultDataAccessException("No location with id " + id + " found", 1);
        }

        return node.getChildren();
    }

    private Optional<List<String>> getSubLevel(final String featureCode)
    {
        if (GeoConstants.CONTINENT_LEVEL_FEATURE.equals(featureCode))
        {
            return Optional.of(GeoConstants.COUNTRY_LEVEL_FEATURES);
        }
        else if (GeoConstants.COUNTRY_LEVEL_FEATURES.contains(featureCode))
        {
            return Optional.of(Collections.singletonList(GeoConstants.ADM1));
        }
        else if (GeoConstants.ADMINISTRATIVE_LEVEL_FEATURES.contains(featureCode))
        {
            final int selfIndex = GeoConstants.ADMINISTRATIVE_LEVEL_FEATURES.indexOf(featureCode);
            if (selfIndex < GeoConstants.ADMINISTRATIVE_LEVEL_FEATURES.size() - 1)
            {
                return Optional.of(Collections.singletonList(GeoConstants.ADMINISTRATIVE_LEVEL_FEATURES.get(selfIndex + 1)));
            }
        }
        return Optional.empty();
    }

    @PostConstruct
    public void load()
    {
        load(new StatefulProgressListener());
    }

    public void load(LoadProgressListener progressListener)
    {
        metaDao.assertHasData();
        final SourceDataInfoSet sourceDataInfo = metaDao.load();
        logger.info("{}", sourceDataInfo);

        progressListener.begin("feature_codes", 1);
        this.featureCodes = featureCodeDao.load();

        progressListener.begin("time_zones", 1);
        this.timezones = HashBiMap.create(timeZoneDao.load());

        final Chronograph chronograph = Chronograph.create();
        chronograph.timed("Locations", () -> loadLocations(progressListener));
        chronograph.timed("Geometry", () -> loadProximityTree(progressListener));
        chronograph.timed("Countries", () -> loadCountries(progressListener));
        chronograph.timed("Continents", () -> loadContinents(progressListener));
        chronograph.timed("SearchIndex", () -> loadSearchIndex(progressListener));
        chronograph.timed("Hierarchy", () -> loadHierarchy(progressListener));

        logger.info("Data loaded successfully");

        logger.info(chronograph.prettyPrint());
    }

    private void loadProximityTree(final LoadProgressListener progressListener)
    {
        final Set<Integer> featureCodesForProximity = featureCodes.entrySet().stream()
                .filter(e -> GeoConstants.ADMINISTRATIVE_OR_ABOVE.contains(e.getValue().getKey()))
                .map(Entry::getKey)
                .collect(Collectors.toSet());
        rtreeRepository = new RtreeRepository(locationDao, boundaryDao, featureCodesForProximity);
    }

    private void loadLocations(final LoadProgressListener progressListener)
    {
        logger.info("Loading locations");
        progressListener.begin("load_locations");
        final int locationCount = locationDao.load();
        logger.info("Loaded {} locations", locationCount);
    }

    private void loadCountries(final LoadProgressListener progressListener)
    {
        progressListener.begin("countries", null);
        this.countries = new LinkedHashMap<>();
        final List<Country> countryList = countryDao.load();
        countryList.sort(Comparator.comparing(Country::getName));
        for (final Country country : countryList)
        {
            countries.put(country.getCountryCode(), country);
        }
        progressListener.end();
    }

    private void loadContinents(final LoadProgressListener progressListener)
    {
        progressListener.begin("continents", 1);
        continents = findByIds(GeoConstants.CONTINENTS.values())
                .stream()
                .map(l -> new Continent(getContinentCode(l.getId()), l))
                .collect(Collectors.toList());
    }

    private void loadHierarchy(final LoadProgressListener progressListener)
    {
        progressListener.begin("load_hierarchy_data");
        final Map<Integer, Integer> childToParent = hierarchyDao.load();
        logger.info("Loaded {} hierarchy references", childToParent.size());
        progressListener.end();

        progressListener.begin("join_admin_levels");
        joinHierarchyNodes(childToParent, progressListener::progress);
        progressListener.end();
    }

    private void loadSearchIndex(final LoadProgressListener progressListener)
    {
        logger.info("Loading search index");
        int count = 0;
        progressListener.begin("load_search_index");
        final Iterator<RawLocation> locationIter = locationDao.stream().iterator();
        while (locationIter.hasNext())
        {
            final RawLocation location = locationIter.next();
            addToSearchIndex(location);
            progressListener.progress(count);
        }

        progressListener.end();
        logger.info("Search index loaded with {} entries", locationsByName.size());
    }

    private void addToSearchIndex(final RawLocation e)
    {
        final MapFeature featureType = featureCodes.get(e.getMapFeatureId());
        if (isSearchIndexed(featureType.getKey()))
        {
            final int id = e.getId();
            final String normalizedName = ASCIIUtils.foldToASCII(e.getName().toLowerCase());
            final int[] existing = locationsByName.putIfAbsent(normalizedName, new int[]{id});
            if (existing != null)
            {
                final int[] newArr = Arrays.copyOf(existing, existing.length + 1);
                newArr[newArr.length - 1] = id;
                locationsByName.put(normalizedName, newArr);
            }
        }
    }

    private boolean isSearchIndexed(final String key)
    {
        return GeoConstants.ADMINISTRATIVE_OR_ABOVE.contains(key) || additionalIndexedFeatures.contains(key);
    }

    @Override
    public List<GeoLocation> findPath(final int id)
    {
        final Collection<Integer> ids = getPath(id);
        final List<GeoLocation> path = new ArrayList<>(ids.size());
        for (int i : ids)
        {
            path.add(findById(i));
        }
        return path;
    }

    @Override
    public Page<Continent> findContinents()
    {
        return new PageImpl<>(continents, PageRequest.of(0, 7), 7);
    }

    private String getContinentCode(int id)
    {
        for (Entry<String, Integer> e : GeoConstants.CONTINENTS.entrySet())
        {
            if (e.getValue().equals(id))
            {
                return e.getKey();
            }
        }
        throw new IllegalArgumentException("Unknown continent ID: " + id);
    }

    @Override
    public Page<Country> findCountriesOnContinent(String continentCode, Pageable pageable)
    {
        final List<Country> onContinent = countries.values()
                .stream()
                .filter(c -> c.getContinentCode().equals(continentCode))
                .collect(Collectors.toList());
        return new PageImpl<>(onContinent.stream().skip(pageable.getOffset()).limit(pageable.getPageSize()).collect(Collectors.toList()), pageable, onContinent.size());

    }

    @Override
    public Page<Country> findCountries(Pageable pageable)
    {
        final List<Country> content = countries.values().stream().filter(c -> nodes.containsKey(c.getId())).skip(pageable.getOffset()).limit(pageable.getPageSize()).collect(Collectors.toList());
        return new PageImpl<>(content, pageable, countries.size());
    }

    @Override
    public Country findCountryByCode(String countryCode)
    {
        if (countryCode != null)
        {
            return countries.get(countryCode.toUpperCase());
        }
        return null;
    }

    @Override
    public Page<GeoLocation> findChildren(String countryCode, Pageable pageable)
    {
        final int id = Optional.ofNullable(countries.get(countryCode.toUpperCase()))
                .map(Country::getId)
                .orElseThrow(() -> new EmptyResultDataAccessException("No country code " + countryCode, 1));

        final List<Integer> childIds = Optional.ofNullable(nodes.get(id).getChildren()).orElse(Collections.emptyList());

        final List<GeoLocation> content = childIds.stream()
                .map(this::findById)
                .filter(l -> "ADM1".equals(l.getFeatureCode()))
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .sorted(Comparator.comparing(GeoLocation::getName))
                .collect(Collectors.toList());
        return new PageImpl<>(content, pageable, childIds.size());
    }

    private void joinHierarchyNodes(final Map<Integer, Integer> childToParent, StepProgressListener listener)
    {
        nodes = new Int2ObjectOpenHashMap<>(childToParent.size());
        childToParent.forEach((child, parent) ->
        {
            final Node childNode = nodes.computeIfAbsent(child, Node::new);
            final Node parentNode = nodes.computeIfAbsent(parent, Node::new);
            parentNode.addChild(child);
            childNode.setParent(parent);
            listener.progress(nodes.size(), childToParent.size());
        });
    }

    @Override
    public Country findByPhoneNumber(String phoneNumber)
    {
        String stripped = phoneNumber.replaceAll("[^\\d.]", "");
        stripped = stripped.replaceFirst("^0+(?!$)", "");

        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        try
        {
            // phone must begin with '+'
            final Phonenumber.PhoneNumber numberProto = phoneUtil.parse("+" + stripped, null);
            int countryCode = numberProto.getCountryCode();
            return countries.values().stream().filter(c -> c.getPhone().equals(Integer.toString(countryCode))).findFirst().orElse(null);
        }
        catch (NumberParseException e)
        {
            return null;
        }
    }

    @Override
    public boolean isInsideAny(List<Integer> locations, int location)
    {
        final GeoLocation loc = findById(location);
        if (loc == null)
        {
            throw new EmptyResultDataAccessException("No such location found " + location, 1);
        }

        for (int l : locations)
        {
            if (l == location || isLocationInside(location, l))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isOutsideAll(List<Integer> locations, final int location)
    {
        final GeoLocation loc = findById(location);
        if (loc == null)
        {
            throw new EmptyResultDataAccessException("No such location found " + location, 1);
        }

        for (int l : locations)
        {
            if (isLocationInside(l, loc.getId()))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isLocationInside(final int locationId, final int suspectedParentId)
    {
        final Collection<Integer> path = getPath(locationId);
        return path.contains(suspectedParentId);
    }

    private Collection<Integer> getPath(final int id)
    {
        Node node = this.nodes.get(id);
        if (node == null)
        {
            throw new EmptyResultDataAccessException("No location with id " + id, 1);
        }
        final Deque<Integer> path = new LinkedBlockingDeque<>();
        while (node != null)
        {
            if (!path.add(node.getId()))
            {
                logger.warn("Circular reference for {}: {}", node.getId(), path);
                node = null;
            }
            else
            {
                final Integer parent = node.getParent();
                node = parent != null ? nodes.get(parent) : null;
            }
        }
        path.removeFirst();
        if (!path.isEmpty())
        {
            path.removeLast();
        }
        return path;
    }

    @Override
    public Continent findContinent(String continentCode)
    {
        final Integer id = GeoConstants.CONTINENTS.get(continentCode.toUpperCase());
        if (id != null)
        {
            return new Continent(continentCode.toUpperCase(), findById(id));
        }
        return null;
    }

    @Override
    public GeoLocation findParent(final int id)
    {
        final GeoLocation location = findById(id);
        return location.getParentLocationId() != null ? findById(location.getParentLocationId()) : null;
    }

    @Override
    public Optional<LookupMetadata> findByCoordinate(Coordinates point, int distance)
    {
        return findWithin(point, distance);
    }

    @Override
    public Optional<Geometry> findBoundaries(final int id, View view)
    {
        return findBoundaries(id).map(full ->
        {
            final Geometry clipped = GeometryUtil.clip(new Envelope(view.getMinLng(), view.getMaxLng(), view.getMinLat(), view.getMaxLat()), full);
            return GeometryUtil.simplify(Objects.requireNonNullElse(clipped, full), view, qualityConstant);
        });
    }

    @Override
    public Optional<Geometry> findBoundaries(final int id, double maxTolerance)
    {
        return this.findBoundaries(id).map(full ->
        {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            final Geometry simplified = GeometryUtil.simplify(full, maxTolerance);
            logger.debug("locationId: {}, original points: {}, remaining points: {}, ratio: {}, elapsed: {}", id, full.getNumPoints(), simplified.getNumPoints(), full.getNumPoints() / (double) simplified.getNumPoints(), stopwatch);
            return simplified;
        });
    }

    @Override
    public Slice<GeoLocation> findByName(String name, Pageable pageable)
    {
        final long queryLimit = pageable.getOffset() + pageable.getPageSize();
        final long skip = pageable.getOffset();
        final long size = pageable.getPageSize();

        final List<Integer> ids = getIds(name, queryLimit + 1);
        if (!ids.isEmpty())
        {
            final boolean hasMore = ids.size() > queryLimit;
            final List<GeoLocation> content = ids.stream()
                    .skip(skip)
                    .limit(size)
                    .map(this::findById)
                    .sorted(Comparator.comparingLong(GeoLocation::getPopulation))
                    .collect(Collectors.toList());
            return new SliceImpl<>(Lists.reverse(content), pageable, hasMore);
        }
        return new SliceImpl<>(Collections.emptyList(), pageable, false);
    }

    private List<Integer> getIds(final String name, final long max)
    {
        final List<Integer> ids = new LinkedList<>();
        for (int[] arr : locationsByName.getValuesForClosestKeys(ASCIIUtils.foldToASCII(name.toLowerCase())))
        {
            for (int id : arr)
            {
                ids.add(id);
                if (ids.size() == max)
                {
                    return ids;
                }
            }
        }
        return ids;
    }

    private GeoLocation populate(RawLocation l)
    {
        final Country country = findCountryByCode(l.getCountryCode());
        final CountrySummary countrySummary = country != null ? country.toSummary(l.getCountryCode()) : null;

        final int id = l.getId();
        final Node node = nodes.get(id);
        final Integer parentId = Optional.ofNullable(node).map(Node::getParent).orElse(null);

        final int featureCodeId = l.getMapFeatureId();
        final MapFeature mapFeature = featureCodeId != 0 ? featureCodes.get(featureCodeId) : null;

        final String timezone = timezones.inverse().get(l.getTimeZoneId());

        final GeoLocation result = new GeoLocation();
        result.setId(id);
        if (l.getPopulation() != 0)
        {
            result.setPopulation(l.getPopulation());
        }
        result.setName(l.getName());
        result.setCoordinates(l.getCoordinates());
        result.setCountry(countrySummary);
        result.setTimeZone(timezone);
        result.setParentLocationId(parentId);
        if (mapFeature != null)
        {
            result.setFeatureClass(mapFeature.getFeatureClass());
            result.setFeatureCode(mapFeature.getFeatureCode());
        }

        return result;
    }
}