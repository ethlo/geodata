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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.sql.DataSource;
import javax.validation.Valid;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.dao.jdbc.JdbcGeonamesDao;
import com.ethlo.geodata.dao.jdbc.JdbcIpDao;
import com.ethlo.geodata.importer.DataType;
import com.ethlo.geodata.importer.jdbc.MysqlCursorUtil;
import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountrySummary;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.View;
import com.ethlo.geodata.progress.StepProgressListener;
import com.ethlo.geodata.util.GeometryUtil;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Stopwatch;

@SuppressWarnings({"UnstableApiUsage"})
@Primary
@Service
@PropertySource("classpath:queries.sql.properties")
public class GeodataServiceImpl implements GeodataService
{
    public static final double RAD_TO_KM_RATIO = 111.195D;
    private static final Map<String, Integer> CONTINENT_IDS = new LinkedHashMap<>();
    private static final WKTReader wktReader = new WKTReader();

    static
    {
        CONTINENT_IDS.put("AF", 6255146);
        CONTINENT_IDS.put("AS", 6255147);
        CONTINENT_IDS.put("EU", 6255148);
        CONTINENT_IDS.put("NA", 6255149);
        CONTINENT_IDS.put("OC", 6255151);
        CONTINENT_IDS.put("SA", 6255150);
        CONTINENT_IDS.put("AN", 6255152);
    }

    private final Logger logger = LoggerFactory.getLogger(GeodataServiceImpl.class);
    private final Map<Integer, Node> nodes = new HashMap<>();
    private final Map<Integer, String> timezones = new HashMap<>();
    private final Map<String, Country> countries = new HashMap<>();

    // TODO: Make this configurable?
    private final long maximumLocationCacheSize = 0;

    private Map<Integer, MapFeature> featureCodes = new HashMap<>();
    private final RowMapper<Country> COUNTRY_INFO_MAPPER = (rs, rowNum) ->
    {
        final GeoLocation location = new GeoLocation();
        mapLocation(location, rs);
        final Country c = Country.from(location);
        c.setCountry(c.toSummary(rs.getString("iso")));
        return c;
    };
    private final RowMapper<GeoLocation> GEONAMES_ROW_MAPPER = (rs, rowNum) ->
    {
        final GeoLocation location = new GeoLocation();
        mapLocation(location, rs);
        return location;
    };
    @Autowired
    private GeoMetaService metaService;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    private JdbcGeonamesDao geonamesDao;
    @Autowired
    private JdbcIpDao ipDao;
    @Autowired
    private DataSource dataSource;
    private List<Continent> continents = new LinkedList<>();
    private Long locationCount;
    @Value("${geodata.sql.geonamesbyid}")
    private String geoNamesByIdSql;
    private final LoadingCache<Integer, GeoLocation> locationByIdCache = Caffeine.newBuilder()
            .maximumSize(maximumLocationCacheSize)
            .build(this::doFindById);
    @Value("${geodata.sql.geonamescount}")
    private String geoNamesCountSql;
    @Value("${geodata.sql.findcountrybyphone}")
    private String findCountryByPhoneNumberSql;
    @Value("${geodata.sql.findwithinboundaries}")
    private String findWithinBoundariesSql;
    @Value("${geodata.sql.findnearest}")
    private String nearestSql;
    @Value("${geodata.sql.findbyids}")
    private String findByIdsSql;
    @Value("${geodata.sql.findboundariesbyid}")
    private String findBoundariesByIdSql;
    @Value("${geodata.sql.findcountriesoncontinent}")
    private String findCountriesOnContinentSql;
    @Value("${geodata.sql.countcountriesoncontinent}")
    private String countCountriesOnContinentSql;
    @Value("${geodata.sql.countcountrychildren}")
    private String countCountryChildrenSql;
    @Value("${geodata.sql.findcountrychildren}")
    private String findCountryChildrenSql;
    @Value("${geodata.sql.findbyname}")
    private String findByNameSql;
    @Value("${geodata.sql.countbyname}")
    private String countByNameSql;
    @Value("${geodata.boundaries.quality}")
    private int qualityConstant;

