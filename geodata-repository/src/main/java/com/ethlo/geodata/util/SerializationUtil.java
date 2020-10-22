package com.ethlo.geodata.util;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Supplier;

import org.springframework.data.util.CloseableIterator;

import com.ethlo.geodata.model.CompactSerializable;
import com.google.common.collect.AbstractIterator;

public class SerializationUtil
{
    public static <T extends CompactSerializable> CloseableIterator<T> read(Path filePath, Supplier<T> instanceCreator)
    {
        DataInputStream in;
        try
        {
            in = new DataInputStream(CompressionUtil.decompress(new BufferedInputStream(Files.newInputStream(filePath))));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }

        return wrapClosable(new AbstractIterator<T>()
        {
            @Override
            protected T computeNext()
            {
                try
                {
                    final T instance = instanceCreator.get();
                    instance.read(in);
                    return instance;
                }
                catch (EOFException ignored)
                {
                    return endOfData();
                }
                catch (IOException exc)
                {
                    throw new UncheckedIOException(exc);
                }
            }
        }, in);
    }

    public static <C> CloseableIterator<C> wrapClosable(final Iterator<C> iter, final Closeable closeable)
    {
        return new CloseableIterator<>()
        {
            @Override
            public void close()
            {
                if (closeable != null)
                {
                    try
                    {
                        closeable.close();
                    }
                    catch (IOException e)
                    {
                        throw new UncheckedIOException(e);
                    }
                }
            }

            @Override
            public boolean hasNext()
            {
                return iter.hasNext();
            }

            @Override
            public C next()
            {
                return iter.next();
            }
        };
    }
}
