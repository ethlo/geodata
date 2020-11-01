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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.stream.XMLStreamException;

import org.geotools.data.geobuf.GeobufGeometry;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.geodata.model.CompactSerializable;
import com.ethlo.geodata.util.GeometryUtil;
import com.ethlo.geodata.util.Kml2GeoJson;
import com.ethlo.geodata.util.ResourceUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class GeoFabrikBoundaryLoader
{
    private static final String BASE_URL = "http://download.geofabrik.de/";
    private static final Logger logger = LoggerFactory.getLogger(GeoFabrikBoundaryLoader.class);
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
    private final Path dir = Paths.get("/tmp/geodata/boundaries");

    public GeoFabrikBoundaryLoader()
    {
        final List<String> countryColumns = Arrays.asList("iso", "iso3", "iso_numeric", "fips", "country", "capital", "area", "population", "continent", "tld", "currency_Code", "currency_name", "phone", "postal_code_format", "postal_code_regex", "languages", "geonameid");
        final String geoNamesCountryInfoUrl = "http://download.geonames.org/export/dump/countryInfo.txt";
        new BaseCsvFileImporter<>(dir, "boundaries", geoNamesCountryInfoUrl, countryColumns, true, 0)
        {
            @Override
            protected CompactSerializable processLine(final Map<String, String> next)
            {
                final String name = next.get("country").toLowerCase();
                final String continent = next.get("continent");
                final int id = Integer.parseInt(next.get("geonameid"));
                importGeoJson(id, continent, name);
                return null;
            }
        }.importData();
    }

    public static void main(String[] args)
    {
        new GeoFabrikBoundaryLoader();
    }

    private void output(final int id, final int index, final Geometry geometry)
    {
        final GeobufGeometry geobufGeometry = new GeobufGeometry();
        final GeoJsonWriter geoJsonWriter = new GeoJsonWriter();
        try (final OutputStream out = new BufferedOutputStream(Files.newOutputStream(dir.resolve(id + "-" + index + ".geobuf")));
             final Writer jsonOut = Files.newBufferedWriter(dir.resolve(id + "-" + index + ".geojson")))
        {
            geobufGeometry.encode(geometry, out);
            geoJsonWriter.write(geometry, jsonOut);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private void importGeoJson(final int id, final String continentCode, final String countryName)
    {
        try
        {
            final ObjectNode jsonNode = fetchGeoJsonFromServer(continentCode, countryName);
            final Geometry geometry = new GeoJsonReader().read(jsonNode.toPrettyString());
            final Collection<Geometry> split = GeometryUtil.split(geometry, 1000, 1000);
            logger.info("Split {} into {} tiles", id, split.size());
            int index = 0;
            for (Geometry geom : split)
            {
                output(id, index++, geom);
            }
        }
        catch (IOException exc)
        {
            logger.warn(exc.getMessage());
        }
        catch (ParseException e)
        {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    public ObjectNode fetchGeoJsonFromServer(String continentCode, String countryName) throws IOException
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
