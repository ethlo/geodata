package com.ethlo.geodata.importer;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

public abstract class BaseFileImporter<T extends Serializable>
{
    private final Path basePath;

    public BaseFileImporter(final Path basePath)
    {
        this.basePath = basePath;
    }

    protected Path getFilePath(final GeonamesSource ip)
    {
        return basePath.resolve(ip.name().toLowerCase() + ".data");
    }

    protected void purge(final GeonamesSource type)
    {
        try
        {
            Files.deleteIfExists(getFilePath(type));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    public int writeData(GeonamesSource type, Iterator<T> data) throws IOException
    {
        int count = 0;
        try (final ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(getFilePath(type)))))
        {
            while (data.hasNext())
            {
                out.writeObject(data.next());
                count++;
            }
        }
        return count;
    }
}
