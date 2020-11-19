package com.ethlo.geodata.importer;

import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class HierachyBuilderTest
{
    @Test
    public void lastOfNotEmptyEqualEntries()
    {
        Assertions.assertThat(HierachyBuilder.lastOfNotEmpty(1, new String[]{"BRU", "BRU", "", ""})).isEqualTo(Optional.of(1));
    }

    @Test
    public void lastOfNotEmpty()
    {
        Assertions.assertThat(HierachyBuilder.lastOfNotEmpty(1, new String[]{"FOO", "BAR", "", ""})).isEqualTo(Optional.of(1));
    }

    @Test
    public void testWith00()
    {
        Assertions.assertThat(HierachyBuilder.lastOfNotEmpty(1, new String[]{"00", "", ""})).isEqualTo(Optional.empty());
    }

    @Test
    public void testWith00NotFirst()
    {
        Assertions.assertThat(HierachyBuilder.lastOfNotEmpty(1, new String[]{"RM", "00", ""})).isEqualTo(Optional.of(1));
    }

    @Test
    public void testWithSelfReferenceError()
    {
        Assertions.assertThat(HierachyBuilder.lastOfNotEmpty(1, new String[]{"RM", "00", "", "1"})).isEqualTo(Optional.of(1));
    }

    @Test
    public void testEmpty()
    {
        Assertions.assertThat(HierachyBuilder.lastOfNotEmpty(1, new String[]{"", "", "", ""})).isEqualTo(Optional.empty());
    }
}