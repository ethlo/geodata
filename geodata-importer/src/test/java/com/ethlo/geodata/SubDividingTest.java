package com.ethlo.geodata;

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
        final Collection<Geometry> split = GeometryUtil.split(123, geometry, 20, 1000);
        for (Geometry sub : split)
        {
            System.out.println(w.write(sub));
        }
    }
}
