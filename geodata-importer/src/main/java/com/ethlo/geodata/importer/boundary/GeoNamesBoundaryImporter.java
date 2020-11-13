package com.ethlo.geodata.importer.boundary;

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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.springframework.data.util.CloseableIterator;

import com.ethlo.geodata.DataType;
import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.importer.BinaryIndexedFileWriter;
import com.ethlo.geodata.importer.CsvFileIterator;
import com.ethlo.geodata.io.BinaryBoundaryEncoder;
import com.ethlo.geodata.model.BoundaryData;
import com.ethlo.geodata.model.RawLocation;
import com.ethlo.geodata.util.GeometryUtil;
import com.ethlo.geodata.util.Kml2GeoJson;
import com.ethlo.geodata.util.SerializationUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;

public class GeoNamesBoundaryImporter
{
    public static final int MAX_PIECES = 100_000;
    private final List<String> columns = Arrays.asList("id", "json");
    private final LocationDao locationDao;
    private final int maxTileSize;

    private final BinaryIndexedFileWriter<BoundaryData> binaryIndexedFileWriter;
    private final Predicate<RawLocation> includeGeometryFilter;

    public GeoNamesBoundaryImporter(final LocationDao locationDao,
                                    final Path baseDirectory,
                                    final int maxTileSize,
                                    final Predicate<RawLocation> includeGeometryFilter)
    {
        this.locationDao = locationDao;
        this.maxTileSize = maxTileSize;
        this.includeGeometryFilter = includeGeometryFilter;

        this.binaryIndexedFileWriter = new BinaryIndexedFileWriter<>(baseDirectory, DataType.BOUNDARIES)
        {
            @Override
            protected void write(final BoundaryData data, final DataOutputStream out) throws IOException
            {
                BinaryBoundaryEncoder.write(data, out);
            }
        };
    }

    public CloseableIterator<BoundaryData> processTsv(final Path inputTsvFile, Consumer<Integer> progress)
    {
        final AtomicInteger processed = new AtomicInteger();
        final CloseableIterator<Map<String, String>> boundaryIterator = new CsvFileIterator<>(inputTsvFile, columns, true, 1, i -> i)
        {
            @Override
            public Map<String, String> next()
            {
                progress.accept(processed.incrementAndGet());
                return super.next();
            }
        };

        final Iterator<Map<String, String>> filtered = Iterators.filter(boundaryIterator, e ->
        {
            final int id = Integer.parseInt(e.get("id"));
            final Optional<RawLocation> location = locationDao.get(id);
            if (location.isEmpty())
            {
                return false;
            }
            else
            {
                return includeGeometryFilter.test(location.get());
            }
        });

        final Iterator<BoundaryData> tileIterator = new AbstractIterator<>()
        {
            private Iterator<BoundaryData> buffer = Collections.emptyIterator();

            @Override
            protected BoundaryData computeNext()
            {
                if (buffer.hasNext())
                {
                    return buffer.next();
                }

                if (!filtered.hasNext())
                {
                    return endOfData();
                }

                final Map<String, String> next = filtered.next();
                final int id = Integer.parseInt(next.get("id"));

                final List<BoundaryData> bufferList = new LinkedList<>();
                try
                {
                    final Geometry geometry = new GeoJsonReader().read(next.get("json"));

                    final BoundaryData full = new BoundaryData(id, 0, geometry.getEnvelopeInternal(), geometry.getArea(), geometry);
                    bufferList.add(full);
                    final List<BoundaryData> list = processSingleGeometry(full);
                    bufferList.addAll(list);
                    buffer = bufferList.iterator();
                }
                catch (ParseException e)
                {
                    throw new UncheckedIOException(new IOException(e));
                }

                return computeNext();
            }
        };

        return SerializationUtil.wrapClosable(tileIterator, boundaryIterator);
    }

    private List<BoundaryData> processSingleGeometry(final BoundaryData full)
    {
        final AtomicInteger index = new AtomicInteger(1);
        final int id = full.getId();
        final Geometry geometry = full.getGeometry();
        return GeometryUtil.split(id, geometry, maxTileSize, MAX_PIECES).stream()
                .map(tileGeometry -> new BoundaryData(id, index.getAndIncrement(), tileGeometry.getEnvelopeInternal(), full.getArea(), tileGeometry))
                .collect(Collectors.toList());
    }

    public Iterator<BoundaryData> processKml(final Path path)
    {
        try (final Reader reader = Files.newBufferedReader(path))
        {
            final int id = getIdFromFilename(path);
            final ObjectNode node = Kml2GeoJson.parse(reader);
            final Geometry geometry = new GeoJsonReader().read(node.toString());
            return writeSingleGeometryWithSplits(id, geometry);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        catch (ParseException | XMLStreamException e)
        {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    private Iterator<BoundaryData> writeSingleGeometryWithSplits(final int id, final Geometry geometry) throws IOException
    {
        final BoundaryData full = new BoundaryData(id, 0, geometry.getEnvelopeInternal(), geometry.getArea(), geometry);
        final List<BoundaryData> list = processSingleGeometry(full);
        list.add(0, full);
        return list.iterator();
    }

    private int getIdFromFilename(final Path path)
    {
        final String filename = path.getFileName().toString();
        final String basename = filename.split("\\.")[0];
        final String idStr = basename.split("__")[0];
        return Integer.parseInt(idStr);
    }

    public Iterator<BoundaryData> processGeoJson(final Path path)
    {
        try (final Reader reader = Files.newBufferedReader(path))
        {
            final int id = getIdFromFilename(path);
            final Geometry geometry = new GeoJsonReader().read(reader);
            return writeSingleGeometryWithSplits(id, geometry);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        catch (ParseException e)
        {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    public int write(final Iterator<BoundaryData> data) throws IOException
    {
        return this.binaryIndexedFileWriter.writeData(data);
    }
}
