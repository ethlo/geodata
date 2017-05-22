package com.ethlo.geodata.importer.jdbc;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.importer.GeonamesImporter;
import com.ethlo.geodata.util.ResourceUtil;

@Component
public class JdbcGeonamesImporter implements PersistentImporter
{
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    
    @Value("${geodata.geonames.source.names}")
    private String geoNamesAllCountriesUrl;
    
    @Value("${geodata.geonames.source.hierarchy}")
    private String geoNamesHierarchyUrl;
    
    private Set<String> exclusions;
    
    @Value("${geodata.geonames.features.excluded}")
    public void setExclusions(String csv)
    {
        exclusions = StringUtils.commaDelimitedListToSet(csv);
    }
    
    @Autowired
    public void setDataSource(DataSource dataSource)
    {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void importData() throws IOException
    {
        final String[] hierarchyUrlParts = StringUtils.split(geoNamesHierarchyUrl, "|");
        final Map.Entry<Date, File> hierarchyFile = ResourceUtil.fetchZipEntry("geonames_hierarchy", hierarchyUrlParts[0], hierarchyUrlParts[1]);
        
        final String[] geoUrlParts = StringUtils.split(geoNamesAllCountriesUrl, "|");
        final Map.Entry<Date, File> allCountriesFile = ResourceUtil.fetchZipEntry("geonames", geoUrlParts[0], geoUrlParts[1]);
        
        doUpdate(allCountriesFile.getValue(), hierarchyFile.getValue());
    }

    @Override
    public void purge()
    {
        jdbcTemplate.update("DELETE FROM geonames", Collections.emptyMap());
    }

    private void doUpdate(File allCountriesFile, File hierarchyFile) throws IOException
    {
        final String sql = "INSERT INTO geonames (id, parent_id, name, feature_class, feature_code, country_code, population, elevation_meters, timezone, last_modified, lat, lng, coord) VALUES ("
                        + ":id, :parent_id, :name, :feature_class, :feature_code, :country_code, :population, :elevation_meters, :timezone, :last_modified, :lat, :lng, ST_GeomFromText(:poly))";

        final GeonamesImporter geonamesImporter = new GeonamesImporter.Builder()
            .allCountriesFile(allCountriesFile)
            .onlyHierarchical(false)
            .exclusions(exclusions)
            .hierarchyFile(hierarchyFile)
            .build();

        geonamesImporter.processFile(entry->
        {
            jdbcTemplate.update(sql, entry);
        });
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return new Date(Math.max(ResourceUtil.getLastModified(geoNamesAllCountriesUrl).getTime(), ResourceUtil.getLastModified(geoNamesHierarchyUrl).getTime()));
    }
}
