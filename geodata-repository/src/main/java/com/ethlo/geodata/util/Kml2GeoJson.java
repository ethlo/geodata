package com.ethlo.geodata.util;

/*-
 * #%L
 * Geodata service
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

import java.io.Reader;
import java.util.Scanner;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Simple converter for KML to GeoJSON
 *
 * https://developers.google.com/kml/documentation/kmlreference
 * https://geojson.org/geojson-spec.html
 */
public class Kml2GeoJson
{
    public static final String PLACE_MARK = "Placemark";
    public static final String POINT = "Point";
    public static final String LINE_STRING = "LineString";
    public static final String LINEAR_RING = "LinearRing";
    public static final String POLYGON = "Polygon";
    public static final String MULTI_GEOMETRY = "MultiGeometry";
    public static final String COORDINATES = "coordinates";
    public static final String GEOMETRY_COLLECTION = "GeometryCollection";
    public static final String TYPE = "type";
    public static final String OUTER_BOUNDARY_IS = "outerBoundaryIs";
    public static final String INNER_BOUNDARY_IS = "innerBoundaryIs";
    public static final String GEOMETRIES = "geometries";
    public static final String COMMA = ",";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final XMLInputFactory xif = XMLInputFactory.newFactory();

    private Kml2GeoJson()
    {
    }

    public static ObjectNode parse(final Reader kml) throws XMLStreamException
    {
        XMLEventReader events = xif.createXMLEventReader(kml);
        while (events.hasNext())
        {
            XMLEvent event = events.nextEvent();
            if (isStartElement(event, PLACE_MARK))
            {
                return readGeometry(events);
            }
        }
        throw new IllegalArgumentException("Found no " + PLACE_MARK + " element in input");
    }

    private static ObjectNode readGeometry(XMLEventReader events) throws XMLStreamException
    {
        while (events.hasNext())
        {
            final XMLEvent event = events.nextEvent();

            if (isStartElement(event, POINT))
            {
                return readPoint(events);
            }

            if (isStartElement(event, LINE_STRING))
            {
                return readLineString(events);
            }
            if (isStartElement(event, LINEAR_RING))
            {
                return readLineString(events);
            }

            if (isStartElement(event, POLYGON))
            {
                return readPolygon(events);
            }

            if (isStartElement(event, MULTI_GEOMETRY))
            {
                return readMultiGeometry(events);
            }
        }
        return null;
    }

    private static ObjectNode readPoint(XMLEventReader events) throws XMLStreamException
    {
        ObjectNode point = mapper.createObjectNode();
        point.put(TYPE, POINT);
        final StringBuilder coordinates = readCoordinates(events);
        if (coordinates != null)
        {
            point.set(COORDINATES, readCoordinate(coordinates.toString()));
        }
        return point;
    }

    private static ObjectNode readLineString(XMLEventReader events) throws XMLStreamException
    {
        final ObjectNode line = mapper.createObjectNode();
        line.put(TYPE, LINE_STRING);
        final StringBuilder coordinates = readCoordinates(events);
        if (coordinates != null)
        {
            line.set(COORDINATES, readCoordinates(coordinates.toString()));
        }
        return line;
    }

    private static StringBuilder readCoordinates(final XMLEventReader events) throws XMLStreamException
    {
        StringBuilder coordinates = null;
        while (events.hasNext())
        {
            XMLEvent event = events.nextEvent();
            if (isStartElement(event, COORDINATES))
            {
                coordinates = new StringBuilder();
            }

            if (isEndElement(event))
            {
                break;
            }

            if (event.isCharacters() && coordinates != null)
            {
                final Characters chars = event.asCharacters();
                coordinates.append(chars.getData());
            }
        }
        return coordinates;
    }

    private static ObjectNode readPolygon(XMLEventReader events) throws XMLStreamException
    {
        final ObjectNode polygon = mapper.createObjectNode();
        polygon.put(TYPE, POLYGON);
        ObjectNode outerBoundary = null;
        ObjectNode innerBoundary = null;
        int boundaryType = 0; // 0 = undefined; 1 = outer; 2 = inner
        while (events.hasNext())
        {
            XMLEvent event = events.nextEvent();
            if (isStartElement(event, OUTER_BOUNDARY_IS))
            {
                boundaryType = 1;
                continue;
            }
            if (isStartElement(event, INNER_BOUNDARY_IS))
            {
                boundaryType = 2;
                continue;
            }
            if (isStartElement(event, LINEAR_RING))
            {
                if (boundaryType == 1)
                {
                    outerBoundary = readLineString(events);
                }
                else if (boundaryType == 2)
                {
                    innerBoundary = readLineString(events);
                }
                boundaryType = 0;
            }
        }
        ArrayNode coordinates = mapper.createArrayNode();
        polygon.set(COORDINATES, coordinates);
        if (outerBoundary != null)
        {
            coordinates.add(outerBoundary.get(COORDINATES));
            if (innerBoundary != null)
            {
                coordinates.add(innerBoundary.get(COORDINATES));
            }
        }
        return polygon;
    }

    private static ObjectNode readMultiGeometry(XMLEventReader events) throws XMLStreamException
    {
        final ObjectNode obj = mapper.createObjectNode();
        obj.put(TYPE, GEOMETRY_COLLECTION);
        final ArrayNode geometries = mapper.createArrayNode();
        obj.set(GEOMETRIES, geometries);
        ObjectNode geo;
        while ((geo = readGeometry(events)) != null)
        {
            geometries.add(geo);
        }
        return obj;
    }

    private static boolean isStartElement(XMLEvent event, String name)
    {
        if (!event.isStartElement())
        {
            return false;
        }
        StartElement elem = event.asStartElement();
        String n = elem.getName().getLocalPart();
        return name.equals(n);
    }

    private static boolean isEndElement(XMLEvent event)
    {
        if (!event.isEndElement())
        {
            return false;
        }
        final EndElement elem = event.asEndElement();
        final String n = elem.getName().getLocalPart();
        return Kml2GeoJson.COORDINATES.equals(n);
    }

    private static ArrayNode readCoordinates(String s)
    {
        final ArrayNode array = mapper.createArrayNode();
        try (Scanner scanner = new Scanner(s))
        {
            while (scanner.hasNext())
            {
                final ArrayNode coordinate = readCoordinate(scanner.next());
                array.add(coordinate);
            }
        }
        return array;
    }

    private static ArrayNode readCoordinate(String s)
    {
        final String[] parts = s.split(COMMA);
        final ArrayNode array = mapper.createArrayNode();
        for (final String part : parts)
        {
            final double num = Double.parseDouble(part);
            array.add(num);
        }
        return array;
    }
}
 
