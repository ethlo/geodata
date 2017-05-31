package com.ethlo.geodata.importer.jdbc;

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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Map.Entry;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.ethlo.geodata.importer.GeonamesBoundaryImporter;
import com.ethlo.geodata.util.ResourceUtil;

@Component
public class JdbcGeonamesBoundaryImporter implements PersistentImporter
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

    @Override
    public void purge() throws IOException
    {
        jdbcTemplate.update("DELETE FROM geoboundaries", Collections.emptyMap());
    }

    @Override
    public void importData() throws IOException
    {
        final Entry<Date, File> boundaryFile = ResourceUtil.fetchResource("geonames_boundary", geoNamesBoundaryUrl);
        final GeonamesBoundaryImporter importer = new GeonamesBoundaryImporter(boundaryFile.getValue());

        final String sql = "INSERT INTO geoboundaries(id, raw_polygon, coord, area) VALUES(:id, ST_GeomFromText(:poly), ST_Centroid(ST_GeomFromText(:poly)), st_area(ST_GeomFromText(:poly)))";
        importer.processFile(entry->
        {
            jdbcTemplate.update(sql, entry);
        });
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return ResourceUtil.getLastModified(geoNamesBoundaryUrl);
    }
}