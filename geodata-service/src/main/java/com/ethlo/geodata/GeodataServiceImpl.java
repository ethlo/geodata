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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.validation.Valid;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.util.CloseableIterator;
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
import com.ethlo.geodata.util.MemoryUsageUtil;
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
    private final RtreeRepository rTree;

    private final List<String> additionalIndexedFeatures;
    private final int qualityConstant;

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
        this.rTree = new RtreeRepository(boundaryDao);
        this.additionalIndexedFeatures = additionalIndexedFeatures;
        this.qualityConstant = qualityConstant;
    }

    @Override
    public GeoLocation findByIp(InetAddress ip)
    {
        return ipDao.findByIp(ip).map(this::findById).orElseThrow(() -> new EmptyResultDataAccessException("Cannot find location for ip " + ip, 1));
    }

    @Override
    public GeoLocation findById(int geoNameId)
    {
        return doFindById(geoNameId);
    }

    private GeoLocation doFindById(int id)
    {
        return locationDao.get(id).map(this::populate).orElseThrow(() -> new EmptyResultDataAccessException("No location with id " + id, 1));
    }

    @Override
    public GeoLocation findWithin(Coordinates coordinate, int maxDistanceInKilometers)
    {
        return Optional.ofNullable(rTree.find(coordinate)).map(this::findById).orElse(null);
    }

    @Override
    public Page<GeoLocationDistance> findNear(Coordinates point, int maxDistanceInKilometers, Pageable pageable)
    {
        final Map<Integer, Double> locations = rTree.getNearest(point, maxDistanceInKilometers, pageable);
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
        return (ids).stream().map(this::doFindById).collect(Collectors.toList());
    }

    @Override
    public byte[] findBoundaries(int id)
    {
        return boundaryDao.findGeoJsonById(id).orElse(null);
    }

    @Override
    public Page<GeoLocation> findChildren(int locationId, Pageable pageable)
    {
        final Node node = nodes.get(locationId);
        if (node == null)
        {
            throw new EmptyResultDataAccessException("No location with id " + locationId + " found", 1);
        }
        final long total = node.getChildren().size();
        final List<Integer> ids = node.getChildren().stream()
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        final List<GeoLocation> locations = findByIds(ids);
        locations.sort(Comparator.comparing(GeoLocation::getName));
        return new PageImpl<>(locations, pageable, total);
    }

    @PostConstruct
    public void load()
    {
        load(new StatefulProgressListener());
    }

    public void load(LoadProgressListener progressListener)
    {
        metaDao.assertHasData();

        progressListener.begin("feature_codes", 1);
        this.featureCodes = featureCodeDao.load();

        progressListener.begin("time_zones", 1);
        this.timezones = HashBiMap.create(timeZoneDao.load());

        final Chronograph chronograph = Chronograph.create();
        chronograph.timed("Locations", () -> loadLocations(progressListener));
        chronograph.timed("Countries", () -> loadCountries(progressListener));
        chronograph.timed("Continents", () -> loadContinents(progressListener));
        chronograph.timed("SearchIndex", () -> loadSearchIndex(progressListener));
        chronograph.timed("Hierarchy", () -> loadHierarchy(progressListener));

        logger.info("Data loaded successfully");

        logger.info(chronograph.prettyPrint());
    }

    private void loadLocations(final LoadProgressListener progressListener)
    {
        logger.info("Loading locations");
        progressListener.begin("load_locations");
        final int locationCount = locationDao.load();
        logger.info("Loaded {} locations", locationCount);
        MemoryUsageUtil.dumpMemUsage("After locations loaded");
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
        MemoryUsageUtil.dumpMemUsage("Location hierarchy loaded");

        progressListener.begin("join_admin_levels");
        joinHierarchyNodes(childToParent, progressListener::progress);
        progressListener.end();

    }

    private void loadSearchIndex(final LoadProgressListener progressListener)
    {
        logger.info("Loading search index");
        int count = 0;
        progressListener.begin("load_search_index");
        try (final CloseableIterator<RawLocation> locationIter = locationDao.iterator())
        {
            while (locationIter.hasNext())
            {
                final RawLocation location = locationIter.next();
                addToSearchIndex(location);
                progressListener.progress(count);
            }
        }
        progressListener.end();
        logger.info("Search index loaded with {} entries", locationsByName.size());
        MemoryUsageUtil.dumpMemUsage("Search-index loaded");
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
        return Lists.reverse(getPath(id).stream().skip(1).map(this::findById).collect(Collectors.toList())).stream().skip(1).collect(Collectors.toList());
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
                .map(this::doFindById)
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
        final Set<Integer> path = new LinkedHashSet<>();
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
    public GeoLocation findByCoordinate(Coordinates point, int distance)
    {
        GeoLocation location = findWithin(point, distance);

        // Fall back to nearest match
        if (location == null)
        {
            final Page<GeoLocationDistance> nearest = findNear(point, distance, PageRequest.of(0, 1));
            location = nearest.hasContent() ? nearest.getContent().get(0).getLocation() : null;
        }

        if (location != null)
        {
            return location;
        }
        throw new EmptyResultDataAccessException("Cannot find a location for position lat=" + point.getLat() + ", lng=" + point.getLng(), 1);
    }

    @Override
    public byte[] findBoundaries(final int id, @Valid View view)
    {
        final byte[] fullWkb = this.findBoundaries(id);

        if (fullWkb == null)
        {
            return null;
        }

        final WKBReader reader = new WKBReader();
        try
        {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            final Geometry full = reader.read(fullWkb);
            Geometry simplified = GeometryUtil.simplify(full, view, qualityConstant);
            final Geometry clipped = GeometryUtil.clip(new Envelope(view.getMinLng(), view.getMaxLng(), view.getMinLat(), view.getMaxLat()), simplified);
            if (clipped != null)
            {
                simplified = clipped;
            }

            logger.debug("locationId: {}, original points: {}, remaining points: {}, ratio: {}, elapsed: {}", id, full.getNumPoints(), simplified.getNumPoints(), full.getNumPoints() / (double) simplified.getNumPoints(), stopwatch);
            return new WKBWriter().write(simplified);
        }
        catch (ParseException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }
    }

    @Override
    public byte[] findBoundaries(final int id, double maxTolerance)
    {
        final byte[] fullWkb = this.findBoundaries(id);

        if (fullWkb == null)
        {
            return null;
        }

        final WKBReader reader = new WKBReader();
        try
        {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            final Geometry full = reader.read(fullWkb);
            final Geometry simplified = GeometryUtil.simplify(full, maxTolerance);
            logger.debug("locationId: {}, original points: {}, remaining points: {}, ratio: {}, elapsed: {}", id, full.getNumPoints(), simplified.getNumPoints(), full.getNumPoints() / (double) simplified.getNumPoints(), stopwatch);
            return new WKBWriter().write(simplified);
        }
        catch (ParseException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }
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
                    .map(this::doFindById)
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