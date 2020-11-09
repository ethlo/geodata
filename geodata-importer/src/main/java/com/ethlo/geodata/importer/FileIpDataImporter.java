package com.ethlo.geodata.importer;

/*-
 * #%L
 * geodata-importer
 * %%
 * Copyright (C) 2017 - 2020 Morten Haraldsen (ethlo)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ethlo.geodata.DataType;
import com.ethlo.geodata.dao.file.FileIpDao;
import com.ethlo.geodata.util.ResourceUtil;
import com.ethlo.geodata.util.TarGzUtil;
import com.maxmind.db.Metadata;
import com.maxmind.db.Reader;

@Component
public class FileIpDataImporter implements DataImporter
{
    private final Path filePath;
    private final String url;
    private final Duration maxAge;

    public FileIpDataImporter(
            @Value("${geodata.geolite2.source.mmdb}") @NotNull final String url,
            @Value("${geodata.base-path}") final Path basePath,
            @Value("${geodata.max-data-age}") final Duration maxAge)
    {
        this.filePath = basePath.resolve(FileIpDao.IP_FILE);
        this.url = url;
        this.maxAge = maxAge;
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
            final Map.Entry<Date, File> entry = ResourceUtil.fetchResource(DataType.IP, maxAge, url);
            final Path tmp = filePath.getParent().resolve(FileIpDao.IP_FILE + ".tmp");
            TarGzUtil.extract(new BufferedInputStream(Files.newInputStream(entry.getValue().toPath())), (e) ->
            {
                if (e.getKey().getName().endsWith("GeoLite2-City.mmdb"))
                {
                    try
                    {
                        Files.copy(e.getValue(), tmp);
                    }
                    catch (IOException exc)
                    {
                        throw new UncheckedIOException(exc);
                    }
                }
            });
            Files.move(tmp, filePath, StandardCopyOption.ATOMIC_MOVE);
            final Metadata metadata = new Reader(filePath.toFile()).getMetadata();
            return extractCount(metadata);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    private int extractCount(final Metadata metadata)
    {
        try
        {
            final Field nodeCountField = Metadata.class.getDeclaredField("nodeCount");
            nodeCountField.setAccessible(true);
            return (int) nodeCountField.get(metadata);
        }
        catch (IllegalAccessException | NoSuchFieldException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return ResourceUtil.getLastModified(url);
    }
}
