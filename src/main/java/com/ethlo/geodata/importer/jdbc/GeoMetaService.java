package com.ethlo.geodata.importer.jdbc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.util.ResourceUtil;

@Service
public class GeoMetaService
{
    @Autowired
    private JdbcIpLookupImporter ipLookupImporter;
    
    @Autowired
    private JdbcGeonamesImporter geonamesImporter;
    
    @Autowired
    private JdbcGeonamesBoundaryImporter boundaryImporter;
    
    private File file = new File(System.getProperty("user.home"), ".geodata.lastmodified");
    
    private static final Logger logger = LoggerFactory.getLogger(GeoMetaService.class);
    
    public Date getLastModified() throws IOException
    {
        if (file.exists())
        {
            final String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return new Date(Long.parseLong(content));
        }
        return new Date(0);
    }
    
    public void setLastModified(Date lastModified) throws IOException
    {
        Files.write(file.toPath(), Long.toString(lastModified.getTime()).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
    }

    public void update() throws IOException
    {
        // Check last modified
        final Date geonamesTimestamp = geonamesImporter.lastRemoteModified();
        final Date boundariesTimestamp = boundaryImporter.lastRemoteModified();
        final Date ipTimestamp = ipLookupImporter.lastRemoteModified();
        final Date latestRemote = ResourceUtil.latest(geonamesTimestamp, boundariesTimestamp, ipTimestamp);
        if (latestRemote.after(getLastModified()))
        {
            logger.info("Updating data. Latest remote resource was {}", latestRemote);
            
            logger.info("Purging all old data");
            ipLookupImporter.purge();
            boundaryImporter.purge();
            geonamesImporter.purge();
        
            logger.info("Importing new data");
            geonamesImporter.importData();
            boundaryImporter.importData();
            ipLookupImporter.importData();
            
            setLastModified(latestRemote);
        }
    }
}
