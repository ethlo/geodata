package com.ethlo.geodata.model;

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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

public class RawLocationTest
{
    private static byte[] write(final RawLocation location) throws IOException
    {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes))
        {
            location.write(out);
        }
        return bytes.toByteArray();
    }

    private static RawLocation read(final byte[] data) throws IOException
    {
        final RawLocation location = new RawLocation();
        location.read(new DataInputStream(new ByteArrayInputStream(data)));
        return location;
    }

    @Test
    public void testSerialization() throws IOException
    {
        final RawLocation l = new RawLocation(123, "norway", "NO", Coordinates.from(23.44499898D, 55.77777D), 33, 989898L, 23, 181);
        final byte[] data = write(l);
        final RawLocation after = read(data);
        assertThat(after).isEqualTo(l);
        assertThat(after.getCountryCode()).isEqualTo("NO");
        assertThat(after.getCoordinates().getLat()).isEqualTo(23.44499898D);
        assertThat(after.getCoordinates().getLng()).isEqualTo(55.77777D);
        assertThat(after.getPopulation()).isEqualTo(989898L);
        assertThat(after.getElevation()).isEqualTo(181);
        assertThat(data).hasSize(52);
    }

    @Test
    public void testSerializationWithoutCC() throws IOException
    {
        final RawLocation l = new RawLocation(123, "europe", null, Coordinates.from(23.44499898D, 55.77777D), 33, 989898L, 23, 181);
        final byte[] data = write(l);
        final RawLocation after = read(data);
        assertThat(after).isEqualTo(l);
        assertThat(after.getCountryCode()).isNull();
        assertThat(data).hasSize(50);
    }
}
