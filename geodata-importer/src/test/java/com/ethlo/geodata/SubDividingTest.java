package com.ethlo.geodata;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;

import javax.xml.stream.XMLStreamException;

import org.junit.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.springframework.core.io.ClassPathResource;

import com.ethlo.geodata.util.GeometryUtil;
import com.ethlo.geodata.util.Kml2GeoJson;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SubDividingTest
{
    @Test
    public void testSplit() throws IOException, XMLStreamException, ParseException
    {
        final ObjectNode node = Kml2GeoJson.parse(new InputStreamReader(new ClassPathResource("sample_boundary.kml").getInputStream()));
        //System.out.println(node.toPrettyString());

        final Geometry geometry = new GeoJsonReader().read(node.toString());
        final GeoJsonWriter w = new GeoJsonWriter();
        final Collection<Geometry> split = GeometryUtil.split(123, geometry, 200, 1000);
        for (Geometry sub : split)
        {
            System.out.println(w.write(sub));
        }
    }
}