    public static String getConcatenatedFeatureCode(final Map<Integer, MapFeature> featureCodes, final int featureCodeId)
    {
        return Optional.ofNullable(featureCodes.get(featureCodeId)).map(f -> f.getFeatureClass() + "." + f.getFeatureCode()).orElseThrow(() -> new EmptyResultDataAccessException("No feature code with ID " + featureCodeId, 1));
    }

    @Override
    public GeoLocation findByIp(String ip)
    {
        return ipDao.findByIp(ip).map(this::findById).orElseThrow(() -> new EmptyResultDataAccessException("Cannot find location for ip " + ip, 1));
    }

    @Override
    public GeoLocation findById(int geoNameId)
    {
        return locationByIdCache.get(geoNameId);
    }

    public GeoLocation doFindById(int id)
    {
        return jdbcTemplate.query(geoNamesByIdSql, Collections.singletonMap("id", id), rs ->
        {
            if (rs.next())
            {
                final GeoLocation location = new GeoLocation();
                mapLocation(location, rs);
                return location;
            }
            return null;
        });
    }

    private <T extends GeoLocation> void mapLocation(T t, ResultSet rs) throws SQLException
    {
        final String countryCode = rs.getString("country_code");

        final Country country = findCountryByCode(countryCode);
        final CountrySummary countrySummary = country != null ? country.toSummary(countryCode) : null;

        final int id = rs.getInt("id");
        final Node node = nodes.get(id);
        final Integer parentId = Optional.ofNullable(node).map(Node::getParent).orElse(null);
        final int featureCodeId = rs.getInt("feature_code_id");
        final MapFeature mapFeature = featureCodeId != 0 ? featureCodes.get(featureCodeId) : null;
        final String wkt = rs.getString("coord");

        final Coordinates coordinates = getCoordinatesFromPoint(wkt);

        t.setId(id);
        t.setName(rs.getString("name"));
        t.setFeatureClass(mapFeature.getFeatureClass());
        t.setFeatureCode(mapFeature.getFeatureCode());
        t.setTimeZone(timezones.get(rs.getInt("timezone_id")));
        t.setPopulation(rs.getLong("population"));
        t.setCoordinates(coordinates);
        t.setParentLocationId(parentId);
        t.setCountry(countrySummary);
    }

    private Coordinates getCoordinatesFromPoint(final String wkt)
    {
        if (wkt == null)
        {
            return null;
        }

        try
        {
            final Geometry geom = wktReader.read(wkt);
            final Coordinate c = geom.getCoordinate();
            return Coordinates.from(c.y, c.x);
        }
        catch (ParseException e)
        {
            throw new IllegalArgumentException("Invalid WKT: " + wkt, e);
        }
    }

    @Override
    public GeoLocation findWithin(Coordinates point, int maxDistanceInKilometers)
    {
        int range = 25;
        GeoLocation location = null;
        while (range <= maxDistanceInKilometers && location == null)
        {
            location = doFindContaining(point, range);
            range *= 2;
        }
        if (location != null)
        {
            return location;
        }
        throw new EmptyResultDataAccessException("Cannot find location for " + point, 1);
    }

    @Override
    public Page<GeoLocationDistance> findNear(Coordinates point, int maxDistanceInKilometers, Pageable pageable)
    {
        final List<GeoLocationDistance> locations = doFindNearest(point, maxDistanceInKilometers, pageable);
        return new PageImpl<>(locations, pageable, getLocationCount());
    }

    private long getLocationCount()
    {
        if (this.locationCount == null)
        {
            final Long result = jdbcTemplate.queryForObject(geoNamesCountSql, Collections.emptyMap(), Long.class);
            locationCount = result != null ? result : 0;
        }
        return locationCount;
    }

