package com.ethlo.geodata.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
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
        final URL url = new URL(urlParts[0]);
        if (url.getProtocol().equals("file"))
        {
            String path = urlStr.substring(7);
            if (path.startsWith("~" + File.separator))
            {
                path = System.getProperty("user.home") + path.substring(1);
            }
            final File file = new File(path);
            if (!file.exists())
            {
                throw new IOException("File " + file.getAbsolutePath() + " does not exist");
            }
            return new FileSystemResource(file);
        }
        return new UrlResource(urlParts[0]);
    }

    public static Map.Entry<Date, File> fetchZipEntry(String alias, String urlStr, String zipEntryName) throws IOException
    {
        final Resource resource = openConnection(urlStr);

        return downloadIfNewer(alias, resource, f -> {
            final ZipInputStream zipIn = new ZipInputStream(resource.getInputStream());
            ZipEntry entry = null;
            do
            {
                entry = zipIn.getNextEntry();
            }
            while (entry != null && !entry.getName().endsWith(zipEntryName));
            Assert.notNull(entry, "Zip entry cannot be found: " + zipEntryName);
            return zipIn;
        });
    }

    private static Entry<Date, File> downloadIfNewer(String alias, Resource resource, CheckedFunction<InputStream, InputStream> fun) throws IOException
    {
        final File file = new File(tmpDir, alias + ".txt");
        final Date remoteLastModified = new Date(resource.lastModified());
        final long localLastModified = file.exists() ? file.lastModified() : -2;
        logger.debug("Local file for " + "alias {}" + "\nPath: {}" + "\nExists: {}" + "\nLast-Modified: {}", alias, file.getAbsolutePath(), file.exists(), formatDate(localLastModified));

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

    public static Entry<Date, File> fetch(String alias, String url) throws IOException
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