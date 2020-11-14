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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.Assert;

public class ResourceUtil
{
    private static final Logger logger = LoggerFactory.getLogger(ResourceUtil.class);
    private static final String tmpDir = System.getProperty("java.io.tmpdir");

    public static Date getLastModified(String urlStr) throws IOException
    {
        final Map.Entry<Date, InputStream> connection = openConnection(urlStr);
        return connection.getKey();
    }

    private static Entry<Date, InputStream> openConnection(String urlStr) throws IOException
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
            return new AbstractMap.SimpleEntry<>(new Date(fileRes.lastModified()), fileRes.getInputStream());
        }
        else if (urlStr.startsWith("classpath:"))
        {
            final ClassPathResource classRes = new ClassPathResource(urlParts[0].substring(10));
            return new AbstractMap.SimpleEntry<>(new Date(classRes.lastModified()), classRes.getInputStream());
        }

        final HttpClient client = HttpClient.newHttpClient();
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlParts[0]))
                .build();

        try
        {
            final HttpResponse<InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200)
            {
                throw new IOException("HTTP status " + resp.statusCode() + " returned for URL " + request.uri());
            }
            final Date lastModified = resp.headers().firstValue("Last-Modified").map(d -> ZonedDateTime.parse(d, DateTimeFormatter.RFC_1123_DATE_TIME)).map(ZonedDateTime::toInstant).map(Date::from).orElseThrow();
            return new AbstractMap.SimpleEntry<>(lastModified, resp.body());
        }
        catch (InterruptedException e)
        {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    public static Map.Entry<Date, File> fetchResource(String alias, Duration maxAge, String urlStr) throws IOException
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

    private static Map.Entry<Date, File> fetchZip(String alias, String url, Duration maxAge, String zipEntry) throws IOException
    {
        final Entry<Date, InputStream> resource = openConnection(url);
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

    private static Entry<Date, File> downloadIfNewer(String alias, String url, Date remoteLastModified, InputStream remoteResource, Duration maxAge, CheckedFunction<InputStream, InputStream> fun) throws IOException
    {
        final File file = new File(tmpDir, alias + "_" + url.hashCode());
        final long localLastModified = file.exists() ? file.lastModified() : -2;

        logger.info("Local file for " + "alias {}: Path: {}" + " - Exists: {}" + " - Last-Modified: {}", alias, file.getAbsolutePath(), file.exists(), formatDate(localLastModified));

        if (remoteLastModified.getTime() > localLastModified + maxAge.toMillis())
        {
            logger.info("New file has last-modified value of {}", formatDate(remoteLastModified.getTime()));
            logger.info("Downloading new file from {}", url);
            try (final InputStream in = fun.apply(remoteResource))
            {
                final Path tmpFile = Files.createTempFile(file.getName() + "-", ".tmp");
                Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmpFile, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                file.setLastModified(remoteLastModified.getTime());
            }
        }

        return new AbstractMap.SimpleEntry<>(new Date(Math.max(localLastModified, remoteLastModified.getTime())), file);
    }

    private static LocalDateTime formatDate(long timestamp)
    {
        return LocalDateTime.ofEpochSecond(timestamp / 1_000, 0, ZoneOffset.UTC);
    }

    private static Entry<Date, File> fetch(String alias, String url, final Duration maxAge) throws IOException
    {
        final Entry<Date, InputStream> resource = openConnection(url);
        return downloadIfNewer(alias, url, resource.getKey(), resource.getValue(), maxAge, i -> i);
    }

    @FunctionalInterface
    public interface CheckedFunction<T, R>
    {
        R apply(T t) throws IOException;
    }
}