    private Map<String, Object> createParams(Coordinates point, int maxDistanceInKm, Pageable pageable)
    {
        final double lat = point.getLat();
        final double lon = point.getLng();
        final double R = 6371;  // earth radius in km
        final double v = Math.toDegrees(maxDistanceInKm / R / Math.cos(Math.toRadians(lat)));
        double x1 = lon - v;
        double x2 = lon + v;
        double y1 = lat - Math.toDegrees(maxDistanceInKm / R);
        double y2 = lat + Math.toDegrees(maxDistanceInKm / R);

        final Map<String, Object> params = new TreeMap<>();
        params.put("point", "POINT(" + point.getLng() + " " + point.getLat() + ")");
        params.put("minPoint", "POINT(" + x1 + " " + y1 + ")");
        params.put("maxPoint", "POINT(" + x2 + " " + y2 + ")");
        params.put("x", point.getLng());
        params.put("y", point.getLat());
        params.put("minX", x1);
        params.put("minY", y1);
        params.put("maxX", x2);
        params.put("maxY", y2);
        params.put("offset", pageable.getOffset());
        params.put("limit", pageable.getPageSize());
        return params;
    }

    private GeoLocation doFindContaining(Coordinates point, int maxDistanceInKm)
    {
        final Map<String, Object> params = createParams(point, maxDistanceInKm, PageRequest.of(0, 1));
        final List<GeoLocation> res = jdbcTemplate.query(findWithinBoundariesSql, params, (rs, rowNum) -> findById(rs.getInt("id")));

        return res.isEmpty() ? null : res.get(0);
    }

    private List<GeoLocationDistance> doFindNearest(Coordinates point, int distance, Pageable pageable)
    {
        // Switch Lat/long
        final Coordinates coordinates = new Coordinates().setLat(point.getLng()).setLng(point.getLat());

        final Map<String, Object> params = createParams(coordinates, distance, pageable);
        return jdbcTemplate.query(nearestSql, params, (rs, rowNum) ->
        {
            final GeoLocation location = new GeoLocation();
            mapLocation(location, rs);

            final double distance1 = BigDecimal.valueOf(rs.getDouble("distance") * RAD_TO_KM_RATIO).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();

            return new GeoLocationDistance().setLocation(location).setDistance(distance1);
        });
    }

    @Override
    public List<GeoLocation> findByIds(Collection<Integer> ids)
    {
        if (ids.isEmpty())
        {
            return Collections.emptyList();
        }
        return jdbcTemplate.query(findByIdsSql, Collections.singletonMap("ids", ids), GEONAMES_ROW_MAPPER);
    }

    @Override
    public byte[] findBoundaries(int id)
    {
        return jdbcTemplate.query(findBoundariesByIdSql, Collections.singletonMap("id", id), rse ->
        {
            if (rse.next())
            {
                return rse.getBytes("wkb");
            }
            return null;
        });
    }

    @Override
    public Page<GeoLocation> findChildren(int locationId, Pageable pageable)
    {
        final Node node = nodes.get(locationId);
        if (node == null)
        {
            throw new EmptyResultDataAccessException("No location with id " + locationId + " found", 1);
        }
        final long total = node.getChildren().length;
        final List<Integer> ids = Arrays.stream(node.getChildren())
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
        this.featureCodes = geonamesDao.loadFeatureCodes();

        progressListener.begin("time_zones", 1);
        loadTimeZones();

        progressListener.begin("continents", 1);
        continents = findByIds(CONTINENT_IDS.values())
                .stream()
                .map(l -> new Continent(getContinentCode(l.getId()), l))
                .collect(Collectors.toList());

        progressListener.begin("countries", featureCodes.size());
        loadCountries();

        progressListener.begin("admin_hierarchies");
        loadHierarchy(progressListener::progress);

        progressListener.begin("ip2location", sourceDataInfo.get(DataType.IP).getCount());
        ipDao.load();
    }

    private void loadTimeZones()
    {
        jdbcTemplate.query("SELECT * FROM timezone", rs -> {
            while (rs.next())
            {
                final int id = rs.getInt("id");
                final String timezone = rs.getString("value");
                timezones.put(id, timezone);
            }
        });
    }

    @Override
    public Page<Continent> findContinents()
    {
        return new PageImpl<>(continents, PageRequest.of(0, 7), 7);
    }

