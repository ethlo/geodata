package com.ethlo.geodata.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import com.ethlo.geodata.model.BoundaryData;
import com.ethlo.geodata.model.BoundaryMetadata;

public class BinaryBoundaryEncoderTest
{
    @Test
    public void readBoundaryMetadata() throws IOException
    {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final DataOutputStream out = new DataOutputStream(bout);
        final Geometry geom = new GeometryFactory()
                .createLinearRing(new Coordinate[]{
                        new CoordinateXY(1, 1),
                        new CoordinateXY(1, 2),
                        new CoordinateXY(2, 2),
                        new CoordinateXY(1, 1)});
        final BoundaryData data = new BoundaryData(123, 0, geom.getEnvelopeInternal(), geom.getArea(), geom);
        BinaryBoundaryEncoder.write(data, out);
        out.flush();

        final BoundaryMetadata deserialized = BinaryBoundaryEncoder.readBoundaryMetadata(new DataInputStream(new ByteArrayInputStream(bout.toByteArray())));
        assertThat(deserialized.getId()).isEqualTo(data.getId());
    }
}