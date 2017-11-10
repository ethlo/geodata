package com.ethlo.geodata;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.ethlo.geodata.importer.file.FileCountryImporter;
import com.ethlo.geodata.importer.file.FileGeonamesBoundaryImporter;
import com.ethlo.geodata.importer.file.FileGeonamesHierarchyImporter;
import com.ethlo.geodata.importer.file.FileGeonamesImporter;
import com.ethlo.geodata.importer.file.FileIpLookupImporter;
import com.ethlo.time.FastInternetDateTimeUtil;
import com.ethlo.time.InternetDateTimeUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GeoMetaService
{
    @Autowired
    private FileCountryImporter countryImporter;
    
    @Autowired
    private FileIpLookupImporter ipLookupImporter;
    
    @Autowired
    private FileGeonamesImporter geonamesImporter;
    
    @Autowired
    private FileGeonamesBoundaryImporter boundaryImporter;
    
    @Autowired
    private FileGeonamesHierarchyImporter hierarchyImporter;
    
    @Value("${data.directory}")
    public void setBaseDirectory(File baseDirectory)
    {
        this.baseDirectory = new File(baseDirectory.getPath().replaceFirst("^~",System.getProperty("user.home")));
    }
    
    private File baseDirectory;
    
    private static final String FILENAME = "meta.json";
    
    private long maxDataAgeMillis;
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    private static final InternetDateTimeUtil itu = new FastInternetDateTimeUtil();
    
    @Value("${geodata.max-data-age}")
    public void setMaxDataAge(String age)
    {
    	final Duration d = Duration.parse("P" + age);
    	maxDataAgeMillis = d.toMillis();
    }
    
    public long getLastModified(String alias)
    {
        final Map<String, String> map = read();
        final String timestamp = map.get(alias);
        return timestamp != null ? itu.parse(timestamp).toInstant().toEpochMilli() : 0;
    }
    
    public Map<String, Date> getLastModified()
    {
        final Map<String, String> map = read();
        return map.entrySet()
             .stream()
             .collect(Collectors.toMap(Map.Entry::getKey,e->new Date(itu.parse(e.getValue()).toInstant().toEpochMilli())));
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, String> read()
    {
        try (final Reader reader = new FileReader(new File(baseDirectory, FILENAME)))
        {
            return mapper.readValue(reader, Map.class);
        }
        catch (IOException exc)
        {
            return new TreeMap<>();
        }
    }
    
    public void setLastModified(String alias, Date lastModified) throws IOException
    {
        synchronized (mapper)
        {
            final Map<String, String> map = read();
            map.put(alias, itu.format(lastModified, "UTC"));
            write(map);
        }
    }

    private void write(Map<String, String> map) throws IOException
    {
        try (final Writer writer = new FileWriter(new File(baseDirectory, FILENAME)))
        {
            mapper.writeValue(writer, map);
        }
    }

    private void ensureBaseDirectory()
    {
        if (! baseDirectory.exists())
        {
            Assert.isTrue(baseDirectory.mkdirs(), "Could not create directory " + baseDirectory.getAbsolutePath());
        }
    }
    
    public void update() throws IOException
    {
        ensureBaseDirectory();
        
        final Date boundariesTimestamp = boundaryImporter.lastRemoteModified();
        if (! boundaryImporter.allFilesExists() || boundariesTimestamp.getTime() > getLastModified("geoboundaries") + maxDataAgeMillis)
        {
            boundaryImporter.purge();
            boundaryImporter.importData();
            setLastModified("geoboundaries", boundariesTimestamp);
        }
        
        final Date countryTimestamp = countryImporter.lastRemoteModified();
        if (! countryImporter.allFilesExists() || countryTimestamp.getTime() > getLastModified("geonames_country") + maxDataAgeMillis)
        {
            countryImporter.purge();
            countryImporter.importData();
            setLastModified("geonames_country", countryTimestamp);
        }
        
        final Date geonamesHierarchyTimestamp = hierarchyImporter.lastRemoteModified();
        if (! hierarchyImporter.allFilesExists() || geonamesHierarchyTimestamp.getTime() > getLastModified("geonames_hierarchy") + maxDataAgeMillis)
        {
            hierarchyImporter.purge();
            hierarchyImporter.importData();
            setLastModified("geonames_hierarchy", geonamesHierarchyTimestamp);
        }
        
        final Date geonamesTimestamp = geonamesImporter.lastRemoteModified();
        if (! geonamesImporter.allFilesExists() || geonamesTimestamp.getTime() > getLastModified("geonames") + maxDataAgeMillis)
        {
            geonamesImporter.purge();
            geonamesImporter.importData();
            setLastModified("geonames", geonamesTimestamp);
        }
        
        final Date ipTimestamp = ipLookupImporter.lastRemoteModified();
        if (! ipLookupImporter.allFilesExists() || ipTimestamp.getTime() > getLastModified("geoip") + maxDataAgeMillis)
        {
            ipLookupImporter.purge();
            ipLookupImporter.importData();
            setLastModified("geoip", ipTimestamp);
        }
    }
}
