package com.ethlo.geodata;

import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Point;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.model.Location;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;

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
        return jdbcTemplate.query(sql, Collections.singletonMap("ip",  ipLong), rs->
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
        return jdbcTemplate.query(sql, Collections.singletonMap("id", geoNameId), rs->
        {
            if (rs.next())
            {
                return new Location.Builder()
                     .id(rs.getLong("id"))
                     .name(rs.getString("name"))
                     // TODO
                     .build();
            }
            return null;
        });
    }

    public Location findByCoordinates(Point point)
    {
        return null;
    }

    public List<Point> findBoundaries(long id)
    {
        final String sql = "SELECT ST_AsText(raw_polygon) as str FROM geoboundaries WHERE id = :id";
        return jdbcTemplate.query(sql, Collections.singletonMap("id", id), rse->
        {
           if (rse.next())
           {
               // TODO: extract points
               final String ls = rse.getString("str");
               System.out.println(ls);
           }
           return null;
        });
    }
}
