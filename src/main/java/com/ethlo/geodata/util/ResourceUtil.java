package com.ethlo.geodata.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.AbstractMap;
import java.util.Date;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.util.Assert;

public class ResourceUtil
{
    private static final Logger logger = LoggerFactory.getLogger(ResourceUtil.class);
    
    public static Date getLastModified(String urlStr) throws IOException
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
            if (! file.exists())
            {
                throw new IOException("File " + file.getAbsolutePath() + " does not exist");
            }
            return new Date(file.lastModified());
        }
        final URLConnection connection = url.openConnection();
        return new Date(connection.getLastModified());
    }
    
    public static Map.Entry<Date, File> fetchZipEntry(String alias, String urlStr, String zipEntryName) throws IOException
    {
        final URL url = new URL(urlStr);
        final Date remoteLastModified = getLastModified(urlStr);
        final String tmpDir = System.getProperty("java.io.tmpdir");
        final File file = new File(tmpDir, alias + ".txt");
        final long localLastModified = file.exists() ? file.lastModified() : -2;
        logger.debug("Local file for "
            + "alias {}"
            + "\nPath: {}"
            + "\nExists: {}"
            + "\nLast-Modified: {}", 
            alias, 
            file.getAbsolutePath(), 
            file.exists(), 
            formatDate(localLastModified));
        
        if (remoteLastModified.getTime() > localLastModified)
        {
            logger.info("New file has last-modified value of {}", formatDate(remoteLastModified.getTime()));
            logger.info("Downloading new file from {}", url);
            
            try (final ZipInputStream zipIn = new ZipInputStream(getInputStream(url));)
            {
                ZipEntry entry = null;
                
                do
                {
                    entry = zipIn.getNextEntry();
                } 
                while(entry != null && !entry.getName().endsWith(zipEntryName));
                
                Assert.notNull(entry, "Zip entry cannot be found: " + zipEntryName);
                
                try(FileOutputStream fos = new FileOutputStream(file))
                {
                    IOUtils.copy(zipIn, fos);
                }
                file.setLastModified(remoteLastModified.getTime());
            }
        }
        else
        {
            logger.info("Using cached file for {}", url);
        }
        return new AbstractMap.SimpleEntry<>(new Date(Math.max(localLastModified, remoteLastModified.getTime())), file);
    }

    private static InputStream getInputStream(URL url) throws IOException
    {
        if (url.getProtocol().equals("file"))
        {
            String path = url.toExternalForm().substring(7);
            if (path.startsWith("~" + File.separator))
            {
                path = System.getProperty("user.home") + path.substring(1);
            }
            final File file = new File(path);
            if (! file.exists())
            {
                throw new IOException("File " + file.getAbsolutePath() + " does not exist");
            }
            return new FileInputStream(file);
        }
        return url.openStream();
    }

    private static LocalDateTime formatDate(long timestamp)
    {
        return LocalDateTime.ofEpochSecond(timestamp/1_000, 0, ZoneOffset.UTC);
    }
}