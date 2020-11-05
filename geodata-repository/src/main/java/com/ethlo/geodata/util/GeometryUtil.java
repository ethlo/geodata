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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;

import org.geotools.geometry.jts.GeometryClipper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.springframework.util.Assert;

import com.ethlo.geodata.model.View;
import com.goebl.simplify.Simplify;

public class GeometryUtil
{
    private final static GeometryFactory geometryFactory = new GeometryFactory();
    public static final Geometry EMPTY_GEOMETRY = geometryFactory.createGeometryCollection(null);
    private final static int TILE_RESOLUTION = 256;

    private static double latRad(double lat)
    {
        final double sin = Math.sin(lat * Math.PI / 180);
        final double radX2 = Math.log((1 + sin) / (1 - sin)) / 2;
        return Math.max(Math.min(radX2, Math.PI), -Math.PI) / 2;
    }

    private static double zoom(int mapPx, double fraction)
    {
        return Math.floor(Math.log(mapPx / (double) GeometryUtil.TILE_RESOLUTION / fraction) / Math.log(2));
    }

    public static int getBoundsZoomLevel(View view)
    {
        final double latFraction = (latRad(view.getMaxLat()) - latRad(view.getMinLat())) / Math.PI;
        final double lngDiff = view.getMaxLng() - view.getMinLng();
        final double lngFraction = ((lngDiff < 0) ? (lngDiff + 360) : lngDiff) / 360;
        final double latZoom = zoom(view.getHeight(), latFraction);
        final double lngZoom = zoom(view.getWidth(), lngFraction);
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
        if (full instanceof GeometryCollection)
        {
            final GeometryCollection p = (GeometryCollection) full;
            final List<Geometry> res = new LinkedList<>();
            for (int i = 0; i < p.getNumGeometries(); i++)
            {
                final Geometry simpleP = simplifyPolygon((Polygon) p.getGeometryN(i), tolerance);
                if (simpleP != EMPTY_GEOMETRY)
                {
                    res.add(simpleP);
                }
            }
            return geometryFactory.buildGeometry(res);
        }
        else
        {
            return simplifyPolygon((Polygon) full, tolerance);
        }
    }

    private static Geometry simplifyPolygon(final Polygon polygon, double tolerance)
    {
        Assert.notNull(polygon, "polygon cannot be null");
        final Coordinate[] shell = polygon.getExteriorRing().getCoordinates();
        if (shell.length == 0)
        {
            return polygon;
        }

        tolerance = JtsPointExtractor.MULTIPLIER * tolerance;
        final Simplify<Coordinate> simplify = new Simplify<>(new Coordinate[0], new JtsPointExtractor());
        final Coordinate[] result = simplify.simplify(polygon.getExteriorRing().getCoordinates(), tolerance, false);

        if (result.length < 4)
        {
            return EMPTY_GEOMETRY;
        }

        if (!result[0].equals2D(result[result.length - 1]))
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
        final Geometry clipped = clipper.clipSafe(geometry, true, 0);
        if (clipped == null)
        {
            return null;
        }

        if (clipped.isEmpty() || clipped.getNumPoints() < 2)
        {
            return null;
        }

        if (clipped instanceof Polygon)
        {
            return cleanupPolygon(clipped);
        }
        else if (clipped instanceof GeometryCollection)
        {
            final List<Polygon> polygons = new LinkedList<>();
            for (int i = 0; i < clipped.getNumGeometries(); i++)
            {
                polygons.add(cleanupPolygon(clipped.getGeometryN(i)));
            }
            return geometryFactory.createGeometryCollection(polygons.toArray(new Polygon[0]));
        }
        throw new UnsupportedOperationException("Unknown geometry: " + clipped.getGeometryType());
    }

    private static Polygon cleanupPolygon(final Geometry geometry)
    {
        final Coordinate[] coordinates = getCoordinates(geometry.getCoordinates());

        final List<LinearRing> holes = new LinkedList<>();
        if (geometry instanceof Polygon)
        {
            final Polygon p = (Polygon) geometry;
            for (int i = 0; i < p.getNumInteriorRing(); i++)
            {
                holes.add(geometryFactory.createLinearRing(getCoordinates(p.getInteriorRingN(i).getCoordinates())));
            }
        }

        return geometryFactory.createPolygon(geometryFactory.createLinearRing(coordinates), holes.toArray(new LinearRing[0]));
    }

    private static Coordinate[] getCoordinates(final Coordinate[] inputCoordinates)
    {
        if (inputCoordinates.length < 4)
        {
            return new Coordinate[0];
        }

        Coordinate[] coordinates = new Coordinate[inputCoordinates.length];
        for (int i = 0; i < inputCoordinates.length; i++)
        {
            coordinates[i] = new CoordinateXY(inputCoordinates[i].x, inputCoordinates[i].y);
        }

        // Ensure linear ring (same coordinate for first/last)
        if (!coordinates[0].equals2D(coordinates[coordinates.length - 1]))
        {
            final Coordinate[] tmp = Arrays.copyOf(coordinates, coordinates.length + 1);
            tmp[coordinates.length] = coordinates[0];
            coordinates = tmp;
        }
        return coordinates;
    }

    public static Collection<Geometry> split(final int id, Geometry g, int maxSize, int maxPieces)
    {
        if (maxSize < 10)
        {
            throw new IllegalArgumentException("maxSize should be greater than or equal to 10");
        }
        if (maxPieces <= 1)
        {
            throw new IllegalArgumentException("maxPieces should be greater than 1");
        }
        final List<Geometry> answer = new ArrayList<>();
        final Queue<Geometry> queue = new LinkedList<Geometry>();
        queue.add(Objects.requireNonNull(g));
        while (!queue.isEmpty())
        {
            final Geometry geom = queue.remove();
            if (geom != null)
            {
                if (size(geom) > maxSize)
                {
                    queue.addAll(subdivide(geom));
                }
                else
                {
                    answer.add(geom);
                }
            }

            if (queue.size() + answer.size() > maxPieces)
            {
                throw new IllegalArgumentException("Exceeded maximum number of allowed subdivisions for " + id + ". Giving up. Consider \n" +
                        "increasing the maxSize and re-running");
            }
        }
        return answer;
    }

    private static int size(final Geometry geom)
    {
        return geom.getNumPoints();
    }

    private static List<Geometry> subdivide(final Geometry geom)
    {
        final Envelope bb = geom.getEnvelopeInternal();
        final Coordinate centre = bb.centre();
        return Arrays.stream(new Envelope[]{
                new Envelope(bb.getMinX(), centre.x, bb.getMinY(), centre.y),
                new Envelope(centre.x, bb.getMaxX(), bb.getMinY(), centre.y),
                new Envelope(bb.getMinX(), centre.x, centre.y, bb.getMaxY()),
                new Envelope(centre.x, bb.getMaxX(), centre.y, bb.getMaxY())})
                .map(box -> clip(box, geom))
                .collect(Collectors.toList());
    }
}
