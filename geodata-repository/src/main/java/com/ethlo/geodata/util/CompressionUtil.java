package com.ethlo.geodata.util;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.springframework.util.FastByteArrayOutputStream;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.UnsupportedOptionsException;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

public class CompressionUtil
{
    public static final LZMA2Options options = new LZMA2Options();

    static
    {
        try
        {
            options.setMode(LZMA2Options.MODE_FAST);
            //options.setMatchFinder(LZMA2Options.MF_HC4);
            //options.setDictSize(LZMA2Options.DICT_SIZE_MIN);
            //options.setNiceLen(100);
        }
        catch (UnsupportedOptionsException exc)
        {
            throw new IllegalStateException(exc);
        }
    }

    public static OutputStream compress(OutputStream out) throws IOException
    {
        return new XZOutputStream(out, options);
    }

    public static InputStream decompress(InputStream in) throws IOException
    {
        return new XZInputStream(in);
    }

    public byte[] compress(byte[] uncompressed) throws IOException
    {
        try (final FastByteArrayOutputStream out = new FastByteArrayOutputStream(Math.max(1024, uncompressed.length / 10)))
        {
            final OutputStream compOut = new XZOutputStream(out, options, XZ.CHECK_CRC64);
            compOut.write(uncompressed);
            compOut.close();
            return out.toByteArray();
        }
    }
}
