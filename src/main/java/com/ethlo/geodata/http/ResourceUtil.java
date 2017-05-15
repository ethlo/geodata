package com.ethlo.geodata.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

public class ResourceUtil
{
    private static final Logger logger = LoggerFactory.getLogger(ResourceUtil.class);
    
    public static File fetchZipEntry(String alias, String urlStr, String zipEntryName) throws IOException
    {
        final URL url = new URL(urlStr);
        final URLConnection connection = url.openConnection();
        final long remoteLastModified = connection.getLastModified();
        final String tmpDir = System.getProperty("java.io.tmpdir");
        final File file = new File(tmpDir, alias + ".data");
        final long localLastModified = file.exists() ? file.lastModified() : -2;
        logger.info("Local file for "
            + "alias {}"
            + "\nPath: {}"
            + "\nExists: {}"
            + "\nLast-Modified: {}", 
            alias, 
            file.getAbsolutePath(), 
            file.exists(), 
            LocalDateTime.ofEpochSecond(localLastModified/1_000, 0, ZoneOffset.UTC));
        
        if (remoteLastModified > localLastModified)
        {
            logger.info("Downloading new file from {}", url);
            try (final ZipInputStream zipIn = new ZipInputStream(url.openStream());)
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
                    Streams.copy(zipIn, fos, true);
                }
                file.setLastModified(remoteLastModified);
            }
        }
        else
        {
            logger.info("Using cached file for {}", url);
        }
        return file;
    }
}
