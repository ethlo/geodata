package com.ethlo.geodata;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class IoUtils
{
    private IoUtils()
    {
    }

    public static int lineCount(final File file)
    {
        try (LineNumberReader reader = new LineNumberReader(new FileReader(file)))
        {
            while ((reader.readLine()) != null)
            {
                if (reader.getLineNumber() % 1000 == 0)
                {
                    System.out.println(reader.getLineNumber());
                }
            }
            return reader.getLineNumber();
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
    }

    public static BufferedReader getBufferedReader(File file) throws FileNotFoundException
    {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
    }

    public static String humanReadableByteCount(long bytes, boolean si)
    {
        final int unit = si ? 1000 : 1024;
        if (bytes < unit)
        {
            return bytes + " B";
        }
        final int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
