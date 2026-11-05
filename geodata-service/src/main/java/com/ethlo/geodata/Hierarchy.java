package com.ethlo.geodata;

/*-
 * #%L
 * geodata
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
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

import java.util.Arrays;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;

/**
 * Read-only location hierarchy kept in primitive arrays: sorted ids, the parent index of each id, and the children of each id as ranges of one shared array.
 */
public final class Hierarchy
{
    private static final int NONE = -1;
    private static final Hierarchy EMPTY = new Hierarchy(new int[0], new int[0], new int[1], new int[0]);

    private final int[] ids;
    private final int[] parentIndex;
    private final int[] childStart;
    private final int[] children;

    private Hierarchy(final int[] ids, final int[] parentIndex, final int[] childStart, final int[] children)
    {
        this.ids = ids;
        this.parentIndex = parentIndex;
        this.childStart = childStart;
        this.children = children;
    }

    public static Hierarchy empty()
    {
        return EMPTY;
    }

    /**
     * @param childToParent child id to parent id. Entries pointing to themselves are ignored.
     */
    public static Hierarchy of(final Map<Integer, Integer> childToParent)
    {
        final int[] childIds = new int[childToParent.size()];
        final int[] parentIds = new int[childToParent.size()];
        int count = 0;
        if (childToParent instanceof Int2IntMap primitive)
        {
            for (final Int2IntMap.Entry e : primitive.int2IntEntrySet())
            {
                if (e.getIntKey() != e.getIntValue())
                {
                    childIds[count] = e.getIntKey();
                    parentIds[count++] = e.getIntValue();
                }
            }
        }
        else
        {
            for (final Map.Entry<Integer, Integer> e : childToParent.entrySet())
            {
                if (!e.getKey().equals(e.getValue()))
                {
                    childIds[count] = e.getKey();
                    parentIds[count++] = e.getValue();
                }
            }
        }

        final int[] all = new int[count * 2];
        System.arraycopy(childIds, 0, all, 0, count);
        System.arraycopy(parentIds, 0, all, count, count);
        Arrays.parallelSort(all);
        final int[] ids = distinct(all);

        final int[] parentIndex = new int[ids.length];
        Arrays.fill(parentIndex, NONE);
        final int[] childStart = new int[ids.length + 1];
        for (int i = 0; i < count; i++)
        {
            final int parent = Arrays.binarySearch(ids, parentIds[i]);
            parentIndex[Arrays.binarySearch(ids, childIds[i])] = parent;
            childStart[parent + 1]++;
        }
        for (int i = 0; i < ids.length; i++)
        {
            childStart[i + 1] += childStart[i];
        }

        final int[] children = new int[count];
        final int[] fill = Arrays.copyOf(childStart, ids.length);
        for (int i = 0; i < ids.length; i++)
        {
            final int parent = parentIndex[i];
            if (parent != NONE)
            {
                children[fill[parent]++] = ids[i];
            }
        }
        return new Hierarchy(ids, parentIndex, childStart, children);
    }

    private static int[] distinct(final int[] sorted)
    {
        int n = 0;
        for (int i = 0; i < sorted.length; i++)
        {
            if (i == 0 || sorted[i] != sorted[i - 1])
            {
                sorted[n++] = sorted[i];
            }
        }
        return Arrays.copyOf(sorted, n);
    }

    public int size()
    {
        return ids.length;
    }

    public boolean contains(final int id)
    {
        return Arrays.binarySearch(ids, id) >= 0;
    }

    /**
     * @return the parent id, or null if the id has no parent or is unknown
     */
    public Integer parentOf(final int id)
    {
        final int index = Arrays.binarySearch(ids, id);
        if (index < 0 || parentIndex[index] == NONE)
        {
            return null;
        }
        return ids[parentIndex[index]];
    }

    /**
     * @return the child ids, sorted, or an empty list if there are none or the id is unknown
     */
    public IntList childrenOf(final int id)
    {
        final int index = Arrays.binarySearch(ids, id);
        if (index < 0 || childStart[index] == childStart[index + 1])
        {
            return IntLists.emptyList();
        }
        return IntLists.unmodifiable(IntArrayList.wrap(Arrays.copyOfRange(children, childStart[index], childStart[index + 1])));
    }

    /**
     * @return the id followed by its ancestors up to the root, or null if the id is unknown. A circular reference ends the path before the repeated id.
     */
    public IntList pathOf(final int id)
    {
        int index = Arrays.binarySearch(ids, id);
        if (index < 0)
        {
            return null;
        }
        final IntArrayList path = new IntArrayList(8);
        while (index != NONE)
        {
            final int current = ids[index];
            if (path.contains(current))
            {
                break;
            }
            path.add(current);
            index = parentIndex[index];
        }
        return path;
    }
}
