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

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.importer.jdbc.JdbcCountryImporter;
import com.ethlo.geodata.importer.jdbc.JdbcGeonamesBoundaryImporter;
import com.ethlo.geodata.importer.jdbc.JdbcGeonamesHierarchyImporter;
import com.ethlo.geodata.importer.jdbc.JdbcGeonamesImporter;
import com.ethlo.geodata.importer.jdbc.JdbcIpLookupImporter;

@Service
public class GeoMetaService
{
    @Autowired
    private JdbcCountryImporter countryImporter;
    
    @Autowired
    private JdbcIpLookupImporter ipLookupImporter;
    
    @Autowired
    private JdbcGeonamesImporter geonamesImporter;
    
    @Autowired
    private JdbcGeonamesBoundaryImporter boundaryImporter;
    
    @Autowired
    private JdbcGeonamesHierarchyImporter hierarchyImporter;
    
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    
    private long maxDataAgeMillis;
    
    @Value("${geodata.max-data-age}")
    public void setMaxDataAge(String age)
    {
    	final Duration d = Duration.parse("P" + age);
    	maxDataAgeMillis = d.toMillis();
    }
    
    public long getLastModified(String alias) throws IOException
    {
        final String sql = "SELECT last_modified from metadata where alias = :alias";
        return jdbcTemplate.query(sql, Collections.singletonMap("alias", alias), rs->
        {
            if (rs.next())
            {
                return rs.getTimestamp("last_modified").getTime();
            }
            return 0L;
        });
    }
    
    public void setLastModified(String alias, Date lastModified) throws IOException
    {
        final String sql = "REPLACE INTO `metadata` (`alias`, `last_modified`) VALUES (:alias, :last_modified)";
        final Map<String, Object> params = new TreeMap<>();
        params.put("alias", alias);
        params.put("last_modified", lastModified);
        jdbcTemplate.update(sql, params);
    }

    public void update() throws IOException
    {
        final Date countryTimestamp = countryImporter.lastRemoteModified();
        if (countryTimestamp.getTime() > getLastModified("geonames_country") + maxDataAgeMillis)
        {
            countryImporter.purge();
            countryImporter.importData();
            setLastModified("geonames_country", countryTimestamp);
        }
        
        final Date geonamesHierarchyTimestamp = hierarchyImporter.lastRemoteModified();
        if (geonamesHierarchyTimestamp.getTime() > getLastModified("geonames_hierarchy") + maxDataAgeMillis)
        {
            hierarchyImporter.purge();
            hierarchyImporter.importData();
            setLastModified("geonames_hierarchy", geonamesHierarchyTimestamp);
        }
        
        final Date geonamesTimestamp = geonamesImporter.lastRemoteModified();
        if (geonamesTimestamp.getTime() > getLastModified("geonames") + maxDataAgeMillis)
        {
            geonamesImporter.purge();
            geonamesImporter.importData();
            setLastModified("geonames", geonamesTimestamp);
        }
        
        final Date boundariesTimestamp = boundaryImporter.lastRemoteModified();
        if (boundariesTimestamp.getTime() > getLastModified("geoboundaries") + maxDataAgeMillis)
        {
            boundaryImporter.purge();
            boundaryImporter.importData();
            setLastModified("geoboundaries", boundariesTimestamp);
        }
        
        final Date ipTimestamp = ipLookupImporter.lastRemoteModified();
        if (ipTimestamp.getTime() > getLastModified("geoip") + maxDataAgeMillis)
        {
            ipLookupImporter.purge();
            ipLookupImporter.importData();
            setLastModified("geoip", ipTimestamp);
        }
    }

    public Map<String, Date> getLastModified()
    {
        final Map<String, Date> retVal = new TreeMap<>();
        jdbcTemplate.query("SELECT alias, last_modified FROM metadata", Collections.emptyMap(), new RowMapper<Void>()
        {
            @Override
            public Void mapRow(ResultSet rs, int rowNum) throws SQLException
            {
                retVal.put(rs.getString("alias"), rs.getTimestamp("last_modified"));
                return null;
            }
            
        });
        return retVal;
    }
}
