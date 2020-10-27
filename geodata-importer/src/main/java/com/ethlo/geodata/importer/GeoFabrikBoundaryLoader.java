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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.springframework.util.Assert;

import com.ethlo.geodata.dao.file.RtreeRepository;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.RTreePayload;
import com.ethlo.geodata.util.JsonUtil;
import com.ethlo.geodata.util.Kml2GeoJson;
import com.ethlo.geodata.util.ResourceUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Stopwatch;
import com.google.common.collect.AbstractIterator;

public class GeoFabrikBoundaryLoader
{
    private static final String BASE_URL = "http://download.geofabrik.de/";
    private static final GeometryFactory geometryFactory = new GeometryFactory();

    private final Map<String, String> continents = new LinkedHashMap<>()
    {{
        put("AF", "africa");
        put("AS", "asia");
        put("EU", "europe");
        put("NA", "north-america");
        put("OC", "oceania");
        put("SA", "south-america");
        put("AN", "antarctica");
    }};

    public static void main(String[] args) throws IOException
    {
        final Path dir = Paths.get("/tmp/geodata/boundaries");
        final GeoFabrikBoundaryLoader loader = new GeoFabrikBoundaryLoader();

        JsonUtil.write(dir.resolve("3144096.json"), loader.loadGeoJson("EU", "norway"));
        JsonUtil.write(dir.resolve("2623032.json"), loader.loadGeoJson("EU", "denmark"));

        final RtreeRepository boundaryRepository = new RtreeRepository(new AbstractIterator<>()
        {
            private final Iterator<Path> filenames = getFileNames().iterator();

            private List<Path> getFileNames()
            {
                try (final Stream<Path> files = Files.list(dir))
                {
                    return files.filter(p -> p.getFileName().toString().endsWith(".json")).collect(Collectors.toList());
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
                    try (final Reader reader = Files.newBufferedReader(path))
                    {
                        final int id = getIdFromFilename(path.getFileName());
                        final Geometry geometry = new GeoJsonReader(geometryFactory).read(reader);
                        return new RTreePayload(id, geometry.getArea(), geometry.getEnvelopeInternal());
                    }
                    catch (IOException exc)
                    {
                        throw new UncheckedIOException(exc);
                    }
                    catch (ParseException exc)
                    {
                        throw new UncheckedIOException(new IOException(exc.getMessage(), exc));
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
        });

        final Stopwatch stopwatch = Stopwatch.createStarted();
        for (int i = 0; i < 1000; i++)
        {
            final double lat = 61;
            final double lng = 10.7;

            final Coordinates coordinate = Coordinates.from(lat, lng);

            final Long hit = boundaryRepository.find(coordinate);

            final Point point = geometryFactory.createPoint(new Coordinate(lng, lat));

            final Path path = dir.resolve(hit + ".json");
            System.out.println(path);
            try (final Reader reader = Files.newBufferedReader(path))
            {
                final Geometry geom = new GeoJsonReader().read(reader);
                if (geom instanceof GeometryCollection)
                {
                    final GeometryCollection coll = (GeometryCollection) geom;
                    for (int num = 0; num < coll.getNumGeometries(); num++)
                    {
                        final Geometry geomElem = coll.getGeometryN(num);
                        //System.out.println(geomElem);
                        final boolean actualInside = geomElem.contains(point);
                        //System.out.println(actual);
                    }
                }
            }
            catch (IOException exc)
            {
                throw new UncheckedIOException(exc);
            }
            catch (ParseException exc)
            {
                throw new UncheckedIOException(new IOException(exc.getMessage(), exc));
            }
        }
        System.out.println(stopwatch);
    }

    public ObjectNode loadGeoJson(String continentCode, String countryName) throws IOException
    {
        final String continentName = Objects.requireNonNull(continents.get(continentCode.toUpperCase()), "No continent with code " + continentCode);
        final String url = BASE_URL + continentName + "/" + countryName + ".kml";
        final Map.Entry<Date, File> file = ResourceUtil.fetchResource((continentName + "_" + countryName + ".kml").toLowerCase(), url);
        try (final Reader reader = Files.newBufferedReader(file.getValue().toPath()))
        {
            return Kml2GeoJson.parse(reader);
        }
        catch (XMLStreamException e)
        {
            throw new UncheckedIOException(new IOException("Unable to read KML", e));
        }
    }
}