    private String getContinentCode(int id)
    {
        for (Entry<String, Integer> e : CONTINENT_IDS.entrySet())
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
        final Map<String, Object> params = new TreeMap<>();
        params.put("continentCode", continentCode);
        params.put("offset", pageable.getOffset());
        params.put("max", pageable.getPageSize());
        final List<Country> locations = jdbcTemplate.query(findCountriesOnContinentSql, params, COUNTRY_INFO_MAPPER);
        final long count = count(countCountriesOnContinentSql, params);
        return new PageImpl<>(locations, pageable, count);
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
        final Map<String, Object> params = new TreeMap<>();
        params.put("cc", countryCode);
        params.put("feature_code_id", getFeatureCodeId("A", "ADM1"));
        params.put("offset", pageable.getOffset());
        params.put("max", pageable.getPageSize());
        final List<GeoLocation> content = jdbcTemplate.query(findCountryChildrenSql, params, GEONAMES_ROW_MAPPER);
        final long total = count(countCountryChildrenSql, params);
        return new PageImpl<>(content, pageable, total);
    }

    private int getFeatureCodeId(final String featureClass, final String featureCode)
    {
        return featureCodes.entrySet().stream()
                .filter(e -> Objects.equals(featureClass, e.getValue().getFeatureClass()) && Objects.equals(featureCode, e.getValue().getFeatureCode()))
                .map(Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new EmptyResultDataAccessException("No feature with feature class " + featureClass + ", feature code " + featureCode, 1));
    }

    private long count(final String sql, final Map<String, Object> params)
    {
        final Long result = jdbcTemplate.queryForObject(sql, params, Long.class);
        return result != null ? result : 0;
    }

    private int loadCountries()
    {
        final Collection<Country> countryList = jdbcTemplate.query(
                "SELECT * FROM geocountry c, geonames n "
                        + "WHERE c.geoname_id = n.id "
                        + "ORDER BY iso ASC", COUNTRY_INFO_MAPPER);
        countryList.forEach(c -> countries.put(c.getCountry().getCode(), c));
        return countryList.size();
    }

    public void loadHierarchy(StepProgressListener listener)
    {
        try
        {
            doLoadHierarchy(listener);
        }
        catch (SQLException exc)
        {
            throw new RuntimeException(exc);
        }
    }

    private void doLoadHierarchy(final StepProgressListener listener) throws SQLException
    {
        final Map<Integer, Integer> childToParent = geonamesDao.buildHierarchyDataFromAdminCodes(listener);

        // Build node hierarchy
        childToParent.forEach((child, parent) ->
        {
            final Node childNode = nodes.computeIfAbsent(child, Node::new);
            final Node parentNode = nodes.computeIfAbsent(parent, Node::new);
            parentNode.addChild(child);
            childNode.setParent(parent);
        });

        logger.info("Loaded node hierarchy of {} locations", nodes.size());
    }

    private int loadExplicitHierarchy(final Map<Integer, Integer> childToParent) throws SQLException
    {
        logger.info("Loading explicit hierarchy structure");
        final AtomicInteger explicitCount = new AtomicInteger();
        final String sql = "SELECT id, parent_id FROM geohierarchy";
        new MysqlCursorUtil(dataSource).query(sql, Collections.emptyMap(), rs ->
        {
            while (rs.next())
            {
                final int id = rs.getInt("id");
                final int parentId = rs.getInt("parent_id");
                if (childToParent.putIfAbsent(id, parentId) == null)
                {
                    explicitCount.incrementAndGet();
                }
            }
        });
        return explicitCount.get();
    }

    @Override
    public Country findByPhonenumber(String phoneNumber)
    {
        String stripped = phoneNumber.replaceAll("[^\\d.]", "");
        stripped = stripped.replaceFirst("^0+(?!$)", "");

        final List<Country> countries = jdbcTemplate.query(findCountryByPhoneNumberSql,
                Collections.singletonMap("phone", stripped), COUNTRY_INFO_MAPPER
        );
        return countries.isEmpty() ? null : countries.get(0);
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
        final Integer id = CONTINENT_IDS.get(continentCode.toUpperCase());
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
    public Page<GeoLocation> findByName(String name, Pageable pageable)
    {
        final Map<String, Object> params = new TreeMap<>();
        params.put("name", name);
        params.put("offset", pageable.getOffset());
        params.put("limit", pageable.getPageSize());
        final List<GeoLocation> content = jdbcTemplate.query(findByNameSql, params, GEONAMES_ROW_MAPPER);
        final long total = jdbcTemplate.queryForObject(countByNameSql, params, Long.class);
        return new PageImpl<>(content, pageable, total);
    }
}
