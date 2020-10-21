package com.ethlo.geodata.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.SerializationUtils.deserialize;
import static org.springframework.util.SerializationUtils.serialize;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class RawLocationTest
{
    @Test
    public void testSerialization()
    {
        final RawLocation l = new RawLocation(123, "norway", "NO", Coordinates.from(23.44499898D, 55.77777D), 33, 989898L, 23);
        final byte[] data = serialize(l);
        final RawLocation after = (RawLocation) deserialize(data);
        assertThat(after).isEqualTo(l);
        assertThat(data).hasSize(107);
    }

    @Test
    public void testSerializationWitoutCC()
    {
        final RawLocation l = new RawLocation(123, "europe", null, Coordinates.from(23.44499898D, 55.77777D), 33, 989898L, 23);
        final byte[] data = serialize(l);
        final RawLocation after = (RawLocation) deserialize(data);
        assertThat(after).isEqualTo(l);
        assertThat(data).hasSize(105);
    }
}