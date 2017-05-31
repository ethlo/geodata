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

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
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
    
    public Date getLastModified(String alias) throws IOException
    {
        final String sql = "SELECT last_modified from metadata where alias = :alias";
        return jdbcTemplate.query(sql, Collections.singletonMap("alias", alias), rs->
        {
            if (rs.next())
            {
                return rs.getTimestamp("last_modified");
            }
            return new Date(0);
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
        if (countryTimestamp.after(getLastModified("geonames_country")))
        {
            countryImporter.purge();
            countryImporter.importData();
            setLastModified("geonames_country", countryTimestamp);
        }
        
        final Date geonamesHierarchyTimestamp = hierarchyImporter.lastRemoteModified();
        if (geonamesHierarchyTimestamp.after(getLastModified("geonames_hierarchy")))
        {
            hierarchyImporter.purge();
            hierarchyImporter.importData();
            setLastModified("geonames_hierarchy", geonamesHierarchyTimestamp);
        }
        
        final Date geonamesTimestamp = geonamesImporter.lastRemoteModified();
        if (geonamesTimestamp.after(getLastModified("geonames")))
        {
            geonamesImporter.purge();
            geonamesImporter.importData();
            setLastModified("geonames", geonamesTimestamp);
        }
        
        final Date boundariesTimestamp = boundaryImporter.lastRemoteModified();
        if (boundariesTimestamp.after(getLastModified("geoboundaries")))
        {
            boundaryImporter.purge();
            boundaryImporter.importData();
            setLastModified("geoboundaries", boundariesTimestamp);
        }
        
        final Date ipTimestamp = ipLookupImporter.lastRemoteModified();
        if (ipTimestamp.after(getLastModified("geoip")))
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