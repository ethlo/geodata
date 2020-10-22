package com.ethlo.geodata.util;

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
    private static final LZMA2Options options = new LZMA2Options();

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
