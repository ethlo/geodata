package com.ethlo.geodata.importer.jdbc;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

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
}
