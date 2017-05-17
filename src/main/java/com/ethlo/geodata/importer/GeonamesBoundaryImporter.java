package com.ethlo.geodata.importer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.geo.Point;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GeonamesBoundaryImporter implements DataImporter
{
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final File boundaryFile;
    
    public GeonamesBoundaryImporter(File boundaryFile)
    {
        this.boundaryFile = boundaryFile;
    }
    
    @Override
    public void processFile(Consumer<Map<String, String>> sink) throws IOException
    {
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
                        final Map<String, String> entry = parsePoints(fields);
                        sink.accept(entry);
                    }
                }
            }
        }
        
    }

    private Map<String, String> parsePoints(final String[] fields) throws JsonProcessingException, IOException
    {
        final long id = Long.parseLong(fields[0]);
        final List<List<Point>> polyList = polyToPoints(fields[1]);
        final List<String> polyStrings = new LinkedList<>();
        for (List<Point> pol : polyList)
        {
            polyStrings.add(toString(pol));
        }
        
        final Map<String, String> params = new TreeMap<>();
        params.put("id", Long.toString(id));
        params.put("poly", "MULTIPOLYGON((" + org.springframework.util.StringUtils.collectionToCommaDelimitedString(polyStrings) + "))");
        return params;
    }

    private String toString(List<Point> poly)
    {
        final StringBuilder b = new StringBuilder();
        poly.stream().forEach((p)->{b.append(p.getX() + " " + p.getY() + ",");});
        return "(" + StringUtils.stripEnd(b.toString(), ",") + ")";
    }

    private List<List<Point>> polyToPoints(final String s) throws JsonProcessingException, IOException
    {
        final JsonNode node = objectMapper.readTree(s);
        final String type = node.get("type").asText();
        final JsonNode coords = node.get("coordinates");
        final List<List<Point>> retVal = new LinkedList<>();
        switch (type)
        {
            case "Polygon":
                for (JsonNode poly : coords)
                {
                    retVal.add(extract(poly));
                }
                return retVal;
                
            case "MultiPolygon":
                for (JsonNode poly : coords)
                {
                    for (JsonNode sub : poly)
                    {
                        retVal.add(extract(sub));
                    }
                }
                return retVal;
                
            default:
                throw new IllegalArgumentException("Unknown type " + type);
        }
    }

    private List<Point> extract(JsonNode polyList)
    {
        final List<Point> points = new LinkedList<>();
        for (JsonNode c : polyList)
        {
            final double lon = c.get(0).asDouble();
            final double lat = c.get(1).asDouble();
            final Point p = new Point(lon, lat);
            points.add(p);
        }
        // Repeat first
        points.add(points.get(0));
        return points;
    }
}

