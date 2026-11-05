package com.ethlo.geodata;

/*-
 * #%L
 * geodata-service
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

import java.util.Map;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

class HierarchyTest
{
    private static final int EARTH = 1;
    private static final int EUROPE = 2;
    private static final int NORWAY = 3;
    private static final int OSLO = 4;
    private static final int BERGEN = 5;

    private static Hierarchy sample()
    {
        return Hierarchy.of(new Int2IntOpenHashMap(Map.of(EUROPE, EARTH, NORWAY, EUROPE, OSLO, NORWAY, BERGEN, NORWAY)));
    }

    @Test
    void parentsAndChildren()
    {
        final Hierarchy hierarchy = sample();
        assertThat(hierarchy.size()).isEqualTo(5);
        assertThat(hierarchy.contains(EARTH)).isTrue();
        assertThat(hierarchy.contains(99)).isFalse();
        assertThat(hierarchy.parentOf(OSLO)).isEqualTo(NORWAY);
        assertThat(hierarchy.parentOf(EARTH)).isNull();
        assertThat(hierarchy.parentOf(99)).isNull();
        assertThat(hierarchy.childrenOf(NORWAY).toIntArray()).containsExactly(OSLO, BERGEN);
        assertThat(hierarchy.childrenOf(OSLO).toIntArray()).isEmpty();
        assertThat(hierarchy.childrenOf(99).toIntArray()).isEmpty();
    }

    @Test
    void pathGoesFromLocationToRoot()
    {
        final Hierarchy hierarchy = sample();
        assertThat(hierarchy.pathOf(OSLO).toIntArray()).containsExactly(OSLO, NORWAY, EUROPE, EARTH);
        assertThat(hierarchy.pathOf(EARTH).toIntArray()).containsExactly(EARTH);
        assertThat(hierarchy.pathOf(99) == null).isTrue();
        assertThat(hierarchy.isCircular(OSLO)).isFalse();
    }

    @Test
    void circularReferenceEndsThePath()
    {
        final Hierarchy hierarchy = Hierarchy.of(Map.of(OSLO, NORWAY, NORWAY, EUROPE, EUROPE, NORWAY));
        assertThat(hierarchy.pathOf(OSLO).toIntArray()).containsExactly(OSLO, NORWAY, EUROPE);
        assertThat(hierarchy.isCircular(OSLO)).isTrue();
    }

    @Test
    void selfReferenceIsIgnored()
    {
        final Hierarchy hierarchy = Hierarchy.of(Map.of(OSLO, OSLO, NORWAY, EUROPE));
        assertThat(hierarchy.contains(OSLO)).isFalse();
        assertThat(hierarchy.parentOf(NORWAY)).isEqualTo(EUROPE);
    }
}
