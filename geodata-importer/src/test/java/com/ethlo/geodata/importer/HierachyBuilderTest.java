package com.ethlo.geodata.importer;

/*-
 * #%L
 * geodata-importer
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

import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

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
