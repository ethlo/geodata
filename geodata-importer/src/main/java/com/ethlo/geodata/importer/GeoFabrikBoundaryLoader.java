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
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.stream.XMLStreamException;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.CloseableIterator;

import com.ethlo.geodata.dao.file.FileBoundaryDao;
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
    private final Path dir = Paths.get("/tmp/geodata/");

    public GeoFabrikBoundaryLoader() throws IOException
    {
        final List<String> columns = Arrays.asList("iso", "iso3", "iso_numeric", "fips", "country", "capital", "area", "population", "continent", "tld", "currency_Code", "currency_name", "phone", "postal_code_format", "postal_code_regex", "languages", "geonameid");
        final String geoNamesCountryInfoUrl = "http://download.geonames.org/export/dump/countryInfo.txt";
        final Map.Entry<Date, File> result = ResourceUtil.fetchResource("country", geoNamesCountryInfoUrl);
        try (final CloseableIterator<Map<String, String>> iter = new CsvFileIterator<>(result.getValue().toPath(), columns, true, 0, i -> i))
        {
            while (iter.hasNext())
            {
                final Map<String, String> next = iter.next();
                final String name = next.get("country").toLowerCase();
                final String continent = next.get("continent");
                final int id = Integer.parseInt(next.get("geonameid"));
                importGeoJson(id, continent, name);
            }
        }
    }

    private void importGeoJson(final int id, final String continentCode, final String countryName)
    {
        try
        {
            final ObjectNode jsonNode = fetchGeoJsonFromServer(continentCode, countryName);
            final Geometry geometry = new GeoJsonReader().read(jsonNode.toPrettyString());
            throw new UnsupportedOperationException();
            //new FileBoundaryDao(dir).save(id, geometry);
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
