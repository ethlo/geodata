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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;
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
