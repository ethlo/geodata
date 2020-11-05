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
import java.util.List;

import org.springframework.util.Assert;

import com.ethlo.geodata.util.CompressionUtil;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.primitives.Ints;

public class BaseMmapDao
{
    private final Path indexPath;
    private final Path dataPath;

    private ArrayListMultimap<Integer, Integer> indexMap;
    private ByteBufferHolder byteBufferHolder;

    public BaseMmapDao(Path basePath, String alias)
    {
        this.indexPath = basePath.resolve(alias + ".index");
        this.dataPath = basePath.resolve(alias + ".data");
    }

    public int load()
    {
        indexMap = loadIndex();
        this.byteBufferHolder = new ByteBufferHolder(dataPath);
        return indexMap.size();
    }

    private ArrayListMultimap<Integer, Integer> loadIndex()
    {
        try (final InputStream indexIn = Files.newInputStream(indexPath))
        {
            final byte[] countBuffer = new byte[4];
            Assert.isTrue(indexIn.read(countBuffer) == 4, "Expected to read 4 bytes");
            final int entries = Ints.fromByteArray(countBuffer);

            try (final DataInputStream compressedIndexIn = new DataInputStream(CompressionUtil.decompress(new BufferedInputStream(indexIn))))
            {
                this.indexMap = ArrayListMultimap.create();
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

    protected DataInputStream getInputStream(long position)
    {
        return new DataInputStream(byteBufferHolder.getInputStream(position));
    }

    protected Integer getOffset(final int id)
    {
        return getOffset(id, 0);
    }

    protected Integer getOffset(final int id, int subIndex)
    {
        final List<Integer> parts = indexMap.get(id);
        return parts.size() > subIndex ? parts.get(subIndex) : null;
    }


    protected int size()
    {
        return indexMap.size();
    }
}
