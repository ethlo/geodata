package com.ethlo.geodata.dao.file;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.util.Assert;

import com.ethlo.geodata.util.CompressionUtil;
import com.google.common.primitives.Ints;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;

public class BaseMmapDao
{
    private final Path indexPath;
    private final Path dataPath;

    private Map<Integer, Integer> indexMap;
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
                    indexMap.putIfAbsent(id, offset);
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
        return indexMap.get(id);
    }

    protected int size()
    {
        return indexMap.size();
    }
}
