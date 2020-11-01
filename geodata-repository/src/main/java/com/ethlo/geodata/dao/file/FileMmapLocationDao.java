package com.ethlo.geodata.dao.file;

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
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.io.ByteBufferBackedInputStream;
import com.ethlo.geodata.model.RawLocation;
import com.ethlo.geodata.util.CompressionUtil;
import com.ethlo.geodata.util.SerializationUtil;
import com.google.common.collect.AbstractIterator;
import com.google.common.primitives.Ints;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;

@Repository
public class FileMmapLocationDao implements LocationDao
{
    public static final String LOCATION_DATA_FILE = "locations.data";
    public static final String LOCATION_INDEX_FILE = "locations.index";

    private final Path basePath;
    private Map<Integer, Integer> indexMap;
    private Path indexPath;
    private ByteBuffer internalBuffer;
    private final ThreadLocal<ByteBuffer> readOnlyBuffers = new ThreadLocal<>()
    {
        @Override
        protected ByteBuffer initialValue()
        {
            return internalBuffer.asReadOnlyBuffer();
        }
    };

    public FileMmapLocationDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.basePath = basePath;
    }

    @Override
    public int load()
    {
        this.indexPath = basePath.resolve(LOCATION_INDEX_FILE);
        indexMap = loadIndex();
        this.internalBuffer = openDataFile();
        return indexMap.size();
    }

    private ByteBuffer openDataFile()
    {
        try
        {
            final Path dataPath = basePath.resolve(LOCATION_DATA_FILE);
            try (final RandomAccessFile file = new RandomAccessFile(dataPath.toFile(), "r"); final FileChannel channel = file.getChannel())
            {
                return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()).asReadOnlyBuffer();
            }
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    private Map<Integer, Integer> loadIndex()
    {
        try (final InputStream indexIn = Files.newInputStream(indexPath))
        {
            final byte[] countBuffer = new byte[4];
            Assert.isTrue(indexIn.read(countBuffer) == 4, "Expected to read 4 bytes");
            final int entries = Ints.fromByteArray(countBuffer);

            try (final DataInputStream compressedIndexIn = new DataInputStream(CompressionUtil.decompress(new BufferedInputStream(indexIn))))
            {
                this.indexMap = new Int2IntLinkedOpenHashMap(entries);
                for (int i = 0; i < entries; i++)
                {
                    final int id = compressedIndexIn.readInt();
                    final int offset = compressedIndexIn.readInt();
                    indexMap.put(id, offset);
                }
            }
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
        return indexMap;
    }

    @Override
    public CloseableIterator<RawLocation> iterator()
    {
        final Iterator<Integer> offsetIterator = indexMap.values().iterator();
        return SerializationUtil.wrapClosable(new AbstractIterator<>()
        {
            @Override
            protected RawLocation computeNext()
            {
                if (offsetIterator.hasNext())
                {
                    final int offset = offsetIterator.next();
                    return readDataAtOffset(offset);
                }
                return endOfData();
            }
        }, null);
    }

    @Override
    public Optional<RawLocation> get(final int id)
    {
        return Optional.ofNullable(indexMap.get(id)).map(this::readDataAtOffset);
    }

    @Override
    public int size()
    {
        return indexMap.size();
    }

    private RawLocation readDataAtOffset(final Integer offset)
    {
        final ByteBuffer buffer = readOnlyBuffers.get();
        buffer.position(offset);
        final DataInputStream in = new DataInputStream(new ByteBufferBackedInputStream(buffer));
        final RawLocation l = new RawLocation();
        try
        {
            l.read(in);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        return l;
    }
}
