package com.ethlo.geodata.util;

/*-
 * #%L
 * Geodata service
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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.geotools.geometry.jts.GeometryClipper;
import org.springframework.util.Assert;

import com.ethlo.geodata.model.View;
import com.goebl.simplify.Simplify;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

public class GeometryUtil
{
    private final static GeometryFactory geometryFactory = new GeometryFactory();

    private final static int TILE_RESOLUTION = 256;

	public static final Geometry EMPTY_GEOMETRY = geometryFactory.createGeometryCollection(null); 
    
	private static double latRad(double lat)
	{
        final double sin = Math.sin(lat * Math.PI / 180);
        final double radX2 = Math.log((1 + sin) / (1 - sin)) / 2;
        return Math.max(Math.min(radX2, Math.PI), -Math.PI) / 2;
    }

    private static double zoom(int mapPx, int worldPx, double fraction)
    {
        return Math.floor(Math.log(mapPx / worldPx / fraction) / Math.log(2));
    }
	
	public static int getBoundsZoomLevel(View view)
	{
	    final double latFraction = (latRad(view.getMaxLat()) - latRad(view.getMinLat())) / Math.PI;
	    final double lngDiff = view.getMaxLng() - view.getMinLng();
	    final double lngFraction = ((lngDiff < 0) ? (lngDiff + 360) : lngDiff) / 360;
	    final double latZoom = zoom(view.getHeight(), TILE_RESOLUTION, latFraction);
	    final double lngZoom = zoom(view.getWidth(), TILE_RESOLUTION, lngFraction);
	    return (int) Math.min(latZoom, lngZoom);
	}

	public static Geometry simplify(Geometry full, View view, int qualityConstant)
	{
        final double lat = full.getCentroid().getCoordinate().y;
        final int zoomLevel = getBoundsZoomLevel(view);
        final double meterPerPixel = 156543.03392 * Math.cos(lat * Math.PI / 180) / Math.pow(2, zoomLevel);
        final double tolerance = (meterPerPixel / qualityConstant);
        return simplify(full, tolerance);
	}
	
    public static Geometry simplify(Geometry full, double tolerance)
    {
    	if (full instanceof MultiPolygon)
        {
    		final MultiPolygon p = (MultiPolygon) full;
    		final List<Geometry> res = new LinkedList<>();
    		for (int i = 0; i < p.getNumGeometries(); i++)
    		{
    			final Geometry simpleP = simplifyPolygon((Polygon)p.getGeometryN(i), tolerance);
    			if (simpleP != EMPTY_GEOMETRY)
    			{
    				res.add(simpleP);
    			}
    		}
    		return geometryFactory.buildGeometry(res);
        }
        else
        {
        	return simplifyPolygon((Polygon)full, tolerance);
        }
	}
    
	private static Geometry simplifyPolygon(Polygon polygon, double tolerance)
    {
		Assert.notNull(polygon, "polygon cannot be null");
		
		tolerance = JtsPointExtractor.MULIPLICATOR * tolerance;
        final Simplify<Coordinate> simplify = new Simplify<Coordinate>(new Coordinate[0], new JtsPointExtractor());
        final Coordinate[] result = simplify.simplify(polygon.getExteriorRing().getCoordinates(), tolerance, false);
        if (result.length < 4)
        {
        	return EMPTY_GEOMETRY;
        }
        
        if (! result[0].equals2D(result[result.length - 1]))
        {
        	final Coordinate[] tmp = Arrays.copyOf(result, result.length + 1);
        	tmp[result.length] = result[0];
        	return geometryFactory.createPolygon(tmp);
        }
        else
        {
        	return geometryFactory.createPolygon(result);
        }
	}
	
	public static Geometry clip(Envelope envelope, Geometry geometry)
	{
		final GeometryClipper clipper = new GeometryClipper(new Envelope(envelope.getMinX(), envelope.getMaxX(), envelope.getMinY(), envelope.getMaxY()));
		return clipper.clipSafe(geometry, true, 0);
	}
}
