package com.ethlo.geodata.importer.file;

/*-
 * #%L
 * Geodata service
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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.function.Function;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.util.CloseableIterator;

import com.google.common.collect.AbstractIterator;

public class JsonIoReader<T> extends JsonIo<T>
{
    public JsonIoReader(File file, Class<T> type)
    {
        super(file, type);
    }
    
    public <S> CloseableIterator<S> iterator(Function<T,S> converter)
    {
        final CloseableIterator<T> source = iterator();
        return wrap(new AbstractIterator<S>()
        {
            @Override
            protected S computeNext()
            {
                if (source.hasNext())
                {
                    return converter.apply(source.next());
                }
                return endOfData();
            }
            
        }, source);
    }

    private <I> CloseableIterator<I> wrap(Iterator<I> source, Closeable closable)
    {
        return new CloseableIterator<I>()
        {
            @Override
            public boolean hasNext()
            {
                return source.hasNext();
            }

            @Override
            public I next()
            {
                return source.next();
            }

            @Override
            public void close()
            {
                try
                {
                    closable.close();
                }
                catch (IOException exc)
                {
                    throw new DataAccessResourceFailureException(exc.getMessage(), exc);
                }
            }
        };
    }

    public CloseableIterator<T> iterator()
    {
        if (! file.exists())
        {
            return empty();
        }
        
        try
        {
            final FileLineIterator iter = new FileLineIterator(file);
            return new CloseableIterator<T>()
            {
                @Override
                public boolean hasNext()
                {
                    return iter.hasNext();
                }

                @Override
                public T next()
                {
                    try
                    {
                        return type.cast(mapper.readValue(iter.next(), type));
                    }
                    catch (IOException exc)
                    {
                        throw new DataAccessResourceFailureException(exc.getMessage(), exc);
                    }
                }

                @Override
                public void close()
                {
                    iter.close();
                }
            };
        }
        catch (IOException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }
    }

    private CloseableIterator<T> empty()
    {
        return new CloseableIterator<T>()
        {
            @Override
            public boolean hasNext()
            {
                return false;
            }

            @Override
            public T next()
            {
                return null;
            }

            @Override
            public void close()
            {
            }
        };
    }
}
