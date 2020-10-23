package com.ethlo.geodata.util;

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
