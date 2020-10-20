package com.ethlo.geodata;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public class PersistentCacheManager
{
    private final Path basePath;

    public PersistentCacheManager(final Path basePath)
    {
        this.basePath = basePath;
    }

    public <T> T get(final String alias, CacheSerializer<T> cacheSerializer, final Supplier<T> loader)
    {
        final Path filePath = basePath.resolve(alias + ".cache");
        if (Files.exists(filePath))
        {
            try (final InputStream in = new BufferedInputStream(Files.newInputStream(filePath)))
            {
                return cacheSerializer.read(in);
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }
        else
        {
            final T result = loader.get();
            try (final OutputStream out = new BufferedOutputStream(Files.newOutputStream(filePath)))
            {
                cacheSerializer.write(result, out);
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
            return result;
        }
    }
}
