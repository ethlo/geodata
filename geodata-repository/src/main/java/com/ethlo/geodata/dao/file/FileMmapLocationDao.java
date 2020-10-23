package com.ethlo.geodata.dao.file;

import static com.ethlo.geodata.dao.file.FileLocationDao.LOCATION_FILE;

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
        this.indexPath = basePath.resolve(LOCATION_FILE + ".index");
        indexMap = loadIndex();
        this.internalBuffer = openDataFile();
        return indexMap.size();
    }

    private ByteBuffer openDataFile()
    {
        try
        {
            final Path dataPath = basePath.resolve(LOCATION_FILE + ".raw");
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
