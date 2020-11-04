package com.ethlo.geodata.io;

/*-
 * #%L
 * geodata-repository
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.util.Assert;

public class BinaryBoundaryEncoder
{
    public static final int VERSION = 0x1;
    private static final GeometryFactory factory = new GeometryFactory();

    public void write(final double totalArea, Geometry geometry, DataOutputStream out) throws IOException
    {
        writeVersion(out);
        writeBoundingBox(geometry, out);
        out.writeDouble(totalArea);
        writeData(geometry, out);
    }

    private void writeData(final Geometry geometry, final DataOutputStream out) throws IOException
    {
        if (geometry instanceof GeometryCollection)
        {
            final GeometryCollection collection = (GeometryCollection) geometry;
            for (int i = 0; i < collection.getNumGeometries(); i++)
            {
                writeData(collection.getGeometryN(i), out);
            }
        }
        else if (geometry instanceof Polygon)
        {
            final Polygon poly = (Polygon) geometry;

            // Always output the shell first, as that is what the holes are in (if any)
            handleShell(poly, out);
            handleHole(poly, out);
        }
        else if (geometry instanceof LineString)
        {
            writeCoordinates(false, geometry.getCoordinates(), out);
        }
        else
        {
            throw new IllegalArgumentException("Unhandled geometry type: " + geometry.getGeometryType());
        }
    }

    private void handleHole(final Polygon poly, DataOutputStream out) throws IOException
    {
        for (int i = 0; i < poly.getNumInteriorRing(); i++)
        {
            writeCoordinates(true, poly.getInteriorRingN(i).getCoordinates(), out);
        }
    }

    private void handleShell(final Polygon poly, DataOutputStream out) throws IOException
    {
        writeCoordinates(false, poly.getExteriorRing().getCoordinates(), out);
    }

    private void writeCoordinates(final boolean hole, final Coordinate[] coordinates, final DataOutputStream out) throws IOException
    {
        out.writeByte(hole ? 0 : 1);
        out.writeInt(coordinates.length);
        for (Coordinate coord : coordinates)
        {
            out.writeFloat((float) coord.y);
            out.writeFloat((float) coord.x);
        }
    }

    private void writeBoundingBox(Geometry polygon, final DataOutputStream out) throws IOException
    {
        final Envelope bb = polygon.getEnvelopeInternal();
        final float minLat = (float) bb.getMinY();
        final float maxLat = (float) bb.getMaxY();
        final float minLng = (float) bb.getMinX();
        final float maxLng = (float) bb.getMaxX();
        out.writeFloat(minLat);
        out.writeFloat(maxLat);
        out.writeFloat(minLng);
        out.writeFloat(maxLng);
    }

    private void writeVersion(final DataOutputStream out) throws IOException
    {
        out.writeByte(VERSION);
    }

    public Geometry read(InputStream source) throws IOException
    {
        try (final DataInputStream in = new DataInputStream(source))
        {
            readVersion(in);
            return readGeometry(in);
        }
    }

    private void readVersion(final DataInputStream in) throws IOException
    {
        final byte version = in.readByte();
        Assert.isTrue(version == VERSION);
    }

    private Geometry readGeometry(final DataInputStream in) throws IOException
    {
        final Envelope bb = readBoundingBox(in);

        final double area = readArea(in);

        final List<Polygon> coll = new ArrayList<>();

        Map.Entry<Boolean, Coordinate[]> coordinates;
        do
        {
            coordinates = attemptReadingCoordinates(in);
            if (coordinates != null && coordinates.getValue().length > 0)
            {
                final boolean isHole = coordinates.getKey();
                if (!isHole)
                {
                    coll.add(createPolygon((coordinates.getValue())));
                }
                else
                {
                    // This entry is a hole in the previous shell
                    Assert.isTrue(!coll.isEmpty(), "Cannot have a hole without a previous shell");
                    final Polygon previous = coll.get(coll.size() - 1);

                    // Copy existing holes
                    final LinearRing[] holes = new LinearRing[previous.getNumInteriorRing() + 1];
                    for (int i = 0; i < holes.length - 1; i++)
                    {
                        holes[i] = previous.getInteriorRingN(i);
                    }

                    // Add new hole
                    holes[holes.length - 1] = factory.createLinearRing(coordinates.getValue());

                    // Return polygon with new hole
                    factory.createPolygon(previous.getExteriorRing(), holes);
                }
            }
        } while (coordinates != null);

        if (coll.size() > 1)
        {
            return new GeometryCollection(coll.toArray(new Geometry[0]), factory);
        }
        return coll.get(0);
    }

    private Polygon createPolygon(final Coordinate[] value)
    {
        LinearRing ring = null;
        if (isClosed(value))
        {
            ring = factory.createLinearRing(value);
        }
        else
        {
            final Coordinate[] array = new Coordinate[value.length + 1];
            for (int i = 0; i < array.length - 1; i++)
            {
                array[i] = value[i];
                array[array.length - 1] = value[0];
                ring = factory.createLinearRing(array);
            }
        }
        return factory.createPolygon(ring, null);
    }

    private boolean isClosed(final Coordinate[] value)
    {
        return value[0].equals2D(value[value.length - 1]);
    }

    private Map.Entry<Boolean, Coordinate[]> attemptReadingCoordinates(final DataInputStream in) throws IOException
    {
        boolean isHole;
        try
        {
            isHole = in.readByte() == 0;
        }
        catch (EOFException ignored)
        {
            return null;
        }

        final int coordinateCount = in.readInt();
        final Coordinate[] coordinates = new Coordinate[coordinateCount];
        for (int i = 0; i < coordinateCount; i++)
        {
            final float lat = in.readFloat();
            final float lng = in.readFloat();
            coordinates[i] = new CoordinateXY(lng, lat);
        }

        return new AbstractMap.SimpleEntry<>(isHole, coordinates);
    }

    private Envelope readBoundingBox(final DataInputStream in) throws IOException
    {
        final float minLat = in.readFloat();
        final float maxLat = in.readFloat();
        final float minLng = in.readFloat();
        final float maxLng = in.readFloat();
        return new Envelope(minLng, maxLng, minLat, maxLat);
    }

    public Envelope readEnvelope(final InputStream in) throws IOException
    {
        final DataInputStream din = new DataInputStream(in);
        readVersion(din);
        return readBoundingBox(din);
    }

    public Map.Entry<Double, Envelope> readEnvelopeAndTotalArea(final InputStream in) throws IOException
    {
        final DataInputStream din = new DataInputStream(in);
        readVersion(din);
        final Envelope bb = readBoundingBox(din);
        final double area = readArea(din);
        return new AbstractMap.SimpleEntry<>(area, bb);
    }

    private double readArea(final DataInputStream din) throws IOException
    {
        return din.readDouble();
    }
}
