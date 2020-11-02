package com.ethlo.geodata.importer;

/*-
 * #%L
 * geodata-importer
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


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.collections4.iterators.FilterIterator;
import org.springframework.data.util.CloseableIterator;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.util.SerializationUtil;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;

public class CsvFileIterator<T> implements CloseableIterator<T>
{
    private final Path file;
    private final List<String> header;
    private final boolean isTsv;
    private final int skipLines;
    private final Function<Map<String, String>, T> converter;
    private final CloseableIterator<T> delegate;

    public CsvFileIterator(final Path file, List<String> header, final boolean isTsv, int skipLines, Function<Map<String, String>, T> converter)
    {
        this.file = file;
        this.header = header;
        this.isTsv = isTsv;
        this.skipLines = skipLines;
        this.converter = converter;
        this.delegate = iterator();
    }

    protected CloseableIterator<T> iterator()
    {
        final CloseableIterator<Map<String, String>> lineIterator = rawIterator(file, header);
        final Iterator<T> entryIterator = new AbstractIterator<>()
        {
            @SuppressWarnings("StatementWithEmptyBody")
            @Override
            protected T computeNext()
            {
                T result = null;
                while (lineIterator.hasNext() && (result = converter.apply(lineIterator.next())) == null) ;
                if (result != null)
                {
                    return result;
                }
                return endOfData();
            }
        };

        return SerializationUtil.wrapClosable(entryIterator, lineIterator);
    }

    private BufferedReader getPositionedReader(final Path csvFile)
    {
        try
        {
            final BufferedReader reader = new BufferedReader(new FileReader(csvFile.toFile()));

            // Skip requested number of lines
            for (int i = 0; i < skipLines; i++)
            {
                reader.readLine();
            }
            return reader;
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    private Map<String, String> convert(String line, List<String> header)
    {
        final String[] entry = StringUtils.delimitedListToStringArray(line, isTsv ? "\t" : ",");
        final Map<String, String> data = new HashMap<>(entry.length);
        for (int i = 0; i < Math.min(header.size(), entry.length); i++)
        {
            data.put(header.get(i), entry[i]);
        }
        return data;
    }

    private CloseableIterator<Map<String, String>> rawIterator(Path file, List<String> columns)
    {
        final BufferedReader reader = getPositionedReader(file);
        final Iterator<String> lineIter = new AbstractIterator<>()
        {
            @Override
            protected String computeNext()
            {
                try
                {
                    String line = reader.readLine();
                    if (line != null)
                    {
                        return line;
                    }
                    return endOfData();
                }
                catch (IOException exc)
                {
                    throw new UncheckedIOException(exc);
                }
            }
        };
        final Iterator<String> nonNullIter = new FilterIterator<>(lineIter, line -> line != null && !line.isBlank() && !line.startsWith("#"));
        return SerializationUtil.wrapClosable(Iterators.transform(nonNullIter, line -> convert(line, columns)), reader);
    }

    @Override
    public void close()
    {
        this.delegate.close();
    }

    @Override
    public boolean hasNext()
    {
        return delegate.hasNext();
    }

    @Override
    public T next()
    {
        return delegate.next();
    }
}
