package com.ethlo.geodata;

import java.io.File;
import java.io.FileNotFoundException;
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.ethlo.geodata.importer.DataType;
import com.ethlo.geodata.importer.file.FileCountryImporter;
import com.ethlo.geodata.importer.file.FileGeonamesBoundaryImporter;
import com.ethlo.geodata.importer.file.FileGeonamesHierarchyImporter;
import com.ethlo.geodata.importer.file.FileGeonamesImporter;
import com.ethlo.geodata.importer.file.FileIpLookupImporter;
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
    
    @Value("${geodata.max-data-age}")
    public void setMaxDataAge(String age)
    {
    	final Duration d = Duration.parse("P" + age);
    	maxDataAgeMillis = d.toMillis();
    }
    
    public long getLastModified(DataType dataType)
    {
        final SourceDataInfoSet map = read();
        final SourceDataInfo info = map.get(dataType);
        return info != null ? info.getLastModified().getTime() : -1;
    }
    
    private SourceDataInfoSet read()
    {
        try (final Reader reader = new FileReader(new File(baseDirectory, FILENAME)))
        {
            return mapper.readValue(reader, SourceDataInfoSet.class);
        }
        catch (FileNotFoundException exc)
        {
            return new SourceDataInfoSet();
        }
        catch (IOException exc)
        {
            throw new RuntimeException(exc);            
        }
    }
    
    public void setSourceDataInfo(DataType type, Date lastModified, long count) throws IOException
    {
        synchronized (mapper)
        {
            final SourceDataInfoSet map = read();
            map.add(new SourceDataInfo(type, count, lastModified));
            write(map);
        }
    }

    private void write(SourceDataInfoSet sourceInfo) throws IOException
    {
        try (final Writer writer = new FileWriter(new File(baseDirectory, FILENAME)))
        {
            mapper.writeValue(writer, sourceInfo);
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
        if (! boundaryImporter.allFilesExists() || boundariesTimestamp.getTime() > getLastModified(DataType.MBR) + maxDataAgeMillis)
        {
            boundaryImporter.purge();
            final long imported = boundaryImporter.importData();
            setSourceDataInfo(DataType.MBR, boundariesTimestamp, imported);
        }
        
        final Date countryTimestamp = countryImporter.lastRemoteModified();
        if (! countryImporter.allFilesExists() || countryTimestamp.getTime() > getLastModified(DataType.COUNTRY) + maxDataAgeMillis)
        {
            countryImporter.purge();
            final long imported = countryImporter.importData();
            setSourceDataInfo(DataType.COUNTRY, countryTimestamp, imported);
        }
        
        final Date geonamesHierarchyTimestamp = hierarchyImporter.lastRemoteModified();
        if (! hierarchyImporter.allFilesExists() || geonamesHierarchyTimestamp.getTime() > getLastModified(DataType.HIERARCHY) + maxDataAgeMillis)
        {
            hierarchyImporter.purge();
            final long imported = hierarchyImporter.importData();
            setSourceDataInfo(DataType.HIERARCHY, geonamesHierarchyTimestamp, imported);
        }
        
        final Date geonamesTimestamp = geonamesImporter.lastRemoteModified();
        if (! geonamesImporter.allFilesExists() || geonamesTimestamp.getTime() > getLastModified(DataType.LOCATION) + maxDataAgeMillis)
        {
            geonamesImporter.purge();
            final long imported = geonamesImporter.importData();
            setSourceDataInfo(DataType.LOCATION, geonamesTimestamp, imported);
        }
        
        final Date ipTimestamp = ipLookupImporter.lastRemoteModified();
        if (! ipLookupImporter.allFilesExists() || ipTimestamp.getTime() > getLastModified(DataType.IP) + maxDataAgeMillis)
        {
            ipLookupImporter.purge();
            final long imported = ipLookupImporter.importData();
            setSourceDataInfo(DataType.IP, ipTimestamp, imported);
        }
    }

    public SourceDataInfoSet getSourceDataInfo()
    {
        return read();
    }
}
