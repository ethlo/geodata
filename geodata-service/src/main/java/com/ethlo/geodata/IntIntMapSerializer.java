package com.ethlo.geodata;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class IntIntMapSerializer implements CacheSerializer<Map<Integer, Integer>>
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
