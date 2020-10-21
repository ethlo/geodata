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

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.cache.SerializableSerializer;
import com.ethlo.geodata.dao.FeatureCodeDao;
import com.ethlo.geodata.dao.ReverseGeocodingDao;
import com.ethlo.geodata.dao.TimeZoneDao;
import com.ethlo.geodata.dao.jdbc.JdbcBoundaryDao;
import com.ethlo.geodata.dao.jdbc.JdbcIpDao;
import com.ethlo.geodata.dao.jdbc.JdbcLocationDao;
import com.ethlo.geodata.importer.GeonamesSource;
import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountrySummary;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.RawLocation;
import com.ethlo.geodata.model.View;
import com.ethlo.geodata.progress.StepProgressListener;
import com.ethlo.geodata.util.GeometryUtil;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.SmartArrayBasedNodeFactory;
import com.illucit.util.ASCIIUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;

@Service
public class GeodataServiceImpl implements GeodataService
{
    private final Logger logger = LoggerFactory.getLogger(GeodataServiceImpl.class);

    // Loaded data
    private Map<Integer, Node> nodes = new Int2ObjectAVLTreeMap<>();
    private Map<Integer, String> timezones;
    private Map<String, Country> countries;
    private List<Continent> continents = new LinkedList<>();
    private Map<Integer, MapFeature> featureCodes = new HashMap<>();
    private Map<String, Integer> reverseFeatureMap = new HashMap<>();
    private RadixTree<int[]> locationsByName = new ConcurrentRadixTree<>(new SmartArrayBasedNodeFactory());
    private Map<Integer, RawLocation> locations;

    @Autowired
    private GeoMetaService metaService;

    @Autowired
    private JdbcLocationDao locationDao;

    @Autowired
    private JdbcIpDao ipDao;

    @Autowired
    private ReverseGeocodingDao reverseGeocodingDao;

    @Autowired
    private JdbcBoundaryDao boundaryDao;

    @Autowired
    private FeatureCodeDao featureCodeDao;

    @Autowired
    private TimeZoneDao timeZoneDao;

    @Value("${geodata.boundaries.quality}")
    private int qualityConstant;

    private PersistentCacheManager persistentCacheManager = new PersistentCacheManager(Paths.get("/tmp"));

    @Override
    public GeoLocation findByIp(String ip)
    {
        return ipDao.findByIp(ip).map(this::findById).orElseThrow(() -> new EmptyResultDataAccessException("Cannot find location for ip " + ip, 1));
    }

    @Override
    public GeoLocation findById(int geoNameId)
    {
        return doFindById(geoNameId);
    }

    public GeoLocation doFindById(int id)
    {
        return Optional.ofNullable(locations.get(id)).map(this::populate).orElseThrow(() -> new EmptyResultDataAccessException("No location with id " + id, 1));
    }

    @Override
    public GeoLocation findWithin(Coordinates point, int maxDistanceInKilometers)
    {
        int range = 25;
        RawLocation location = null;
        while (range <= maxDistanceInKilometers && location == null)
        {
            location = reverseGeocodingDao.findContaining(point, range);
            range *= 2;
        }
        if (location != null)
        {
            return populate(location);
        }
        throw new EmptyResultDataAccessException("Cannot find location for " + point, 1);
    }

    @Override
    public Page<GeoLocationDistance> findNear(Coordinates point, int maxDistanceInKilometers, Pageable pageable)
    {
        final Map<Integer, Double> locations = reverseGeocodingDao.findNearest(point, maxDistanceInKilometers, pageable);
        final List<GeoLocationDistance> content = locations.entrySet().stream().map(e -> new GeoLocationDistance().setLocation(findById(e.getKey())).setDistance(e.getValue())).collect(Collectors.toList());
        return new PageImpl<>(content, pageable, metaService.getSourceDataInfo().get(GeonamesSource.LOCATION).getCount());
    }

    @Override
    public List<GeoLocation> findByIds(Collection<Integer> ids)
    {
        if (ids.isEmpty())
        {
            return Collections.emptyList();
        }
        return locationDao.findByIds(ids).stream().map(this::populate).collect(Collectors.toList());
    }

