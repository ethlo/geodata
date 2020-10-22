package com.ethlo.geodata.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtil
{
    private static final ObjectMapper mapper = new ObjectMapper();

    public static <T> T read(final Path path, final Class<T> type)
    {
        try
        {
            return mapper.readValue(path.toFile(), type);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    public static void write(final Path path, final Object data)
    {
        try
        {
            mapper.writeValue(path.toFile(), data);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }
}
