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
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.Assert;

public class ResourceUtil
{
    private static final Logger logger = LoggerFactory.getLogger(ResourceUtil.class);
    private static final String tmpDir = System.getProperty("java.io.tmpdir");

    public static Date getLastModified(String urlStr) throws IOException
    {
        final Resource connection = openConnection(urlStr);
        final long lastModified = connection.lastModified();
        if (lastModified == 0)
        {
            throw new IOException("No value for Last-Modified for URL " + urlStr);
        }
        return new Date(lastModified);
    }

    private static Resource openConnection(String urlStr) throws MalformedURLException, IOException
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
            return new FileSystemResource(file);
        }
        else if (urlStr.startsWith("classpath:"))
        {
            return new ClassPathResource(urlParts[0].substring(10));
        }

        return new UrlResource(urlParts[0]);
    }

    public static Map.Entry<Date, File> fetchResource(String alias, String urlStr) throws IOException
    {
        final String[] urlParts = StringUtils.split(urlStr, "|");
        if (urlParts[0].endsWith("zip"))
        {
            return fetchZip(alias, urlParts[0], urlParts[1]);
        }
        else
        {
            return fetch(alias, urlStr);
        }
    }

    private static Map.Entry<Date, File> fetchZip(String alias, String url, String zipEntry) throws MalformedURLException, IOException
    {
        final Resource resource = openConnection(url);
        return downloadIfNewer(alias, resource, f ->
        {
            final ZipInputStream zipIn = new ZipInputStream(resource.getInputStream());
            ZipEntry entry = null;
            do
            {
                entry = zipIn.getNextEntry();
            }
            while (entry != null && !entry.getName().endsWith(zipEntry));
            Assert.notNull(entry, "Zip entry cannot be found: " + zipEntry);
            return zipIn;
        });
    }

    private static Entry<Date, File> downloadIfNewer(String alias, Resource resource, CheckedFunction<InputStream, InputStream> fun) throws IOException
    {
        final File file = new File(tmpDir, alias + resource.getURL().hashCode() + ".txt");
        final Date remoteLastModified = new Date(resource.lastModified());
        final long localLastModified = file.exists() ? file.lastModified() : -2;
        logger.info("Local file for " + "alias {}" + "\nPath: {}" + "\nExists: {}" + "\nLast-Modified: {}", alias, file.getAbsolutePath(), file.exists(), formatDate(localLastModified));

        if (remoteLastModified.getTime() > localLastModified)
        {
            logger.info("New file has last-modified value of {}", formatDate(remoteLastModified.getTime()));
            logger.info("Downloading new file from {}", resource.getURL());
            try (final InputStream in = fun.apply(resource.getInputStream()))
            {
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                file.setLastModified(remoteLastModified.getTime());
            }
        }

        return new AbstractMap.SimpleEntry<>(new Date(Math.max(localLastModified, remoteLastModified.getTime())), file);
    }

    private static LocalDateTime formatDate(long timestamp)
    {
        return LocalDateTime.ofEpochSecond(timestamp / 1_000, 0, ZoneOffset.UTC);
    }

    private static Entry<Date, File> fetch(String alias, String url) throws IOException
    {
        final Resource resource = openConnection(url);
        return downloadIfNewer(alias, resource, in -> in);
    }

    @FunctionalInterface
    public interface CheckedFunction<T, R>
    {
        R apply(T t) throws IOException;
    }
}
