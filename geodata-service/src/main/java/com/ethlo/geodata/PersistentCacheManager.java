package com.ethlo.geodata;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;

public class PersistentCacheManager
{
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path basePath;

    public PersistentCacheManager(final Path basePath)
    {
        this.basePath = basePath;
    }

    public <T> T get(final String alias, final Class<T> type, final Supplier<T> loader)
    {
        final Path filePath = basePath.resolve(alias + "json.cache");
        if (Files.exists(filePath))
        {
            try (final Reader reader = Files.newBufferedReader(filePath))
            {
                return mapper.readValue(reader, type);
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }
        else
        {
            final T result = loader.get();
            try (final Writer writer = Files.newBufferedWriter(filePath))
            {
                mapper.writeValue(writer, result);
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
            return result;
        }
    }
}
