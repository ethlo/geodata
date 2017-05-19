package com.ethlo.geodata;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Point;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.model.Coordinate;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.Location;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;

@Service
public class GeodataService
{
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    public void setDataSource(DataSource dataSource)
    {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    public Location findByIp(String ip)
    {
        final long ipLong = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(ip))).longValue();
        final String sql = "SELECT * from geoip WHERE first <= :ip AND last >= :ip";
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

    public Location findById(long geoNameId)
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

    private Location mapLocation(ResultSet rs) throws SQLException
    {
        return new Location.Builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            //.city(rs.getString("city"))
            //.address(rs.getString("address"))
            .coordinates(new Coordinate().setLat(rs.getDouble("lat")).setLng(rs.getDouble("lng")))
            .parentLocationId(rs.getLong("parent_id"))
            .country(findCountryByCode(rs.getString("country_code")))
            .build();
    }

    public Location findByCoordinates(Point point, int maxDistanceInKilometers)
    {
        final List<Entry<Location, Long>> locations = doFindNearest(point, maxDistanceInKilometers, new PageRequest(0, 1));
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
    
    private List<Entry<Location, Long>> doFindNearest(Point point, int distance, Pageable pageable)
    {
        final Map<String, Object> params = createParams(point, distance, pageable);
        final String sql = "SELECT id, st_distance(ST_PointFromText(:point), coord) AS distance " 
                         + "FROM geoboundaries "
                         + "WHERE st_within(coord, st_envelope(st_makeline(:minPoint, :maxPoint))) "
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
        
        final List<Location> locations = findByIds(new LinkedList<>(idAndDistance.keySet()));
        return locations.stream().map(l->new AbstractMap.SimpleEntry<>(l, idAndDistance.get(l.getId()))).collect(Collectors.toList());
    }

    private List<Location> findByIds(List<Long> ids)
    {
        final String sql = "SELECT * from geonames WHERE id in (:ids)";
        return jdbcTemplate.query(sql, Collections.singletonMap("ids", ids), new RowMapper<Location>()
        {
            @Override
            public Location mapRow(ResultSet rs, int rowNum) throws SQLException
            {
                return mapLocation(rs);
            }
        });
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

    public Collection<Location> getChildren(long locationId)
    {
        // TODO: Implement me
        return null;
    }

    public Collection<Location> getContinents()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public Collection<Location> findCountriesOnContinent(String continentName)
    {
        // TODO Auto-generated method stub
        return null;
    }

    public Country findCountryByCode(String countryCode)
    {
        // TODO Auto-generated method stub
        return null;
    }

    public List<Location> getChildren(Country country)
    {
        // TODO Auto-generated method stub
        return null;
    }    
}
