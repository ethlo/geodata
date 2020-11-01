package com.ethlo.geodata.dao.file;

/*-
 * #%L
 * geodata-common
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.geotools.data.geobuf.GeobufGeometry;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import com.ethlo.geodata.dao.BoundaryDao;
import com.ethlo.geodata.model.RTreePayload;
import com.google.common.collect.AbstractIterator;

@Repository
public class FileBoundaryDao implements BoundaryDao
{
    private static final GeometryFactory geometryFactory = new GeometryFactory();
    private static final GeobufGeometry geobufGeometry = new GeobufGeometry();

    private final Path basePath;

    public FileBoundaryDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.basePath = basePath.resolve("boundaries");
    }

    @Override
    public Optional<byte[]> findGeoJsonById(final int id)
    {
        final Path fileGeoJson = basePath.resolve(id + ".geojson");
        if (Files.exists(fileGeoJson))
        {
            try
            {
                return Optional.of(Files.readAllBytes(fileGeoJson));
            }
            catch (IOException exc)
            {
                throw new UncheckedIOException(exc);
            }
        }
        return Optional.empty();
    }

    @Override
    public Iterator<RTreePayload> entries()
    {
        return new AbstractIterator<>()
        {
            private final Iterator<Path> filenames = getFileNames().iterator();

            private List<Path> getFileNames()
            {
                try (final Stream<Path> files = Files.list(basePath))
                {
                    return files.filter(p -> p.getFileName().toString().endsWith(".geobuf")).collect(Collectors.toList());
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            protected RTreePayload computeNext()
            {
                if (filenames.hasNext())
                {
                    final Path path = filenames.next();
                    try (final InputStream in = new BufferedInputStream(Files.newInputStream(path)))
                    {
                        final int id = getIdFromFilename(path.getFileName());
                        final Geometry geometry = geobufGeometry.decode(in);
                        return new RTreePayload(id, geometry.getArea(), geometry.getEnvelopeInternal());
                    }
                    catch (IOException exc)
                    {
                        throw new UncheckedIOException(exc);
                    }
                }

                return endOfData();
            }

            private int getIdFromFilename(final Path path)
            {
                final String filename = path.getFileName().toString();
                final int dotIndex = filename.indexOf(".");
                Assert.isTrue(dotIndex > -1, "A dot was expected in the filename " + filename);
                return Integer.parseInt(filename.substring(0, dotIndex));
            }
        };
    }

    @Override
    public Optional<Geometry> findGeometryById(final int id)
    {
        final Path file = basePath.resolve(id + ".geobuf");
        if (Files.exists(file))
        {
            try (InputStream reader = new BufferedInputStream(Files.newInputStream(file)))
            {
                return Optional.ofNullable(geobufGeometry.decode(reader));
            }
            catch (IOException exc)
            {
                throw new UncheckedIOException(exc);
            }
        }
        return Optional.empty();
    }
}
