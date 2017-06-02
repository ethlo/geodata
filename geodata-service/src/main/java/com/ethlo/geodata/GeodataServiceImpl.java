package com.ethlo.geodata;

/*-
 * #%L
 * geodata
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.importer.HierarchyImporter;
import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountrySummary;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.google.common.io.Files;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;

@Service
public class GeodataServiceImpl implements GeodataService
{
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    
    private static final Map<String, Long> CONTINENT_IDS = new LinkedHashMap<>();

    private final RowMapper<Country> COUNTRY_INFO_MAPPER = new RowMapper<Country>()
    {
        @Override
        public Country mapRow(ResultSet rs, int rowNum) throws SQLException
        {
            final GeoLocation location = new GeoLocation();
            mapLocation(location, rs);            
            final Country c = Country.from(location);
            c.setCountry(c.toSummary(rs.getString("iso")));
            return c;
        }            
    };
    
    private final RowMapper<GeoLocation> GEONAMES_ROW_MAPPER = new RowMapper<GeoLocation>()
    {
        @Override
        public GeoLocation mapRow(ResultSet rs, int rowNum) throws SQLException
        {
            final GeoLocation location = new GeoLocation();
            mapLocation(location, rs);
            return location;
        }
    };
    
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
    
    private List<Continent> continents;
    private Set<Node> roots;
    private Map<Long, Node> nodes;
    private Map<String, Country> countries;
    private Long locationCount;

    @Override
    public GeoLocation findByIp(String ip)
    {
        long ipLong;
        try
        {
            ipLong = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(ip))).longValue();
        }
        catch (IllegalArgumentException exc)
        {
            throw new InvalidIpException(ip, exc.getMessage(), exc);
        }
        
        final String sql = "SELECT geoname_id, geoname_country_id from geoip WHERE :ip BETWEEN first and last LIMIT 1";
        return jdbcTemplate.query(sql, Collections.singletonMap("ip", ipLong), rs -> 
        {
            if (rs.next())
            {
                final Long geoNameId = rs.getLong("geoname_id") != 0 ? rs.getLong("geoname_id") : rs.getLong("geoname_country_id");
                return findById(geoNameId);
            }
            return null;
        });
    }

    @Override
    public GeoLocation findById(long geoNameId)
    {
        final String sql = "SELECT * from geonames WHERE id = :id";
        return jdbcTemplate.query(sql, Collections.singletonMap("id", geoNameId), rs ->
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
        final List<GeoLocationDistance> locations = doFindNearest(point, maxDistanceInKilometers, new PageRequest(0, 1));
        
        return new PageImpl<>(locations, pageable, getlocationCount());
    }
    
    private long getlocationCount()
    {
        if (this.locationCount == null)
        {
            locationCount = jdbcTemplate.queryForObject("SELECT COUNT(id) FROM geonames", Collections.emptyMap(), Long.class);
        }
        return locationCount;
    }

    private Map<String, Object> createParams(Coordinates point, int maxDistanceInKm, Pageable pageable)
    {
        final double lat = point.getLat();
        final double lon = point.getLng();
        final double R = 6371;  // earth radius in km
        double x1 = lon - Math.toDegrees(maxDistanceInKm/R/Math.cos(Math.toRadians(lat)));
        double x2 = lon + Math.toDegrees(maxDistanceInKm/R/Math.cos(Math.toRadians(lat)));
        double y1 = lat - Math.toDegrees(maxDistanceInKm/R);
        double y2 = lat + Math.toDegrees(maxDistanceInKm/R);
        
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
        final Map<String, Object> params = createParams(point, maxDistanceInKm, new PageRequest(0, 1));
        
        final String sql = "SELECT id "
                        + "FROM geoboundaries "
                        + "WHERE st_within(coord, st_envelope(linestring(point(:minX, :minY), point(:maxX, :maxY)))) "
                        + "AND st_contains(raw_polygon, POINT(:x,:y)) "
                        + "ORDER BY area ASC " 
                        + "LIMIT :limit";
        
        final List<GeoLocation> res = jdbcTemplate.query(sql, params, new RowMapper<GeoLocation>()
        {
            @Override
            public GeoLocation mapRow(ResultSet rs, int rowNum) throws SQLException
            {
                return findById(rs.getLong("id"));
            }
        });
        
        return res.isEmpty() ? null : res.get(0);
    }
    
    private List<GeoLocationDistance> doFindNearest(Coordinates point, int distance, Pageable pageable)
    {
        final Map<String, Object> params = createParams(point, distance, pageable);
        final String sql = "SELECT *, st_distance(POINT(:x, :y), coord) AS distance " 
                        + "FROM geonames "
                        + "WHERE st_within(coord, st_envelope(linestring(point(:minX, :minY), point(:maxX,:maxY)))) "
                        + "ORDER BY distance ASC "
                        + "LIMIT :offset, :limit";
        
        return jdbcTemplate.query(sql, params, new RowMapper<GeoLocationDistance>()
        {
            @Override
            public GeoLocationDistance mapRow(ResultSet rs, int rowNum) throws SQLException
            {
                final GeoLocation location = new GeoLocation();
                mapLocation(location, rs);
                return new GeoLocationDistance().setLocation(location).setDistance(rs.getDouble("distance"));
            }
            
        });
    }

    @Override
    public List<GeoLocation> findByIds(Collection<Long> ids)
    {
        if (ids.isEmpty())
        {
            return Collections.emptyList();
        }
        
        final String sql = "SELECT * from geonames WHERE id in (:ids)";
        return jdbcTemplate.query(sql, Collections.singletonMap("ids", ids), GEONAMES_ROW_MAPPER);
    }

    @Override
    public byte[] findBoundaries(long id)
    {
        final String sql = "SELECT ST_AsBinary(raw_polygon) as wkb FROM geoboundaries WHERE id = :id";
        return jdbcTemplate.query(sql, Collections.singletonMap("id", id), rse ->
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
            .map(n->n.getId())
            .collect(Collectors.toList());
        return new PageImpl<>(findByIds(ids), pageable, total);
    }

    private void loadNodes()
    {
        if (nodes == null)
        {
            try
            {
                loadHierarchy();
            }
            catch (IOException exc)
            {
                throw new DataAccessResourceFailureException(exc.getMessage(), exc);
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
                .map(l->new Continent(getContinentCode(l.getId()), l))
                .collect(Collectors.toList());
        }
        return new PageImpl<>(continents, new PageRequest(0, continents.size()), continents.size());
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
        final List<Country> locations = jdbcTemplate.query("SELECT * FROM geocountry c, geonames n WHERE c.geoname_id = n.id AND continent = :continentCode LIMIT :offset,:max", params, COUNTRY_INFO_MAPPER);
        final long count = jdbcTemplate.queryForObject("SELECT COUNT(iso) FROM geocountry WHERE continent = :continentCode", params, Long.class);
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
        final long total = jdbcTemplate.queryForObject("select COUNT(id) from geonames where country_code = :cc and feature_code = 'ADM1'", params, Long.class);
        final List<GeoLocation> content = jdbcTemplate.query("select * from geonames where country_code = :cc and feature_code = 'ADM1' LIMIT :offset,:max", params, GEONAMES_ROW_MAPPER);
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
            countryList.forEach(c->countries.put(c.getCountry().getCode(), c));
            return countryList.size();
        }
        return 0;
    }
    
    public int loadHierarchy() throws IOException
    {
        final byte[] data = fetchHierarchy();

        nodes = new HashMap<>();
        final Map<Long, Long> childToParent = new HashMap<>();
        
        final File hierarchyFile = File.createTempFile("hierarchy", ".tsv");
        Files.write(data, hierarchyFile);
        new HierarchyImporter(hierarchyFile).processFile(r->
        {
            final long parentId = Long.parseLong(r.get("parent_id"));
            final long childId = Long.parseLong(r.get("child_id"));
            final Node parent = nodes.getOrDefault(parentId, new Node(parentId));
            final Node child = nodes.getOrDefault(childId, new Node(childId));
            nodes.put(parent.getId(), parent);
            nodes.put(child.getId(), child);
            
            childToParent.put(childId, parentId);
        });

        // Connect children directly to continents
        jdbcTemplate.query("SELECT * FROM geocountry", new RowMapper<Object>()
        {
            @Override
            public Object mapRow(ResultSet rs, int rowNum) throws SQLException
            {
                final Long id = CONTINENT_IDS.get(rs.getString("continent"));
                childToParent.put(rs.getLong("geoname_id"), id);
                return null;
            }            
        });
        
        // Process hierarchy after, as we do not know the order of the pairs
        childToParent.entrySet().forEach(e->
        {
            final long child = e.getKey();
            final long parent = e.getValue();
            final Node childNode = nodes.get(child);
            final Node parentNode = nodes.get(parent);
            parentNode.addChild(childNode);
            childNode.setParent(parentNode);
        });
        
        roots = new HashSet<>();
        for (Entry<Long, Node> e : nodes.entrySet())
        {
            if (e.getValue().getParent() == null)
            {
                roots.add(e.getValue());
            }
        }
        
        return childToParent.size();
    }

    private byte[] fetchHierarchy()
    {
        final String sql = "SELECT data FROM geohierarchy";
        return jdbcTemplate.query(sql, rs->
        {
           if (rs.next())
           {
               return rs.getBytes("data");
           }
           return null;
        });
    }
    
    @Override
    public Country findByPhonenumber(String phoneNumber)
    {
        String stripped = phoneNumber.replaceAll("[^\\d.]", "");
        stripped = stripped.replaceFirst("^0+(?!$)", "");
        
        final List<Country> countries = jdbcTemplate.query("SELECT n.*, c.iso, c.geoname_id, c.country FROM geocountry c, geonames n "
                + "WHERE c.geoname_id = n.id "
                + "AND :phone like CONCAT(phone, '%') "
                + "ORDER BY population DESC", 
                Collections.singletonMap("phone", stripped), COUNTRY_INFO_MAPPER);
        return countries.isEmpty() ? null : countries.get(0);
    }

    @Override
    public GeoLocation findLocationByCountryCode(String cc)
    {
        loadCountries();
        final Country country = countries.get(cc.toUpperCase());
        if (country != null)
        {
            return findById(country.getId());
        }
        return null;
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
            if (l.equals(location) || isLocationInside(l, location))
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
            final Page<GeoLocationDistance> nearest = findNear(point, distance, new PageRequest(0, 1));
            location = nearest.hasContent() ? nearest.getContent().get(0).getLocation() : null;
        }
        
        if (location != null)
        {
            return location;
        }
        throw new EmptyResultDataAccessException("Cannot find a location for position lat=" + point.getLat() + ", lng=" + point.getLng(), 1);
    }
}
