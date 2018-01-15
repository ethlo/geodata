package com.ethlo.geodata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class IoUtils
{
    public static long lineCount(File file)
    {
        final Path path = file.toPath();
        try
        {
            return Files.lines(path).count();
        }
        catch (IOException exc)
        {
            return 0;
        }
    }
}
