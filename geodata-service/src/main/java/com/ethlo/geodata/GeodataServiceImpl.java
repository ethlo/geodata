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
import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.sql.DataSource;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.ethlo.geodata.importer.jdbc.JdbcGeonamesDao;
import com.ethlo.geodata.importer.jdbc.MysqlCursorUtil;
import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountrySummary;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.View;
import com.ethlo.geodata.util.GeometryUtil;
import com.google.common.base.Stopwatch;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;

@Service
@PropertySource("queries.sql.properties")
public class GeodataServiceImpl implements GeodataService
{
    public static final double RAD_TO_KM_RATIO = 111.195D;
    private static final Map<String, Long> CONTINENT_IDS = new LinkedHashMap<>();

    static
    {
        CONTINENT_IDS.put("AF", 6255146L);
        CONTINENT_IDS.put("AS", 6255147L);
        CONTINENT_IDS.put("EU", 6255148L);
        CONTINENT_IDS.put("NA", 6255149L);
        CONTINENT_IDS.put("OC", 6255151L);
        CONTINENT_IDS.put("SA", 6255150L);
        CONTINENT_IDS.put("AN", 6255152L);
    }

    private final Logger logger = LoggerFactory.getLogger(GeodataServiceImpl.class);

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcGeonamesDao geonamesDao;

    @Autowired
    private DataSource dataSource;

    private List<Continent> continents;
    private Map<Long, Node> nodes;
    private Map<String, Country> countries;
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

    private Long locationCount;

    @Value("${geodata.sql.ipLookup}")
    private String ipLookupSql;

    @Value("${geodata.sql.geonamesbyid}")
    private String geoNamesByIdSql;

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

    @Autowired
    private TransactionTemplate txnTemplate;

