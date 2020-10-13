package com.ethlo.geodata.boundaries;

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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.util.CloseableIterator;

import com.ethlo.geodata.importer.file.FileGeonamesBoundaryImporter;
import com.google.common.collect.AbstractIterator;

public class WkbDataReader implements AutoCloseable
{
    // ID to offset map
    private final Map<Long, Map.Entry<Long, Integer>> offsetMap = new LinkedHashMap<>();
    private RandomAccessFile raf;
    private File file;

    public WkbDataReader(File file)
    {
        this.file = file;
        final File indexFile = new File(file.getParentFile(), FileGeonamesBoundaryImporter.BOUNDARIES_FILENAME + ".index");

        if (file.exists() && indexFile.exists())
        {
            try
            {
                openFile(file);
                loadIndexFile(indexFile);
            }
            catch (IOException exc)
            {
                throw new DataAccessResourceFailureException(exc.getMessage(), exc);
            }
        }
    }

    private void openFile(File file) throws IOException
    {
        this.raf = new RandomAccessFile(new File(file.getParentFile(), FileGeonamesBoundaryImporter.BOUNDARIES_FILENAME), "r");
    }

    private void loadIndexFile(File indexFile) throws IOException
    {
        try (final DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(indexFile))))
        {
            try
            {
                while (true)
                {
                    final long id = in.readLong();
                    final long offset = in.readLong();
                    final int length = in.readInt();
                    offsetMap.put(id, new AbstractMap.SimpleEntry<>(offset, length));
                }
            }
            catch (EOFException exc)
            {
                // Ignored
            }
        }
    }

    public byte[] read(long id)
    {
        final Map.Entry<Long, Integer> offsetAndLength = offsetMap.get(id);
        try
        {
            return offsetAndLength != null ? readAtOffset(offsetAndLength) : null;
        }
        catch (IOException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }
    }

    private byte[] readAtOffset(Entry<Long, Integer> offsetAndLength) throws IOException
    {
        synchronized (raf)
        {
            final byte[] b = new byte[offsetAndLength.getValue()];
            raf.seek(offsetAndLength.getKey());
            raf.read(b);
            return b;
        }
    }

    @Override
    public void close() throws Exception
    {
        if (raf != null)
        {
            raf.close();
        }
    }

    @SuppressWarnings("resource")
    public CloseableIterator<Entry<Long, byte[]>> iterator()
    {
        if (!file.exists())
        {
            return new CloseableIterator<Map.Entry<Long, byte[]>>()
            {
                @Override
                public boolean hasNext()
                {
                    return false;
                }

                @Override
                public Entry<Long, byte[]> next()
                {
                    return null;
                }

                @Override
                public void close()
                {
                }
            };
        }
        final Iterator<Entry<Long, Entry<Long, Integer>>> iter = offsetMap.entrySet().iterator();
        DataInputStream in;
        try
        {
            in = new DataInputStream(new FileInputStream(file));
        }
        catch (FileNotFoundException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }

        final Iterator<Map.Entry<Long, byte[]>> closable = new AbstractIterator<Map.Entry<Long, byte[]>>()
        {
            @Override
            protected Entry<Long, byte[]> computeNext()
            {
                if (iter.hasNext())
                {
                    final Entry<Long, Entry<Long, Integer>> e = iter.next();
                    final byte[] data = new byte[e.getValue().getValue()];
                    try
                    {
                        in.readFully(data);
                    }
                    catch (IOException exc)
                    {
                        throw new DataAccessResourceFailureException(exc.getMessage(), exc);
                    }
                    return new AbstractMap.SimpleEntry<>(e.getKey(), data);
                }
                return endOfData();
            }
        };
        return new CloseableIterator<Entry<Long, byte[]>>()
        {

            @Override
            public boolean hasNext()
            {
                return closable.hasNext();
            }

            @Override
            public Entry<Long, byte[]> next()
            {
                return closable.next();
            }

            @Override
            public void close()
            {
                try
                {
                    in.close();
                }
                catch (IOException exc)
                {
                    throw new DataAccessResourceFailureException(exc.getMessage(), exc);
                }
            }
        };
    }
}
