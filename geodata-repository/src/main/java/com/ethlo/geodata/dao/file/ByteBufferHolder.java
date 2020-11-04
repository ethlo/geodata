package com.ethlo.geodata.dao.file;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import com.ethlo.geodata.io.ByteBufferBackedInputStream;
import com.google.common.primitives.Ints;

public class ByteBufferHolder
{
    private final ByteBuffer internalBuffer;

    private final ThreadLocal<ByteBuffer> readOnlyBuffers = new ThreadLocal<>()
    {
        @Override
        protected ByteBuffer initialValue()
        {
            return internalBuffer.asReadOnlyBuffer();
        }
    };

    public ByteBufferHolder(final Path dataPath)
    {
        try (final RandomAccessFile file = new RandomAccessFile(dataPath.toFile(), "r"); final FileChannel channel = file.getChannel())
        {
            this.internalBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length()).asReadOnlyBuffer();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    public InputStream getInputStream(final long position)
    {
        final ByteBuffer byteBuffer = readOnlyBuffers.get();
        byteBuffer.position(Ints.checkedCast(position));
        return new DataInputStream(new ByteBufferBackedInputStream(byteBuffer));
    }
}
