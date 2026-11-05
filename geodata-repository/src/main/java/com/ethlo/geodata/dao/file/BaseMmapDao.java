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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.io.input.BoundedInputStream;
import org.springframework.util.Assert;

import com.ethlo.geodata.io.RecordType;
import com.ethlo.geodata.util.CompressionUtil;
import com.google.common.primitives.Ints;

public class BaseMmapDao
{
    private final Path indexPath;
    private final Path dataPath;

    private IdOffsetIndex index;
    private ByteBufferHolder byteBufferHolder;

    public BaseMmapDao(Path basePath, String alias)
    {
        this.indexPath = basePath.resolve(alias + ".index");
        this.dataPath = basePath.resolve(alias + ".data");
    }

    public int load()
    {
        if (index == null)
        {
            final IdOffsetIndex loaded = loadIndex();
            if (!loaded.isEmpty())
            {
                this.byteBufferHolder = new ByteBufferHolder(dataPath);
            }
            this.index = loaded;
        }
        return index.size();
    }

    private IdOffsetIndex loadIndex()
    {
        if (Files.exists(indexPath))
        {
            try (final InputStream indexIn = Files.newInputStream(indexPath))
            {
                final byte[] countBuffer = new byte[4];
                Assert.isTrue(indexIn.read(countBuffer) == 4, "Expected to read 4 bytes");
                final int entries = Ints.fromByteArray(countBuffer);

                final long[] packed = new long[entries];
                try (final DataInputStream compressedIndexIn = new DataInputStream(CompressionUtil.decompress(new BufferedInputStream(indexIn))))
                {
                    for (int i = 0; i < entries; i++)
                    {
                        final int id = compressedIndexIn.readInt();
                        final int offset = compressedIndexIn.readInt();
                        packed[i] = IdOffsetIndex.entry(id, offset);
                    }
                }
                return IdOffsetIndex.of(packed);
            }
            catch (IOException exc)
            {
                throw new UncheckedIOException(exc);
            }
        }
        return IdOffsetIndex.empty();
    }

    protected Stream<Map.Entry<Integer, DataInputStream>> rawIterator()
    {
        final IdOffsetIndex idx = index;
        return IntStream.range(0, idx.size()).parallel().mapToObj(i -> new AbstractMap.SimpleEntry<>(idx.idAt(i), getInputStream(idx.offsetAt(i))));
    }

    protected DataInputStream getInputStream(long position)
    {
        try
        {
            final DataInputStream in = new DataInputStream(byteBufferHolder.getInputStream(position));
            final int typeInfo = in.readUnsignedByte();
            final int blockSize = in.readInt();

            final BoundedInputStream bounded = new BoundedInputStream(in, blockSize);
            return new DataInputStream(typeInfo == RecordType.LZMA2_PREFIXED_LENGTH.getId() ? CompressionUtil.decompress(bounded) : bounded);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    protected Integer getOffset(final int id)
    {
        return getOffset(id, 0);
    }

    protected Integer getOffset(final int id, int subIndex)
    {
        return index.offset(id, subIndex);
    }

    public boolean exists(int id)
    {
        return index.contains(id);
    }

    public int size()
    {
        return index.size();
    }
}
