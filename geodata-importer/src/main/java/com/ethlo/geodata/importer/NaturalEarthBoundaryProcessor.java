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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.stream.XMLStreamException;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.geobuf.GeobufGeometry;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.OutputStreamOutStream;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.opengis.feature.simple.SimpleFeature;

import com.ethlo.geodata.model.CompactSerializable;
import com.ethlo.geodata.util.JsonUtil;
import com.ethlo.geodata.util.Kml2GeoJson;
import com.ethlo.geodata.util.ResourceUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class NaturalEarthBoundaryProcessor
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
        final List<String> countryColumns = Arrays.asList("iso", "iso3", "iso_numeric", "fips", "country", "capital", "area", "population", "continent", "tld", "currency_Code", "currency_name", "phone", "postal_code_format", "postal_code_regex", "languages", "geonameid");
        final String geoNamesCountryInfoUrl = "http://download.geonames.org/export/dump/countryInfo.txt";
        final Map<String, Integer> isoToId = new HashMap<>();
        new BaseCsvFileImporter<>(Paths.get("/tmp/geodata"), "boundaries", geoNamesCountryInfoUrl, countryColumns, true, 1)
        {
            @Override
            protected CompactSerializable processLine(final Map<String, String> next)
            {
                isoToId.put(next.get("iso"), Integer.parseInt(next.get("geonameid")));
                return null;
            }
        }.importData();

        //final String countriesUrl = "https://www.naturalearthdata.com/http//www.naturalearthdata.com/download/10m/cultural/ne_10m_admin_0_countries_lakes.zip|ne_10m_admin_0_countries_lakes.shp";
        final Path dir = Paths.get("/tmp/geodata/boundaries");
        final NaturalEarthBoundaryProcessor loader = new NaturalEarthBoundaryProcessor();

        //final Map.Entry<Date, File> file = ResourceUtil.fetchResource("natural_earth_countries", countriesUrl);
        //final ShapefileDataStore store = new ShapefileDataStore().toFile().toURL());
        final File shapefile = Paths.get("/home/morten/Downloads/countries_shape/ne_10m_admin_0_countries_lakes.shp").toFile();
        final FileDataStore store = FileDataStoreFinder.getDataStore(shapefile);

        SimpleFeatureSource source = store.getFeatureSource();
        SimpleFeatureCollection featureCollection = source.getFeatures();
        final SimpleFeatureIterator simpleFeatureIterator = featureCollection.features();
        while (simpleFeatureIterator.hasNext())
        {
            final SimpleFeature sf = simpleFeatureIterator.next();
            final String countryCode = (String) sf.getAttribute("ISO_A2");
            final Geometry geometry = (Geometry) sf.getDefaultGeometry();
            final Integer id = isoToId.get(countryCode);
            if (id != null)
            {
                output(dir, id + "", geometry);
                System.out.println(countryCode + ": " + geometry.getCoordinates().length);
            }
            else
            {
                System.out.println("No id for " + countryCode);
            }
        }
    }

    private static void output(Path baseDir, final String countryCode, final Geometry geometry)
    {
        GeobufGeometry geobufGeometry = new GeobufGeometry();
        final GeoJsonWriter geoJsonWriter = new GeoJsonWriter();
        try (final OutputStream out = new BufferedOutputStream(Files.newOutputStream(baseDir.resolve(countryCode + ".geobuf")));
             final Writer jsonOut = Files.newBufferedWriter(baseDir.resolve(countryCode + ".geojson")))
        {
            geobufGeometry.encode(geometry, out);
            geoJsonWriter.write(geometry, jsonOut);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static void importGeoJson(final Path dir, final NaturalEarthBoundaryProcessor loader, final int id, final String continentCode, final String countryName) throws IOException, ParseException
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
