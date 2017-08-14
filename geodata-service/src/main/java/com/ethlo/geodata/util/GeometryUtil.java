package com.ethlo.geodata.util;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.ethlo.geodata.model.View;
import com.goebl.simplify.Simplify;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

public class GeometryUtil
{
    private final static GeometryFactory geometryFactory = new GeometryFactory();

    private final static int TILE_RESOLUTION = 256; 
    
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

	public static Geometry simplify(Geometry full, View view)
	{
        final double lat = full.getCentroid().getCoordinate().y;
        final int zoomLevel = getBoundsZoomLevel(view);
        final double meterPerPixel = 156543.03392 * Math.cos(lat * Math.PI / 180) / Math.pow(2, zoomLevel);
        final double tolerance = (meterPerPixel / 100_000);
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
    			if (simpleP != null)
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
		tolerance = JtsPointExtractor.MULIPLICATOR * tolerance;
        final Simplify<Coordinate> simplify = new Simplify<Coordinate>(new Coordinate[0], new JtsPointExtractor());
        final Coordinate[] result = simplify.simplify(polygon.getExteriorRing().getCoordinates(), tolerance, false);
        if (result.length < 4)
        {
        	return null;
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
}
