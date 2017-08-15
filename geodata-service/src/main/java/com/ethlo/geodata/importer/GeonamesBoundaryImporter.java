package com.ethlo.geodata.importer;

/*-
 * #%L
 * geodata
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.geojson.GeoJsonReader;

public class GeonamesBoundaryImporter implements DataImporter
{
    private final Logger logger = LoggerFactory.getLogger(GeonamesBoundaryImporter.class);
    
    private final File boundaryFile;
    
    private final GeoJsonReader r = new GeoJsonReader();
    
    public GeonamesBoundaryImporter(File boundaryFile)
    {
        this.boundaryFile = boundaryFile;
    }
    
    @Override
    public void processFile(Consumer<Map<String, String>> sink) throws IOException
    {
        int count = 0;
        try (final BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(boundaryFile), StandardCharsets.UTF_8)))
        {
            String line = r.readLine(); // skip first line
            
            while((line = r.readLine()) != null)
            {
                if (StringUtils.isNotBlank(line))
                {
                    final String[] fields = line.split("\t");
                    if (fields.length == 2)
                    {
                        try
                        {
                            final Map<String, String> entry = parsePoints(fields);
                            sink.accept(entry);
                            count++;
                        }
                        catch (ParseException exc)
                        {
                            logger.warn("Cannot parse geometry for location {}: {}", fields[0], exc.getMessage());
                        }
                    }
                    else
                    {
                    	logger.warn("Unexpected field count for {}", StringUtils.abbreviate(line, 100));
                    }
                }
                
                if (count % 1_000 == 0)
                {
                    System.out.println(count);
                }
            }
        }
        
    }

    private Map<String, String> parsePoints(final String[] fields) throws IOException, ParseException
    {
        final long id = Long.parseLong(fields[0]);
        
        Geometry geometry = r.read(fields[1]);
        final Map<String, String> params = new TreeMap<>();
        params.put("id", Long.toString(id));
        params.put("poly", geometry.toText());
        return params;
    }
}

