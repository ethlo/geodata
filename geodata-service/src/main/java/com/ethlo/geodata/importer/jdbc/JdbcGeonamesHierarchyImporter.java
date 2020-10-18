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
import java.io.LineNumberReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ethlo.geodata.importer.DataType;
import com.ethlo.geodata.util.ResourceUtil;

@Component
public class JdbcGeonamesHierarchyImporter implements PersistentImporter
{
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${geodata.geonames.source.hierarchy}")
    private String geoNamesHierarchyUrl;

    @Override
    @Transactional
    public long importData()
    {
        try
        {
            final Map.Entry<Date, File> hierarchyFile = ResourceUtil.fetchResource(DataType.HIERARCHY, geoNamesHierarchyUrl);
            return doUpdate(hierarchyFile.getValue());
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    @Override
    public void purge()
    {
        jdbcTemplate.update("DELETE FROM geohierarchy", Collections.emptyMap());
    }

    private long doUpdate(File hierarchyFile) throws IOException
    {
        final AtomicInteger count = new AtomicInteger();
        try (final LineNumberReader r = new LineNumberReader(Files.newBufferedReader(hierarchyFile.toPath())))
        {
            final Map<Long, Long> buffer = new HashMap<>(100_000);

            String line;
            while ((line = r.readLine()) != null)
            {
                final String[] parts = line.split("\t");
                final long parentId = Long.parseLong(parts[0]);
                final long childId = Long.parseLong(parts[1]);
                buffer.put(childId, parentId);
                count.incrementAndGet();
            }

            flush(buffer);
        }
        return count.get();
    }

    private void flush(final Map<Long, Long> buffer)
    {
        final Map<String, ?>[] array = JdbcGeonamesImporter.newArray(buffer.size());
        int index = 0;
        for (Map.Entry<Long, Long> e : buffer.entrySet())
        {
            final Map<String, Object> params = new TreeMap<>();
            params.put("id", e.getKey());
            params.put("parent_id", e.getValue());
            array[index++] = params;
        }

        try
        {
            jdbcTemplate.batchUpdate("INSERT INTO geohierarchy (id, parent_id) VALUES(:id, :parent_id)", array);
        }
        catch (DuplicateKeyException ignored)
        {

        }
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return new Date(ResourceUtil.getLastModified(geoNamesHierarchyUrl).getTime());
    }
}
