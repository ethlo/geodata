package com.ethlo.geodata.importer.jdbc;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.ethlo.geodata.http.ResourceUtil;
import com.ethlo.geodata.importer.GeonamesBoundaryImporter;

@Component
public class JdbcGeonamesBoundaryImporter
{
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    
    @Value("${geodata.geonames.source.boundaries}")
    private String geoNamesBoundaryUrl;
    
    @Autowired
    public void setDataSource(DataSource dataSource)
    {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    public void importBoundaries() throws IOException, SQLException
    {
        final File boundaryFile = ResourceUtil.fetchZipEntry("geonames_boundary", geoNamesBoundaryUrl, "shapes_all_low.txt");
        final GeonamesBoundaryImporter importer = new GeonamesBoundaryImporter(boundaryFile);

        final String sql = "INSERT INTO geoboundaries(id, raw_polygon, coord) VALUES(:id, ST_MPolyFromText(:poly), ST_Centroid(ST_MPolyFromText(:poly)))";
        importer.processFile(entry->
        {
            jdbcTemplate.update(sql, entry);
        });
    }
}
