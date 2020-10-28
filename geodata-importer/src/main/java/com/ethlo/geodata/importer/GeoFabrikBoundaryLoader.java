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
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.xml.stream.XMLStreamException;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.OutputStreamOutStream;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.geojson.GeoJsonReader;

import com.ethlo.geodata.util.JsonUtil;
import com.ethlo.geodata.util.Kml2GeoJson;
import com.ethlo.geodata.util.ResourceUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class GeoFabrikBoundaryLoader
{
    private static final String BASE_URL = "http://download.geofabrik.de/";

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

    public static void main(String[] args) throws IOException, ParseException
    {
        final Path dir = Paths.get("/tmp/geodata/boundaries");
        final GeoFabrikBoundaryLoader loader = new GeoFabrikBoundaryLoader();

        importGeoJson(dir, loader, 3144096, "EU", "norway");
        importGeoJson(dir, loader, 2623032, "EU", "denmark");
    }

    private static void importGeoJson(final Path dir, final GeoFabrikBoundaryLoader loader, final int id, final String continentCode, final String countryName) throws IOException, ParseException
    {
        final ObjectNode jsonNode = loader.loadGeoJson(continentCode, countryName);
        final Geometry geometry = new GeoJsonReader().read(jsonNode.toPrettyString());
        JsonUtil.write(dir.resolve(id + ".json"), jsonNode);
        try (final OutputStream out = Files.newOutputStream(dir.resolve(id + ".wkb")))
        {
            new WKBWriter(2).write(geometry, new OutputStreamOutStream(out));
        }
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
