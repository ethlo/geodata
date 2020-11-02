package com.ethlo.geodata.importer;

/*-
 * #%L
 * geodata-importer
 * %%
 * Copyright (C) 2017 - 2020 Morten Haraldsen (ethlo)
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.springframework.data.util.CloseableIterator;

import com.ethlo.geodata.dao.BoundaryDao;
import com.ethlo.geodata.dao.file.FileBoundaryDao;

public class GeoNamesBoundaryLoader
{
    private static final Path input = Paths.get("/home/morten/Downloads/allshapes.txt");
    private static final Path baseDirectory = Paths.get("/tmp/geodata/");

    public GeoNamesBoundaryLoader()
    {
        final List<String> columns = Arrays.asList("id", "json");
        final BoundaryDao boundaryDao = new FileBoundaryDao(baseDirectory);
        int processed = 0;
        try (final CloseableIterator<Map<String, String>> iter = new CsvFileIterator<>(input, columns, true, 1, i -> i))
        {
            while (iter.hasNext())
            {
                final Map<String, String> next = iter.next();
                final int id = Integer.parseInt(next.get("id"));
                try
                {
                    final Geometry geometry = new GeoJsonReader().read(next.get("json"));
                    boundaryDao.save(id, geometry);
                }
                catch (ParseException e)
                {
                    throw new UncheckedIOException(new IOException(e));
                }

                processed++;

                if (processed % 1_000 == 0)
                {
                    System.out.println("Imported " + processed);
                }
            }
        }
    }

    public static void main(String[] args)
    {
        new GeoNamesBoundaryLoader();
    }
}
