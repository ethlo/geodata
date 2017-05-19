package com.ethlo.geodata.importer;

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
                        System.err.println("NOT LENGTH 2: " + line);
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

