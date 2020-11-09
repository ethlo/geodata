package com.ethlo.geodata.util;

import java.nio.file.Path;

public class IoUtil
{
    public static String getExtension(Path path)
    {
        final String filename = path.getFileName().toString();
        final int dotIdx = filename.lastIndexOf('.');
        if (dotIdx > -1)
        {
            return filename.substring(dotIdx + 1);
        }
        return "";
    }
}
