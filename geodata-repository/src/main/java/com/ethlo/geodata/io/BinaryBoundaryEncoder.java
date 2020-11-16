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

import static org.springframework.util.Assert.isTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
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

import com.ethlo.geodata.model.BoundaryData;
import com.ethlo.geodata.model.BoundaryMetadata;
import com.ethlo.geodata.model.Coordinates;

public class BinaryBoundaryEncoder
{
    public static final int SIZE_OF_COORDINATE = 6;
    private static final GeometryFactory factory = new GeometryFactory();

    public static BoundaryData readGeometry(final DataInputStream in)
    {
        try
        {
            return doRead(in);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static BoundaryData doRead(final DataInputStream in) throws IOException
    {
        final int id = in.readInt();
        final int subDivideIndex = in.readInt();
        final Envelope bb = readBoundingBox(in);
        final double area = readArea(in);

        final int numGeometries = in.readInt();

        final List<Polygon> coll = new ArrayList<>();

        for (int numGeometry = 0; numGeometry < numGeometries; numGeometry++)
        {
            final Map.Entry<Boolean, Coordinate[]> coordinates = readCoordinates(in);
            if (coordinates.getValue().length == 0)
            {
                continue;
            }

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

        if (coll.size() > 1)
        {
            return new BoundaryData(id, subDivideIndex, bb, area, new GeometryCollection(coll.toArray(new Geometry[0]), factory));
        }
        return new BoundaryData(id, subDivideIndex, bb, area, coll.get(0));
    }

    private static Polygon createPolygon(final Coordinate[] value)
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

    private static boolean isClosed(final Coordinate[] value)
    {
        return value[0].equals2D(value[value.length - 1]);
    }

    private static Map.Entry<Boolean, Coordinate[]> readCoordinates(final DataInputStream in) throws IOException
    {
        boolean isHole = in.readByte() == 0;
        final int coordinateCount = in.readInt();
        final Coordinate[] coordinates = new Coordinate[coordinateCount];
        final byte[] data = new byte[6];
        for (int i = 0; i < coordinateCount; i++)
        {
            Assert.isTrue(in.read(data) == 6);
            final Coordinates coord = LatLngEncoder.decode(data);
            coordinates[i] = new CoordinateXY(coord.getLng(), coord.getLat());
        }
        return new AbstractMap.SimpleEntry<>(isHole, coordinates);
    }

    private static Envelope readBoundingBox(final DataInputStream in) throws IOException
    {
        final float minLat = in.readFloat();
        final float maxLat = in.readFloat();
        final float minLng = in.readFloat();
        final float maxLng = in.readFloat();
        return new Envelope(minLng, maxLng, minLat, maxLat);
    }

    public static BoundaryMetadata readBoundaryMetadata(final DataInputStream in) throws IOException
    {
        final int id = in.readInt();
        isTrue(id > 0, "id must be a positive integer");
        final int subDivideIndex = in.readInt();
        isTrue(subDivideIndex >= 0, "subDivideIndex must be a non-negative integer");
        final Envelope bb = readBoundingBox(in);
        final double area = readArea(in);
        skipGeometry(in);
        return new BoundaryMetadata(id, subDivideIndex, bb, area);
    }

    private static void skipGeometry(final DataInputStream in) throws IOException
    {
        final int numGeometries = in.readInt();
        for (int i = 0; i < numGeometries; i++)
        {
            skipCoordinates(in);
        }
    }

    private static void skipCoordinates(final DataInputStream in) throws IOException
    {
        final byte isHole = in.readByte();
        isTrue(isHole == 0 || isHole == 1, "isHole should be 0 or 1");
        final int pointCount = in.readInt();
        final int skipBytes = SIZE_OF_COORDINATE * pointCount;
        in.skipBytes(skipBytes);
    }

    private static double readArea(final DataInputStream din) throws IOException
    {
        return din.readDouble();
    }

    public static void write(BoundaryData data, DataOutputStream out) throws IOException
    {
        out.writeInt(data.getId());
        out.writeInt(data.getSubDivideIndex());
        writeBoundingBox(data.getMbr(), out);
        out.writeDouble(data.getArea());
        writeData(data.getGeometry(), out);
    }

    private static void writeData(final Geometry geometry, final DataOutputStream out) throws IOException
    {
        final int count = countCoordinateRings(geometry);
        out.writeInt(count);
        final int written = doWriteData(geometry, out);
        isTrue(count == written, "Expected " + count + " but wrote " + written);
    }

    private static int countCoordinateRings(final Geometry geometry)
    {
        int count = 0;
        if (geometry instanceof GeometryCollection)
        {
            final GeometryCollection collection = (GeometryCollection) geometry;
            for (int i = 0; i < collection.getNumGeometries(); i++)
            {
                final Geometry g = collection.getGeometryN(i);
                count += countCoordinateRings(g);
            }
        }
        else if (geometry instanceof Polygon)
        {
            final Polygon poly = (Polygon) geometry;
            count++; // Shell
            count += poly.getNumInteriorRing(); // Potential holes
        }
        else if (geometry instanceof LineString)
        {
            count++;
        }
        else
        {
            throw new IllegalArgumentException("Unhandled geometry type: " + geometry.getGeometryType());
        }
        return count;
    }

    private static int doWriteData(final Geometry geometry, final DataOutputStream out) throws IOException
    {
        int written = 0;
        if (geometry instanceof GeometryCollection)
        {
            final GeometryCollection collection = (GeometryCollection) geometry;
            for (int i = 0; i < collection.getNumGeometries(); i++)
            {
                final Geometry g = collection.getGeometryN(i);
                written += doWriteData(g, out);
            }
        }
        else if (geometry instanceof Polygon)
        {
            final Polygon poly = (Polygon) geometry;

            // Always output the shell first, as that is what the holes are in (if any)
            handleShell(poly, out);
            written++;
            written += handleHole(poly, out);
        }
        else if (geometry instanceof LineString)
        {
            writeCoordinates(false, geometry.getCoordinates(), out);
            written++;
        }
        else
        {
            throw new IllegalArgumentException("Unhandled geometry type: " + geometry.getGeometryType());
        }
        return written;
    }

    private static int handleHole(final Polygon poly, DataOutputStream out) throws IOException
    {
        for (int i = 0; i < poly.getNumInteriorRing(); i++)
        {
            writeCoordinates(true, poly.getInteriorRingN(i).getCoordinates(), out);
        }
        return poly.getNumInteriorRing();
    }

    private static void handleShell(final Polygon poly, DataOutputStream out) throws IOException
    {
        writeCoordinates(false, poly.getExteriorRing().getCoordinates(), out);
    }

    private static void writeCoordinates(final boolean hole, final Coordinate[] coordinates, final DataOutputStream out) throws IOException
    {
        out.writeByte(hole ? 0 : 1);
        out.writeInt(coordinates.length);
        for (Coordinate coord : coordinates)
        {
            out.write(LatLngEncoder.encode(Coordinates.of(coord)));
        }
    }

    private static void writeBoundingBox(Envelope bb, final DataOutputStream out) throws IOException
    {
        final float minLat = (float) bb.getMinY();
        final float maxLat = (float) bb.getMaxY();
        final float minLng = (float) bb.getMinX();
        final float maxLng = (float) bb.getMaxX();
        out.writeFloat(minLat);
        out.writeFloat(maxLat);
        out.writeFloat(minLng);
        out.writeFloat(maxLng);
    }
}
