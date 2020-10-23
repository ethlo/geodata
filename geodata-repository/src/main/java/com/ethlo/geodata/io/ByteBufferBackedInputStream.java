package com.ethlo.geodata.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.annotation.Nonnull;

public class ByteBufferBackedInputStream extends InputStream
{
    private final ByteBuffer buffer;

    public ByteBufferBackedInputStream(ByteBuffer buffer)
    {
        this.buffer = buffer;
    }

    @Override
    public int read() throws IOException
    {
        if (!buffer.hasRemaining())
        {
            return -1;
        }
        return buffer.get() & 0xFF;
    }

    @Override
    public int read(@Nonnull byte[] bytes, int off, int len) throws IOException
    {
        if (!buffer.hasRemaining())
        {
            return -1;
        }

        len = Math.min(len, buffer.remaining());
        buffer.get(bytes, off, len);
        return len;
    }
}