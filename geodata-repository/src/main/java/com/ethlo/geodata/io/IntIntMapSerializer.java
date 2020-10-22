package com.ethlo.geodata.io;

/*-
 * #%L
 * geodata-common
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class IntIntMapSerializer implements DataSerializer<Map<Integer, Integer>>
{
    @Override
    public void write(final Map<Integer, Integer> obj, final OutputStream target) throws IOException
    {
        try (final DataOutputStream out = new DataOutputStream(target))
        {
            out.writeInt(obj.size());
            for (Map.Entry<Integer, Integer> e : obj.entrySet())
            {
                out.writeInt(e.getKey());
                out.writeInt(e.getValue());
            }
        }
    }

    @Override
    public Map<Integer, Integer> read(final InputStream source) throws IOException
    {
        try (final DataInputStream in = new DataInputStream(source))
        {
            final int size = in.readInt();
            final Map<Integer, Integer> result = new Int2IntOpenHashMap(size);
            for (int i = 0; i < size; i++)
            {
                final int k = in.readInt();
                final int v = in.readInt();
                result.put(k, v);
            }
            return result;
        }
    }
}
