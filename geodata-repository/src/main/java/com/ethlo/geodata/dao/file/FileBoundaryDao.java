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
import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import com.ethlo.geodata.dao.BoundaryDao;
import com.ethlo.geodata.io.BinaryBoundaryEncoder;
import com.ethlo.geodata.model.RTreePayload;
import com.ethlo.geodata.util.GeometryUtil;
import com.google.common.collect.AbstractIterator;

@Repository
public class FileBoundaryDao implements BoundaryDao
{
    public static final int MAX_SIZE = 2_000;
    public static final int MAX_PIECES = 10_000;

    public static final String CACHE_DIRECTORY_NAME = "cache";
    public static final String BOUNDARIES_DIRECTORY_NAME = "boundaries";

    private final BinaryBoundaryEncoder binaryEncoder = new BinaryBoundaryEncoder();
    private final Path boundaryPath;
    private final Path cachePath;

    public FileBoundaryDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.boundaryPath = basePath.resolve(BOUNDARIES_DIRECTORY_NAME);
        this.cachePath = boundaryPath.resolve(CACHE_DIRECTORY_NAME);
        try
        {
            Files.createDirectories(cachePath);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Cannot create directory", e);
        }
    }

    @Override
    public Iterator<RTreePayload> entries()
    {
        Iterator<Path> files;
        try
        {
            files = Files.walk(cachePath)
                    .filter(p ->
                    {
                        final String filename = p.getFileName().toString();
                        return filename.contains("-") && filename.endsWith(".ego");
                    }).iterator();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }

        return new AbstractIterator<>()
        {
            @Override
            protected RTreePayload computeNext()
            {
                if (files.hasNext())
                {
                    final Path path = files.next();
                    final Map.Entry<Integer, Integer> idAndSubdivideIndex = getIdAndSubdivideIndexFromFilename(path.getFileName());
                    final Map.Entry<Double, Envelope> envelopeAndTotalArea = loadEnvelopeAndTotalArea(path);
                    return new RTreePayload(idAndSubdivideIndex.getKey(), idAndSubdivideIndex.getValue(), envelopeAndTotalArea.getKey(), envelopeAndTotalArea.getValue());
                }

                return endOfData();
            }

            private Map.Entry<Integer, Integer> getIdAndSubdivideIndexFromFilename(final Path path)
            {
                final String filename = path.getFileName().toString();
                final int dotIndex = filename.indexOf(".");
                Assert.isTrue(dotIndex > -1, "A dot was expected in the filename " + filename);
                final String basename = filename.substring(0, dotIndex);
                final String[] parts = basename.split("-");
                return new AbstractMap.SimpleEntry<>(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            }
        };
    }

    private Map.Entry<Double, Envelope> loadEnvelopeAndTotalArea(final Path path)
    {
        try (final InputStream in = new BufferedInputStream(Files.newInputStream(path)))
        {
            return binaryEncoder.readEnvelopeAndTotalArea(in);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    @Override
    public Optional<Geometry> findGeometryById(final int id)
    {
        final Path file = getSubDirectory(id).resolve(Integer.toString(id)).resolve(id + ".ego");
        if (Files.exists(file))
        {
            try (final InputStream in = new BufferedInputStream(Files.newInputStream(file)))
            {
                return Optional.of(new BinaryBoundaryEncoder().read(in));
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Geometry> findGeometryById(final int id, final int subdivideIndex)
    {
        final Path file = getSubDirectory(id).resolve(Integer.toString(id)).resolve(id + "-" + subdivideIndex + ".ego");
        try (final InputStream in = new BufferedInputStream(Files.newInputStream(file)))
        {
            return Optional.of(new BinaryBoundaryEncoder().read(in));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void save(final int id, final Geometry geometry)
    {
        final double totalArea = geometry.getArea();
        outputFull(id, geometry);
        outputTiles(id, geometry, totalArea);
    }

    private void outputTiles(final int id, final Geometry geometry, final double totalArea)
    {
        int index = 0;
        final Collection<Geometry> split = GeometryUtil.split(id, geometry, MAX_SIZE, MAX_PIECES);
        for (Geometry geom : split)
        {
            outputForLookup(id, index++, totalArea, geom);
        }
    }

    private void outputForLookup(final int id, final int index, final double totalArea, final Geometry geometry)
    {
        final Path path = ensureDir(id);
        try (final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path.resolve(id + "-" + index + ".ego")))))
        {
            binaryEncoder.write(totalArea, geometry, out);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private Path ensureDir(final int id)
    {
        final Path subDir = getSubDirectory(id);
        final Path path = subDir.resolve(Integer.toString(id));
        try
        {
            Files.createDirectories(path);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        return path;
    }

    private Path getSubDirectory(final int id)
    {
        final String fullId = StringUtils.leftPad(Integer.toString(id), 9, '0');
        return cachePath.resolve(fullId.substring(0, 3)).resolve(fullId.substring(3, 6)).resolve(fullId.substring(6, 9));
    }

    private void outputFull(final int id, final Geometry geometry)
    {
        final Path path = ensureDir(id);
        try (final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path.resolve(id + ".ego"))))
             //final Writer writer = Files.newBufferedWriter(boundaryPath.resolve(id + ".geojson"))
        )
        {
            binaryEncoder.write(geometry.getArea(), geometry, out);
            //new GeoJsonWriter().write(geometry, writer);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

}
