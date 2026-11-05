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

import java.util.Arrays;

/**
 * Read-only id to offset index kept in two sorted primitive arrays. An id may have several offsets, which are kept in insertion order.
 */
public final class IdOffsetIndex
{
    private static final IdOffsetIndex EMPTY = new IdOffsetIndex(new int[0], new int[0]);

    private final int[] ids;
    private final int[] offsets;

    private IdOffsetIndex(final int[] ids, final int[] offsets)
    {
        this.ids = ids;
        this.offsets = offsets;
    }

    public static IdOffsetIndex empty()
    {
        return EMPTY;
    }

    /**
     * @param entries id in the high 32 bits and offset in the low 32 bits, in any order. The array is sorted in place.
     */
    public static IdOffsetIndex of(final long[] entries)
    {
        Arrays.parallelSort(entries);
        final int[] ids = new int[entries.length];
        final int[] offsets = new int[entries.length];
        for (int i = 0; i < entries.length; i++)
        {
            ids[i] = (int) (entries[i] >> 32);
            offsets[i] = (int) entries[i];
        }
        return new IdOffsetIndex(ids, offsets);
    }

    public static long entry(final int id, final int offset)
    {
        if (offset < 0)
        {
            throw new IllegalArgumentException("Negative offset " + offset + " for id " + id);
        }
        return ((long) id << 32) | offset;
    }

    public int size()
    {
        return ids.length;
    }

    public boolean isEmpty()
    {
        return ids.length == 0;
    }

    public boolean contains(final int id)
    {
        return Arrays.binarySearch(ids, id) >= 0;
    }

    /**
     * @return the offset of the given part of the id, or -1 if there is none
     */
    public int offset(final int id, final int subIndex)
    {
        final int first = firstIndexOf(id);
        if (first < 0 || subIndex < 0)
        {
            return -1;
        }
        final int index = first + subIndex;
        return index < ids.length && ids[index] == id ? offsets[index] : -1;
    }

    public int idAt(final int index)
    {
        return ids[index];
    }

    public int offsetAt(final int index)
    {
        return offsets[index];
    }

    private int firstIndexOf(final int id)
    {
        int low = 0;
        int high = ids.length;
        while (low < high)
        {
            final int mid = (low + high) >>> 1;
            if (ids[mid] < id)
            {
                low = mid + 1;
            }
            else
            {
                high = mid;
            }
        }
        return low < ids.length && ids[low] == id ? low : -1;
    }
}