    @Override
    public byte[] findBoundaries(int id)
    {
        return boundaryDao.findById(id).orElseThrow(() -> new EmptyResultDataAccessException("No boundaries found for location " + id, 1));
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

    @Override
    public void load(LoadProgressListener progressListener)
    {
        final SourceDataInfoSet sourceDataInfo = metaService.getSourceDataInfo();

        progressListener.begin("feature_codes", 1);
        this.featureCodes = featureCodeDao.findFeatureCodes();
        this.reverseFeatureMap = new LinkedHashMap<>();
        featureCodes.forEach((k, v) -> reverseFeatureMap.put(v.getFeatureClass() + "." + v.getFeatureCode(), k));

        progressListener.begin("time_zones", 1);
        this.timezones = timeZoneDao.findTimeZones();

        progressListener.begin("continents", 1);
        continents = findByIds(GeoConstants.CONTINENTS.values())
                .stream()
                .map(l -> new Continent(getContinentCode(l.getId()), l))
                .collect(Collectors.toList());

        progressListener.begin("countries", null);
        this.countries = new HashMap<>();
        locationDao.getCountries().forEach(l -> countries.put(l.getCountryCode(), Country.from(populate(l))));

        logger.info("Loading locations");
        progressListener.begin("load_locations");
        locations = persistentCacheManager.get("raw_locations", new SerializableSerializer<>(), () ->
        {
            final Map<Integer, RawLocation> tmp = new Int2ObjectAVLTreeMap<>();
            locationDao.iterate(e ->
            {
                tmp.put(e.getId(), e);
                progressListener.progress(tmp.size());
            });
            return tmp;
        });

        logger.info("Loading search index");
        int count = 0;
        progressListener.begin("load_search_index");
        for (RawLocation location : locations.values())
        {
            addToSearchIndex(location);
            progressListener.progress(count);
        }
        progressListener.end();

        logger.info("Loading hierarchy data");
        progressListener.begin("load_hierarchy_data");
        final Map<Integer, Integer> childToParent = persistentCacheManager.get("hierarchy", new IntIntMapSerializer(), () ->
                locationDao.loadHierarchy(countries, featureCodes, progressListener::progress));

        logger.info("Joining location nodes hierarchy");
        progressListener.begin("join_admin_levels");
        joinHierarchyNodes(childToParent, progressListener::progress);
        //reportNoParent(nodes);

        logger.info("Loading IP to location data");
        progressListener.begin("ip_data");
        ipDao.load(progressListener::progress);

        progressListener.end();
    }

    private void addToSearchIndex(final RawLocation e)
    {
        final MapFeature featureType = featureCodes.get(e.getMapFeatureId());
        if (GeoConstants.isAdministrativeOrAbove(featureType.getKey()))
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

    @Override
    public List<GeoLocation> findPath(final int id)
    {
        return getPath(id).stream().skip(1).map(this::findById).collect(Collectors.toList());
    }

    private void reportNoParent(final Map<Integer, Node> nodes)
    {
        final Map<String, Integer> featureStatsNoParent = new HashMap<>();
        nodes.forEach((id, node) ->
        {
            if (node.getParent() == null && !Objects.equals(id, GeoConstants.EARTH_ID))
            {
                logger.info("{} has no parent", id);
                final GeoLocation location = findById(node.getId());
                featureStatsNoParent.compute(location.getFeatureClass() + "." + location.getFeatureCode(), (key, existing) -> existing == null ? 1 : existing + 1);
            }
        });

        logger.info("No parent by feature type: {}", featureStatsNoParent);
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
        return locationDao.findCountriesOnContinent(continentCode, pageable).map(e -> Country.from(populate(e)));
    }

    @Override
    public Page<Country> findCountries(Pageable pageable)
    {
        final List<Country> content = countries.values().stream().skip(pageable.getOffset()).limit(pageable.getPageSize()).collect(Collectors.toList());
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
        final int adm1Id = reverseFeatureMap.get("A.ADM1");
        return locationDao.findChildren(countryCode, adm1Id, pageable).map(raw ->
        {
            final GeoLocation l = populate(raw);
            return Country.from(l);
        });
    }

    private void joinHierarchyNodes(final Map<Integer, Integer> childToParent, StepProgressListener listener)
    {
        nodes = new Int2ObjectAVLTreeMap<>();
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

        final List<Integer> countryIds = locationDao.findByPhoneNumber(stripped);

        return !countryIds.isEmpty() ? populateCountry(findById(countryIds.get(0))) : null;
    }

    private Country populateCountry(final GeoLocation location)
    {
        return Country.from(location);
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
        final List<Integer> path = getPath(locationId);
        return path.contains(suspectedParentId);
    }

    private List<Integer> getPath(final int id)
    {
        Node node = this.nodes.get(id);
        final List<Integer> path = new LinkedList<>();
        while (node != null)
        {
            path.add(node.getId());
            final Integer parent = node.getParent();
            node = parent != null ? nodes.get(parent) : null;
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
    public GeoLocation findbyCoordinate(Coordinates point, int distance)
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
        final long max = pageable.getOffset() + pageable.getPageSize();
        final List<Integer> ids = getIds(name, max + 1);
        if (!ids.isEmpty())
        {
            final boolean hasMore = ids.size() > max;
            final List<GeoLocation> content = ids.stream()
                    .limit(max - 1)
                    .map(locations::get)
                    .map(this::populate)
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

        final String timezone = timezones.get(l.getTimeZoneId());

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
