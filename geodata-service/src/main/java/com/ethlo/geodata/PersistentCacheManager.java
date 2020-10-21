package com.ethlo.geodata;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
            final Path tmpFile = basePath.resolve(alias + ".tmp");
            try (final OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmpFile)))
            {
                cacheSerializer.write(result, out);
                Files.move(tmpFile, filePath, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            } finally
            {
                try
                {
                    Files.deleteIfExists(tmpFile);
                }
                catch (IOException ignored)
                {

                }
            }
            return result;
        }
    }
}
