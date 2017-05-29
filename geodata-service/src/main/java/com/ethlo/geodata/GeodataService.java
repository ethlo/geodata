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
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.springframework.data.geo.Point;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.importer.HierarchyImporter;
import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinate;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.Node;
import com.google.common.io.Files;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;

@Service
public class GeodataService
{
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    //private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
    //private Map<Integer, GeoLocation> countryCodeMap;
    
    private static final Map<String, Long> CONTINENT_IDS = new LinkedHashMap<>();
    private final RowMapper<GeoLocation> GEONAMES_ROW_MAPPER = new RowMapper<GeoLocation>()
    {
        @Override
        public GeoLocation mapRow(ResultSet rs, int rowNum) throws SQLException
        {
            return mapLocation(rs);
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
    
    private Set<GeoLocation> continents;
    private Set<Node> roots;
    private Map<Long, Node> nodes;
    private Map<String, Country> countries;

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

    public GeoLocation findById(long geoNameId)
    {
        final String sql = "SELECT * from geonames WHERE id = :id";
        return jdbcTemplate.query(sql, Collections.singletonMap("id", geoNameId), rs ->
        {
            if (rs.next())
            {
                return mapLocation(rs);
            }
            return null;
        });
    }

    private GeoLocation mapLocation(ResultSet rs) throws SQLException
    {
        final Long parentId = rs.getLong("parent_id") != 0 ? rs.getLong("parent_id") : null;
        final String countryCode = rs.getString("country_code");
        
        return new GeoLocation.Builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .featureCode(rs.getString("feature_code"))
            .coordinates(new Coordinate().setLat(rs.getDouble("lat")).setLng(rs.getDouble("lng")))
            .parentLocationId(parentId)
            .country(findCountryByCode(countryCode))
            .build();
    }

    public GeoLocation findWithin(Point point, int maxDistanceInKilometers)
    {
        final List<Long> locationIds = doFindContaining(point, maxDistanceInKilometers, new PageRequest(0, 10));
        if (! locationIds.isEmpty())
        {
            return findById(locationIds.get(0));
        }
        return null;
    }
    
    public GeoLocation findNear(Point point, int maxDistanceInKilometers)
    {
        final List<Entry<GeoLocation, Long>> locations = doFindNearest(point, maxDistanceInKilometers, new PageRequest(0, 1));
        if (! locations.isEmpty())
        {
            return locations.get(0).getKey();
        }
        return null;
    }
    
    private Map<String, Object> createParams(Point point, int maxDistanceInKm, Pageable pageable)
    {
        final double lat = point.getY();
        final double lon = point.getX();
        final double R = 6371;  // earth radius in km
        double x1 = lon - Math.toDegrees(maxDistanceInKm/R/Math.cos(Math.toRadians(lat)));
        double x2 = lon + Math.toDegrees(maxDistanceInKm/R/Math.cos(Math.toRadians(lat)));
        double y1 = lat - Math.toDegrees(maxDistanceInKm/R);
        double y2 = lat + Math.toDegrees(maxDistanceInKm/R);
        
        final Map<String, Object> params = new TreeMap<>();
        params.put("point", "POINT(" + point.getX() + " " + point.getY() + ")");
        params.put("minPoint", "POINT(" + x1 + " " + y1 + ")");
        params.put("maxPoint", "POINT(" + x2 + " " + y2 + ")");
        params.put("x", point.getX());
        params.put("y", point.getY());
        params.put("minX", x1); 
        params.put("minY", y1); 
        params.put("maxX", x2); 
        params.put("maxY", y2); 
        params.put("offset", pageable.getOffset());
        params.put("limit", pageable.getPageSize());
        return params;
    }
    
    private List<Long> doFindContaining(Point point, int maxDistanceInKm, Pageable pageable)
    {
        final Map<String, Object> params = createParams(point, maxDistanceInKm, pageable);
        final String sql = "SELECT id "
                        + "FROM geoboundaries "
                        + "WHERE st_within(coord, st_envelope(linestring(point(:minX, :minY), point(:maxX, :maxY)))) "
                        + "AND st_contains(raw_polygon, POINT(:x,:y)) "
                        + "ORDER BY area ASC " 
                        + "LIMIT :limit";
        
        return jdbcTemplate.query(sql, params, new RowMapper<Long>()
        {
            @Override
            public Long mapRow(ResultSet rs, int rowNum) throws SQLException
            {
                return rs.getLong("id");
            }
        });
    }
    
    private List<Entry<GeoLocation, Long>> doFindNearest(Point point, int distance, Pageable pageable)
    {
        final Map<String, Object> params = createParams(new Point(point.getY(), point.getX()), distance, pageable);
        final String sql = "SELECT id, st_distance(POINT(:x, :y), coord) AS distance " 
                        + "FROM geonames "
                        + "WHERE st_within(coord, st_envelope(linestring(point(:minX, :minY), point(:maxX,:maxY)))) "
                        + "ORDER BY distance ASC "
                        + "LIMIT :offset, :limit";
        
        final SqlRowSet rs = jdbcTemplate.queryForRowSet(sql, params);
        final Map<Long, Long> idAndDistance = new TreeMap<>();
        while (rs.next())
        {
            idAndDistance.put(rs.getLong("id"), (long) rs.getDouble("distance") * 1000);
        }
        
        if (idAndDistance.isEmpty())
        {
            return Collections.emptyList();
        }
        
        final List<GeoLocation> locations = findByIds(new LinkedList<>(idAndDistance.keySet()));
        return locations.stream().map(l->new AbstractMap.SimpleEntry<>(l, idAndDistance.get(l.getId()))).collect(Collectors.toList());
    }

    private List<GeoLocation> findByIds(Collection<Long> ids)
    {
        if (ids.isEmpty())
        {
            return Collections.emptyList();
        }
        
        final String sql = "SELECT * from geonames WHERE id in (:ids)";
        return jdbcTemplate.query(sql, Collections.singletonMap("ids", ids), GEONAMES_ROW_MAPPER);
    }

    public Geometry findBoundaries(long id)
    {
        final String sql = "SELECT ST_AsBinary(raw_polygon) as wkb FROM geoboundaries WHERE id = :id";
        return jdbcTemplate.query(sql, Collections.singletonMap("id", id), rse ->
        {
            if (rse.next())
            {
                WKBReader r = new WKBReader();
                try
                {
                    return r.read(rse.getBytes("wkb"));
                }
                catch (ParseException exc)
                {
                    throw new DataAccessResourceFailureException(exc.getMessage(), exc);
                }
            }
            return null;
        });
    }

    public Page<GeoLocation> getChildren(long locationId, Pageable pageable)
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

    public Collection<GeoLocation> getContinents()
    {
        if (continents == null)
        {
            continents = new LinkedHashSet<>(findByIds(CONTINENT_IDS.values()));
        }
        return continents;
    }

    public Page<GeoLocation> findCountriesOnContinent(String continentCode, Pageable pageable)
    {
        final Map<String, Object> params = new TreeMap<>();
        params.put("continentCode", continentCode);
        params.put("offset", pageable.getOffset());
        params.put("max", pageable.getPageSize());
        final List<GeoLocation> locations = jdbcTemplate.query("SELECT n.* FROM geocountry c, geonames n WHERE c.geoname_id = n.id AND continent = :continentCode LIMIT :offset,:max", params, GEONAMES_ROW_MAPPER);
        final long count = jdbcTemplate.queryForObject("SELECT COUNT(iso) FROM geocountry WHERE continent = :continentCode", params, Long.class);
        return new PageImpl<>(locations, pageable, count);
    }

    public Country findCountryByCode(String countryCode)
    {
        loadCountries();
        return countries.get(countryCode);
    }

    public Collection<GeoLocation> getChildren(Country country)
    {
        return jdbcTemplate.query("select * from geonames where country_code = :cc and feature_code = 'ADM1'", Collections.singletonMap("cc", country.getCode()), GEONAMES_ROW_MAPPER);
    }
    
    public int loadCountries()
    {
        if (countries == null)
        {
            countries = new LinkedHashMap<>();
            final Collection<Country> countryList = jdbcTemplate.query("SELECT * FROM geocountry ORDER BY iso ASC", new RowMapper<Country>()
            {
                @Override
                public Country mapRow(ResultSet rs, int rowNum) throws SQLException
                {
                    return new Country().setId(rs.getLong("geoname_id")).setCode(rs.getString("iso")).setName(rs.getString("country"));
                }            
            });
            countryList.forEach(c->countries.put(c.getCode(), c));
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
    
    public Country findPhoneLocation(String phoneNumber)
    {
        final List<Country> countries = jdbcTemplate.query("SELECT iso, phone, geoname_id, country FROM geocountry "
                + "WHERE :phone like CONCAT(phone, '%') "
                + "ORDER BY population DESC", 
                Collections.singletonMap("phone", phoneNumber.replaceAll("[^\\d.]", "")), new RowMapper<Country>()
        {
            @Override
            public Country mapRow(ResultSet rs, int rowNum) throws SQLException
            {
                return new Country().setCode(rs.getString("iso")).setName(rs.getString("country")).setId(rs.getLong("geoname_id"));
            }
        });
        return countries.isEmpty() ? null : countries.get(0);
    }

    public GeoLocation findLocationByCountryCode(String cc)
    {
        loadCountries();
        final Country country = countries.get(cc);
        if (country != null)
        {
            return findById(country.getId());
        }
        return null;
    }    
    
    public boolean isInsideAny(List<Long> locations, long location)
    {
        final GeoLocation loc = findById(location);
        if (loc == null)
        {
            throw new EmptyResultDataAccessException("No such location found " + location, 1);
        }
        
        for (Long l : locations)
        {
            if (l.equals(location) || locationContains(l, location))
            {
                return true;
            }
        }
        return false;
    }
    
    public boolean isOutsideAll(List<Long> locations, Long location)
    {
        for (Long l : locations)
        {
            if (location.equals(l) || locationContains(location, l))
            {
                return true;
            }
        }
        return false;
    }

    public boolean isLocationInside(long locationId, long suspectedParentId)
    {
        final List<Long> path = getPath(locationId);
        return path.contains(suspectedParentId);
    }
    
    public boolean locationContains(long parentId, long suspectedChild)
    {
        return isLocationInside(suspectedChild, parentId);
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

    public Continent findContinent(String continentCode)
    {
        final Long id = CONTINENT_IDS.get(continentCode.toUpperCase());
        if (id != null)
        {
            return new Continent(continentCode.toUpperCase(), findById(id));
        }
        return null;
    }
}
