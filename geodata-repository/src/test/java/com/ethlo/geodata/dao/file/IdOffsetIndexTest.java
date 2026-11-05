package com.ethlo.geodata.dao.file;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdOffsetIndexTest
{
    private static IdOffsetIndex sample()
    {
        return IdOffsetIndex.of(new long[]{
                IdOffsetIndex.entry(30, 0),
                IdOffsetIndex.entry(10, 100),
                IdOffsetIndex.entry(30, 200),
                IdOffsetIndex.entry(-5, 300),
                IdOffsetIndex.entry(30, 400),
                IdOffsetIndex.entry(20, Integer.MAX_VALUE)
        });
    }

    @Test
    void findsOffsetsById()
    {
        final IdOffsetIndex index = sample();
        assertThat(index.size()).isEqualTo(6);
        assertThat(index.offset(10, 0)).isEqualTo(100);
        assertThat(index.offset(-5, 0)).isEqualTo(300);
        assertThat(index.offset(20, 0)).isEqualTo(Integer.MAX_VALUE);
        assertThat(index.contains(20)).isTrue();
        assertThat(index.contains(15)).isFalse();
        assertThat(index.offset(15, 0)).isEqualTo(-1);
    }

    @Test
    void repeatedIdsKeepTheirOrder()
    {
        final IdOffsetIndex index = sample();
        assertThat(index.offset(30, 0)).isZero();
        assertThat(index.offset(30, 1)).isEqualTo(200);
        assertThat(index.offset(30, 2)).isEqualTo(400);
        assertThat(index.offset(30, 3)).isEqualTo(-1);
        assertThat(index.offset(10, 1)).isEqualTo(-1);
    }

    @Test
    void emptyIndex()
    {
        assertThat(IdOffsetIndex.empty().isEmpty()).isTrue();
        assertThat(IdOffsetIndex.empty().contains(1)).isFalse();
        assertThat(IdOffsetIndex.empty().offset(1, 0)).isEqualTo(-1);
    }

    @Test
    void rejectsNegativeOffset()
    {
        assertThatThrownBy(() -> IdOffsetIndex.entry(1, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
