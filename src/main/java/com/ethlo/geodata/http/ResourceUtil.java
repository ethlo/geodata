package com.ethlo.geodata.http;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.util.Assert;

import com.google.common.base.Throwables;

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
            formatDate(localLastModified));
        
        if (remoteLastModified > localLastModified)
        {
            logger.info("New file has last-modified value of {}", formatDate(localLastModified));
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

    private static LocalDateTime formatDate(long localLastModified)
    {
        return LocalDateTime.ofEpochSecond(localLastModified/1_000, 0, ZoneOffset.UTC);
    }

    public static void dumpInsertStatements(NamedParameterJdbcOperations jdbcTemplate, String tableName, File file) throws IOException, SQLException
    {
        final String fileName = file != null ? file.getAbsolutePath() : File.createTempFile("geodata_" + tableName + "_", ".sql").getAbsolutePath();
        logger.info("Dumping to {}", fileName);
        final String dumpSqlStatement = "SCRIPT TABLE " + tableName;
        
        try (final Writer out = new BufferedWriter(new FileWriter(fileName)))
        {
            jdbcTemplate.execute(dumpSqlStatement, new PreparedStatementCallback<Void>()
            {
                @Override
                public Void doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException
                {
                    final ResultSet rs = ps.executeQuery();
                    while (rs.next())
                    {
                        final String stmt = rs.getString(1);
                        if (stmt.startsWith("INSERT"))
                        {
                            try
                            {
                                out.write(stmt);
                                out.write("\n");
                            }
                            catch (IOException exc)
                            {
                                throw Throwables.propagate(exc);
                            }
                        }
                    }
                    return null;
                }
            });
        };
    }        
}