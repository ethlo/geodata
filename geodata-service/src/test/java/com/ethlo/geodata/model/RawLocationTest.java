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
import static org.springframework.util.SerializationUtils.deserialize;
import static org.springframework.util.SerializationUtils.serialize;

import org.junit.jupiter.api.Test;

public class RawLocationTest
{
    @Test
    public void testSerialization()
    {
        final RawLocation l = new RawLocation(123, "norway", "NO", Coordinates.from(23.44499898D, 55.77777D), 33, 989898L, 23, 181);
        final byte[] data = serialize(l);
        final RawLocation after = (RawLocation) deserialize(data);
        assertThat(after).isEqualTo(l);
        assertThat(data).hasSize(107);
    }

    @Test
    public void testSerializationWithoutCC()
    {
        final RawLocation l = new RawLocation(123, "europe", null, Coordinates.from(23.44499898D, 55.77777D), 33, 989898L, 23, 181);
        final byte[] data = serialize(l);
        final RawLocation after = (RawLocation) deserialize(data);
        assertThat(after).isEqualTo(l);
        assertThat(data).hasSize(105);
    }
}
