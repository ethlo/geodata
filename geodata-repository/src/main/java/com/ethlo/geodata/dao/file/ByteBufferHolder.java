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
