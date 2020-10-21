package com.ethlo.geodata.cache;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import com.ethlo.geodata.CacheSerializer;

public class SerializableSerializer<T> implements CacheSerializer<T>
{
    @Override
    public void write(final T obj, final OutputStream target) throws IOException
    {
        final ObjectOutputStream out = new ObjectOutputStream(target);
        out.writeObject(obj);
    }

    @Override
    public T read(final InputStream source) throws IOException
    {
        final ObjectInputStream in = new ObjectInputStream(source);
        try
        {
            return (T) in.readObject();
        }
        catch (ClassNotFoundException e)
        {
            throw new UncheckedIOException(new IOException(e));
        }
    }
}
