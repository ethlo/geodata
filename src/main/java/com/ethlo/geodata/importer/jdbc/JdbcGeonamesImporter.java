package com.ethlo.geodata.importer.jdbc;

import static org.hamcrest.CoreMatchers.nullValue;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.importer.GeonamesImporter;
import com.ethlo.geodata.model.Node;
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
        final Map.Entry<Date, File> hierarchyFile = ResourceUtil.fetchZipEntry("geonames_hierarchy", geoNamesHierarchyUrl, "hierarchy.txt");
        final Map.Entry<Date, File> allCountriesFile = ResourceUtil.fetchZipEntry("geonames", geoNamesAllCountriesUrl, "allCountries.txt");
        
        doUpdate(allCountriesFile.getValue(), hierarchyFile.getValue());
    }

    @Override
    public void purge()
    {
        jdbcTemplate.update("DELETE FROM geonames", Collections.emptyMap());
    }

    private void doUpdate(File allCountriesFile, File hierarchyFile) throws IOException
    {
        final String sql = "INSERT INTO geonames (id, parent_id, name, feature_class, feature_code, country_code, population, elevation_meters, timezone, last_modified, lat, lng) VALUES ("
                        + ":id, :parent_id, :name, :feature_class, :feature_code, :country_code, :population, :elevation_meters, :timezone, :last_modified, :lat, :lng)";

        final GeonamesImporter geonamesImporter = new GeonamesImporter.Builder()
            .allCountriesFile(allCountriesFile)
            .onlyHierarchical(false)
            .exclusions(exclusions)
            .hierarchyFile(hierarchyFile)
            .build();

        // Collect parent/child hierarchy
        final Map<Long, Long> childToParentMap = new HashMap<>();
        final Function<Map.Entry<Long,Long>, Void> hierarchyListener = r->
        {
            childToParentMap.put(r.getKey(), r.getValue());
            return null;
        };
        
        geonamesImporter.processFile(entry->
        {
            jdbcTemplate.update(sql, entry);
            
            if (entry.get("parent_id") != null)
            {
                hierarchyListener.apply(new AbstractMap.SimpleEntry<>(Long.parseLong(entry.get("id")), Long.parseLong(entry.get("parent_id"))));
            }
        });
        
        // Process hierarchy
        final Map<Long, Node> parents = new HashMap<>();
        childToParentMap.entrySet().forEach(e->
        {
            final Long parent = e.getValue();
            parents.put(parent, new Node(null, parent));
        });
        
        childToParentMap.entrySet().forEach(e->
        {
            final Long child = e.getKey();
            final Long parent = e.getValue();
            // TODO: Process hierarchy and serialize/store it as JSON or something quick to load!
        });
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return new Date(Math.max(ResourceUtil.getLastModified(geoNamesAllCountriesUrl).getTime(), ResourceUtil.getLastModified(geoNamesHierarchyUrl).getTime()));
    }
}
