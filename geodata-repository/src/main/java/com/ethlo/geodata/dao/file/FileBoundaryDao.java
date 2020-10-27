package com.ethlo.geodata.dao.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.BoundaryDao;

@Repository
public class FileBoundaryDao implements BoundaryDao
{
    private final Path basePath;

    public FileBoundaryDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.basePath = basePath.resolve("boundaries");
    }

    @Override
    public Optional<byte[]> findById(final int id)
    {
        final Path file = basePath.resolve(id + ".json");
        if (Files.exists(file))
        {
            try
            {
                return Optional.of(Files.readAllBytes(file));
            }
            catch (IOException exc)
            {
                throw new UncheckedIOException(exc);
            }
        }
        return Optional.empty();
    }
}
