package com.ethlo.geodata.util;

/*-
 * #%L
 * geodata
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class ResourceUtil
{
    private static final Logger logger = LoggerFactory.getLogger(ResourceUtil.class);
    private final Path tmpDir;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)  // follow all redirects automatically
            .build();

    public ResourceUtil(@Value("${geodata.tmp.dir}") Path tmpDir)
    {
        this.tmpDir = tmpDir;
    }

    private static OffsetDateTime getOffsetDateTime(final Resource fileRes) throws IOException
    {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(fileRes.lastModified()), ZoneOffset.systemDefault());
    }

    public OffsetDateTime getLastModified(String urlStr) throws IOException
    {
        final Entry<OffsetDateTime, InputStream> connection = openConnection(urlStr);
        return connection.getKey();
    }

    private Entry<OffsetDateTime, InputStream> openConnection(String urlStr) throws IOException
    {
        final String[] urlParts = StringUtils.split(urlStr, "|");

        if (urlStr.startsWith("file:"))
        {
            String path = urlParts[0].substring(7);
            if (path.startsWith("~" + File.separator))
            {
                path = System.getProperty("user.home") + path.substring(1);
            }
            final File file = new File(path);
            if (!file.exists())
            {
                throw new FileNotFoundException(file.getAbsolutePath());
            }
            final FileSystemResource fileRes = new FileSystemResource(file);
            return new AbstractMap.SimpleEntry<>(getOffsetDateTime(fileRes), fileRes.getInputStream());
        }
        else if (urlStr.startsWith("classpath:"))
        {
            final ClassPathResource classRes = new ClassPathResource(urlParts[0].substring(10));
            return new AbstractMap.SimpleEntry<>(getOffsetDateTime(classRes), classRes.getInputStream());
        }

        final HttpRequest request = HttpRequest.newBuilder()
                .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                .uri(URI.create(urlParts[0]))
                .build();

        try
        {
            final HttpResponse<InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200)
            {
                throw new IOException("HTTP status " + resp.statusCode() + " returned for URL " + request.uri());
            }
            final OffsetDateTime lastModified = resp.headers().firstValue("Last-Modified").map(d -> ZonedDateTime.parse(d, DateTimeFormatter.RFC_1123_DATE_TIME)).map(ZonedDateTime::toOffsetDateTime).orElseThrow();
            return new AbstractMap.SimpleEntry<>(lastModified, resp.body());
        }
        catch (ConnectException e)
        {
            throw new UncheckedIOException("Cannot connect to " + request.uri(), e);
        }
        catch (InterruptedException e)
        {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    public Entry<OffsetDateTime, Path> fetchResource(String alias, Duration maxAge, String urlStr) throws IOException
    {
        final String[] urlParts = StringUtils.split(urlStr, "|");
        if (urlParts[0].endsWith("zip"))
        {
            return fetchZip(alias, urlParts[0], maxAge, urlParts[1]);
        }
        else
        {
            return fetch(alias, urlStr, maxAge);
        }
    }

    private Map.Entry<OffsetDateTime, Path> fetchZip(String alias, String url, Duration maxAge, String zipEntry) throws IOException
    {
        final Entry<OffsetDateTime, InputStream> resource = openConnection(url);
        return downloadIfNewer(alias, url, resource.getKey(), resource.getValue(), maxAge, f ->
        {
            final ZipInputStream zipIn = new ZipInputStream(resource.getValue());
            ZipEntry entry;
            do
            {
                entry = zipIn.getNextEntry();
            }
            while (entry != null && !entry.getName().endsWith(zipEntry));
            Assert.notNull(entry, "Zip entry cannot be found: " + zipEntry);
            return zipIn;
        });
    }

    private Entry<OffsetDateTime, Path> downloadIfNewer(String alias, String url, OffsetDateTime remoteLastModified, InputStream remoteResource, Duration maxAge, CheckedFunction<InputStream, InputStream> fun) throws IOException
    {
        final Path file = tmpDir.resolve(alias + "_" + url.hashCode());
        final OffsetDateTime localLastModified = Files.exists(file) ? OffsetDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault()) : OffsetDateTime.MIN;

        logger.info("Local file for " + "alias {}: Path: {}" + " - Exists: {}" + " - Last-Modified: {}", alias, file, Files.exists(file), localLastModified);

        if (remoteLastModified.isAfter(localLastModified.plus(maxAge.toMillis(), ChronoUnit.MILLIS)))
        {
            logger.info("New file has last-modified value of {}", remoteLastModified);
            logger.info("Downloading new file from {}", url);
            try (final InputStream in = fun.apply(remoteResource))
            {
                final Path tmpFile = Files.createTempFile(file.getFileName().toString() + "-", ".tmp");
                Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
                Files.createDirectories(file.getParent());
                Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING);
                Files.setLastModifiedTime(file, FileTime.fromMillis(remoteLastModified.toInstant().toEpochMilli()));
            }
        }

        return new AbstractMap.SimpleEntry<>(Collections.max(Arrays.asList(localLastModified, remoteLastModified)), file);
    }

    private Entry<OffsetDateTime, Path> fetch(String alias, String url, final Duration maxAge) throws IOException
    {
        final Entry<OffsetDateTime, InputStream> resource = openConnection(url);
        return downloadIfNewer(alias, url, resource.getKey(), resource.getValue(), maxAge, i -> i);
    }

    @FunctionalInterface
    public interface CheckedFunction<T, R>
    {
        R apply(T t) throws IOException;
    }
}
