package com.ethlo.geodata.importer.jdbc;

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
    public long importData() throws IOException
    {
        final Entry<Date, File> boundaryFile = ResourceUtil.fetchResource("geonames_boundary", geoNamesBoundaryUrl);
        final GeonamesBoundaryImporter importer = new GeonamesBoundaryImporter(boundaryFile.getValue());

        final String sql = "INSERT INTO geoboundaries(id, raw_polygon, coord, area) VALUES(:id, ST_GeomFromText(:poly), ST_Centroid(ST_GeomFromText(:poly)), st_area(ST_GeomFromText(:poly)))";
        return importer.processFile(entry -> jdbcTemplate.update(sql, entry));
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return ResourceUtil.getLastModified(geoNamesBoundaryUrl);
    }
}
