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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.springframework.data.util.CloseableIterator;

import com.ethlo.geodata.GeoConstants;
import com.ethlo.geodata.dao.FeatureCodeDao;
import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.dao.file.FileFeatureCodeDao;
import com.ethlo.geodata.dao.file.FileMmapLocationDao;
import com.ethlo.geodata.io.BinaryBoundaryEncoder;
import com.ethlo.geodata.model.BoundaryData;
import com.ethlo.geodata.model.MapFeature;
import com.ethlo.geodata.model.RawLocation;
import com.ethlo.geodata.util.GeometryUtil;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;

public class GeoNamesBoundaryImporter
{
    public static final int MAX_SIZE = 2_000;
    public static final int MAX_PIECES = 100_000;

    private static final Path input = Paths.get("/home/morten/Downloads/allshapes.txt");
    private static final Path baseDirectory = Paths.get("/tmp/geodata/");

    private final BinaryIndexedFileWriter<BoundaryData> binaryIndexedFileWriter = new BinaryIndexedFileWriter<>(baseDirectory, "boundaries")
    {
        @Override
        protected void write(final BoundaryData data, final DataOutputStream out) throws IOException
        {
            BinaryBoundaryEncoder.write(data, out);
        }
    };

    public GeoNamesBoundaryImporter(final Map<Integer, MapFeature> featureCodes, final LocationDao locationDao)
    {
        final List<String> columns = Arrays.asList("id", "json");
        final AtomicInteger processed = new AtomicInteger();
        try (final CloseableIterator<Map<String, String>> boundaryIterator = new CsvFileIterator<>(input, columns, true, 1, i -> i)
        {
            @Override
            public Map<String, String> next()
            {
                final int current = processed.incrementAndGet();
                if (current % 1000 == 0)
                {
                    System.out.println(current);
                }
                return super.next();
            }
        })
        {
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
                    final MapFeature mapFeature = featureCodes.get(location.get().getMapFeatureId());
                    final String key = mapFeature.getKey();
                    return GeoConstants.ADMINISTRATIVE_OR_ABOVE.contains(key) && !key.equals("A.ADM3") && !key.equals("A.ADM4");
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
                        final double fullArea = full.getArea();
                        bufferList.add(full);

                        final AtomicInteger index = new AtomicInteger(1);
                        final List<BoundaryData> list = GeometryUtil.split(id, geometry, MAX_SIZE, MAX_PIECES).stream()
                                .map(tileGeometry -> new BoundaryData(id, index.getAndIncrement(), tileGeometry.getEnvelopeInternal(), fullArea, tileGeometry))
                                .collect(Collectors.toList());
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

            binaryIndexedFileWriter.writeData(tileIterator);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    public static void main(String[] args)
    {
        final FeatureCodeDao featureCodeDao = new FileFeatureCodeDao(baseDirectory);
        final LocationDao locationDao = new FileMmapLocationDao(baseDirectory);
        locationDao.load();
        new GeoNamesBoundaryImporter(featureCodeDao.load(), locationDao);
    }
}
