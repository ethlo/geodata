package com.ethlo.geodata.io;

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
    public int read()
    {
        if (!buffer.hasRemaining())
        {
            return -1;
        }
        return buffer.get() & 0xFF;
    }

    @Override
    public int read(@Nonnull byte[] bytes, int off, int len)
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