    @Override
    public GeoLocation findByIp(String ip)
    {
        final boolean isValid = InetAddresses.isInetAddress(ip);
        final InetAddress address = InetAddresses.forString(ip);
        final boolean isLocalAddress = address.isLoopbackAddress() || address.isAnyLocalAddress();
        if (!isValid || isLocalAddress)
        {
            return null;
        }

        long ipLong;
        try
        {
            ipLong = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(ip))).longValue();
        }
        catch (IllegalArgumentException exc)
        {
            throw new InvalidIpException(ip, exc.getMessage(), exc);
        }

        return jdbcTemplate.query(ipLookupSql, Collections.singletonMap("ip", ipLong), rs ->
        {
            if (rs.next())
            {
                final long geoNameId = rs.getLong("geoname_id") != 0 ? rs.getLong("geoname_id") : rs.getLong("geoname_country_id");
                return findById(geoNameId);
            }
            return null;
        });
    }

    @Override
    public GeoLocation findById(long geoNameId)
    {
        return jdbcTemplate.query(geoNamesByIdSql, Collections.singletonMap("id", geoNameId), rs ->
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
        final Long parentId = rs.getLong("parent_id") != 0 ? rs.getLong("parent_id") : null;
        final String countryCode = rs.getString("country_code");

        final Country country = findCountryByCode(countryCode);
        final CountrySummary countrySummary = country != null ? country.toSummary(countryCode) : null;

        t
                .setId(rs.getLong("id"))
                .setName(rs.getString("name"))
                .setFeatureCode(rs.getString("feature_code"))
                .setPopulation(rs.getLong("population"))
                .setCoordinates(Coordinates.from(rs.getDouble("lat"), rs.getDouble("lng")))
                .setParentLocationId(parentId)
                .setCountry(countrySummary);
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
        final List<GeoLocation> res = jdbcTemplate.query(findWithinBoundariesSql, params, (rs, rowNum) -> findById(rs.getLong("id")));

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
    public List<GeoLocation> findByIds(Collection<Long> ids)
    {
        if (ids.isEmpty())
        {
            return Collections.emptyList();
        }
        return jdbcTemplate.query(findByIdsSql, Collections.singletonMap("ids", ids), GEONAMES_ROW_MAPPER);
    }

    @Override
    public byte[] findBoundaries(long id)
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
    public Page<GeoLocation> findChildren(long locationId, Pageable pageable)
    {
        loadNodes();

        final Node node = nodes.get(locationId);
        final long total = node.getChildren().size();
        final List<Long> ids = node.getChildren()
                .stream()
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .map(Node::getId)
                .collect(Collectors.toList());
        final List<GeoLocation> locations = findByIds(ids);
        locations.sort(Comparator.comparing(GeoLocation::getName));
        return new PageImpl<>(locations, pageable, total);
    }

    private void loadNodes()
    {
        if (nodes == null)
        {
            try
            {
                loadHierarchy();
            }
            catch (SQLException exc)
            {
                throw new RuntimeException(exc);
            }
        }
    }

    @Override
    public Page<Continent> findContinents()
    {
        if (continents == null)
        {
            continents = findByIds(CONTINENT_IDS.values())
                    .stream()
                    .map(l -> new Continent(getContinentCode(l.getId()), l))
                    .collect(Collectors.toList());
        }
        return new PageImpl<>(continents, PageRequest.of(0, 7), 7);
    }

    private String getContinentCode(Long id)
    {
        for (Entry<String, Long> e : CONTINENT_IDS.entrySet())
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
        final long count = jdbcTemplate.queryForObject(countCountriesOnContinentSql, params, Long.class);
        return new PageImpl<>(locations, pageable, count);
    }

    @Override
    public Page<Country> findCountries(Pageable pageable)
    {
        loadCountries();
        final List<Country> content = countries.values().stream().skip(pageable.getOffset()).limit(pageable.getPageSize()).collect(Collectors.toList());
        return new PageImpl<>(content, pageable, countries.size());
    }


    @Override
    public Country findCountryByCode(String countryCode)
    {
        loadCountries();

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
        params.put("offset", pageable.getOffset());
        params.put("max", pageable.getPageSize());
        final List<GeoLocation> content = jdbcTemplate.query(findCountryChildrenSql, params, GEONAMES_ROW_MAPPER);
        final long total = jdbcTemplate.queryForObject(countCountryChildrenSql, params, Long.class);
        return new PageImpl<>(content, pageable, total);
    }

    public int loadCountries()
    {
        if (countries == null)
        {
            countries = new LinkedHashMap<>();
            final Collection<Country> countryList = jdbcTemplate.query(
                    "SELECT * FROM geocountry c, geonames n "
                            + "WHERE c.geoname_id = n.id "
                            + "ORDER BY iso ASC", COUNTRY_INFO_MAPPER);
            countryList.forEach(c -> countries.put(c.getCountry().getCode(), c));
            return countryList.size();
        }
        return 0;
    }

    public void loadHierarchy() throws SQLException
    {
        final Map<Long, Long> childToParent = geonamesDao.buildHierarchyDataFromAdminCodes();

        logger.info("Loading explicit hierarchy structure");
        final String sql = "SELECT id, parent_id FROM geohierarchy";
        new MysqlCursorUtil(dataSource).query(sql, Collections.emptyMap(), rs ->
        {
            try
            {
                while (rs.next())
                {
                    final long id = rs.getLong("id");
                    final long parentId = rs.getLong("parent_id");
                    childToParent.put(id, parentId);
                }
            }
            catch (SQLException exc)
            {
                throw new RuntimeException(exc);
            }
        });

        // Build node hierarchy
        nodes = new HashMap<>(childToParent.size());
        childToParent.forEach((child, parent) ->
        {
            final Node childNode = nodes.computeIfAbsent(child, Node::new);
            final Node parentNode = nodes.computeIfAbsent(parent, Node::new);
            parentNode.addChild(childNode);
            childNode.setParent(parentNode);
        });

        logger.info("Loaded node hierarchy of {} locations", nodes.size());
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
    public boolean isInsideAny(List<Long> locations, long location)
    {
        final GeoLocation loc = findById(location);
        if (loc == null)
        {
            throw new EmptyResultDataAccessException("No such location found " + location, 1);
        }

        for (Long l : locations)
        {
            if (l.equals(location) || isLocationInside(location, l))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isOutsideAll(List<Long> locations, long location)
    {
        final GeoLocation loc = findById(location);
        if (loc == null)
        {
            throw new EmptyResultDataAccessException("No such location found " + location, 1);
        }

        for (long l : locations)
        {
            if (isLocationInside(l, loc.getId()))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isLocationInside(long locationId, long suspectedParentId)
    {
        final List<Long> path = getPath(locationId);
        return path.contains(suspectedParentId);
    }

    private List<Long> getPath(long id)
    {
        loadNodes();

        Node node = this.nodes.get(id);
        final List<Long> path = new LinkedList<>();
        while (node != null)
        {
            path.add(node.getId());
            node = node.getParent();
        }
        return path;
    }

    @Override
    public Continent findContinent(String continentCode)
    {
        final Long id = CONTINENT_IDS.get(continentCode.toUpperCase());
        if (id != null)
        {
            return new Continent(continentCode.toUpperCase(), findById(id));
        }
        return null;
    }

    @Override
    public GeoLocation findParent(long id)
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
    public byte[] findBoundaries(long id, @Valid View view)
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
    public byte[] findBoundaries(long id, double maxTolerance)
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
