package com.ethlo.geodata.importer.jdbc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Date;

import org.springframework.stereotype.Service;

@Service
public class GeoMetaService
{
    private final File file = new File(System.getProperty("user.home"), ".geodata.lastmodified");
    
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
}
