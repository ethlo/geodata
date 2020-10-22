package com.ethlo.geodata.importer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ethlo.geodata.DataType;
import com.ethlo.geodata.util.ResourceUtil;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;

@Component
public class FileIpDataImporter implements DataImporter
{
    private static final String IP_FILE = "geolite2.mmdb";
    private final Path filePath;
    private final String url;

    public FileIpDataImporter(
            @Value("${geodata.geolite2.source.mmdb}") final String url,
            @Value("${geodata.base-path}") final Path basePath)
    {
        this.filePath = basePath.resolve(IP_FILE);
        this.url = url;
    }

    @Override
    public void purgeData()
    {
        try
        {
            Files.deleteIfExists(filePath);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int importData()
    {
        try
        {
            final Map.Entry<Date, File> entry = ResourceUtil.fetchResource(DataType.IP, url);
            final Path tmp = filePath.getParent().resolve("ip.tmp");
            final InputStream source = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(entry.getValue().toPath())));
            final long bytes = ByteStreams.copy(source, Files.newOutputStream(tmp));
            Files.move(tmp, filePath, StandardCopyOption.ATOMIC_MOVE);
            return Ints.saturatedCast(bytes);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return ResourceUtil.getLastModified(url);
    }
}