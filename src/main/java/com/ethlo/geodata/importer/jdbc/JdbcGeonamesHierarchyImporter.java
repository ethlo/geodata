package com.ethlo.geodata.importer.jdbc;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.util.ResourceUtil;

@Component
public class JdbcGeonamesHierarchyImporter implements PersistentImporter
{
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    
    @Value("${geodata.geonames.source.hierarchy}")
    private String geoNamesHierarchyUrl;
    
    @Override
    public void importData() throws IOException
    {
        final String[] hierarchyUrlParts = StringUtils.split(geoNamesHierarchyUrl, "|");
        final Map.Entry<Date, File> hierarchyFile = ResourceUtil.fetchZipEntry("geonames_hierarchy", hierarchyUrlParts[0], hierarchyUrlParts[1]);
        doUpdate(hierarchyFile.getValue());
    }

    @Override
    public void purge()
    {
        jdbcTemplate.update("DELETE FROM geohierarchy", Collections.emptyMap());
    }

    private void doUpdate(File hierarchyFile) throws IOException
    {
        
        jdbcTemplate.update("INSERT INTO geohierarchy VALUES(:data)", Collections.singletonMap("data", java.nio.file.Files.readAllBytes(hierarchyFile.toPath())));
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return new Date(ResourceUtil.getLastModified(geoNamesHierarchyUrl).getTime());
    }
}
