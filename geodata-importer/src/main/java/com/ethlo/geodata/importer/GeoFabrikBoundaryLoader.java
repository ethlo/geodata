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
import java.nio.file.Files;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethlo.geodata.util.ResourceUtil;

public class GeoFabrikBoundaryLoader
{
    private static final Logger logger = LoggerFactory.getLogger(GeoFabrikBoundaryLoader.class);
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

    public String getKml(String continentCode, String countryName) throws IOException
    {
        final String continentName = Objects.requireNonNull(continents.get(continentCode.toUpperCase()), "No continent with code " + continentCode);
        final String url = BASE_URL + continentName + "/" + countryName + ".kml";
        final Map.Entry<Date, File> file = ResourceUtil.fetchResource((continentName + "_" + countryName + ".kml").toLowerCase(), url);
        return Files.readString(file.getValue().toPath());
    }
}
