package com.ethlo.geodata.importer;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.http.ResourceUtil;

@Service
public class GeonamesImporter
{
    private static final Logger logger = LoggerFactory.getLogger(GeonamesImporter.class);

    private NamedParameterJdbcTemplate jdbcTemplate;
    
    @Value("${geodata.geonames.source}")
    private String url;

    @Autowired
    public void setDataSource(DataSource dataSource)
    {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    public void importLocations() throws IOException
    {
        final String sql = "INSERT INTO geonames (id, name, feature_class, feature_code, country_code, population, elevation_meters, timezone, last_modified, lat, lng, coord) VALUES ("
                        + ":id, :name, :feature_class, :feature_code, :country_code, :population, :elevation_meters, :timezone, :last_modified, :lat, :lng, :coord)";

        final File file = ResourceUtil.fetchZipEntry("geonames", url, "allCountries.txt");
        processFile(file, entry->
        {
            //System.out.println(StringUtils.arrayToCommaDelimitedString(entry));
            final Map<String, Object> paramMap = new TreeMap<>();
            
            final String lat = stripToNull(entry[4]);
            final String lng = stripToNull(entry[5]);
            final String coord = lat != null ? ("POINT(" + lat + " " + lng + ")") : null;
            
            paramMap.put("id", stripToNull(entry[0]));
            paramMap.put("name", stripToNull(entry[1]));
            paramMap.put("lat", lat);
            paramMap.put("lng", lng);
            paramMap.put("feature_class", stripToNull(entry[6]));
            paramMap.put("feature_code", stripToNull(entry[7]));
            paramMap.put("country_code", stripToNull(entry[8]));
            paramMap.put("population", stripToNull(entry[14]));
            paramMap.put("elevation_meters", stripToNull(entry[15]));
            paramMap.put("timezone", stripToNull(entry[18]));
            paramMap.put("last_modified", stripToNull(null));
            paramMap.put("coord", coord);
            
            jdbcTemplate.update(sql, paramMap);
        });
    }
    
    private void processFile(File csvFile, Consumer<String[]> sink) throws IOException
    {
        int count = 0;
        try (final BufferedReader reader = new BufferedReader(new FileReader(csvFile)))
        {
            String line;
            while ((line = reader.readLine()) !=  null)
            {
                final String[] split = StringUtils.delimitedListToStringArray(line, "\t");
                sink.accept(split);
                count++;
                
                if (count % 1000 == 0)
                {
                    logger.info("Progress: {}", count);
                }
            }
        }
    }
}