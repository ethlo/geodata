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

import com.ethlo.geodata.dao.file.FileBoundaryDao;
import com.ethlo.geodata.model.CompactSerializable;

public class GeoNamesBoundaryLoader
{
    private final Path dir = Paths.get("/tmp/geodata/");

    public GeoNamesBoundaryLoader()
    {
        final List<String> countryColumns = Arrays.asList("id", "json");
        new BaseCsvFileImporter<>(dir, "boundaries", "file:///home/morten/Downloads/allshapes.txt", countryColumns, true, 1)
        {
            @Override
            protected CompactSerializable processLine(final Map<String, String> next)
            {
                final int id = Integer.parseInt(next.get("id"));
                try
                {
                    final Geometry geometry = new GeoJsonReader().read(next.get("json"));
                    new FileBoundaryDao(dir).save(id, geometry);
                }
                catch (ParseException e)
                {
                    throw new UncheckedIOException(new IOException(e));
                }
                return null;
            }
        }.importData();
    }

    public static void main(String[] args)
    {
        new GeoNamesBoundaryLoader();
    }
}
