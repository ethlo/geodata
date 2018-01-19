package com.ethlo.geodata.importer.file;

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
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.DataLoadedEvent;
import com.ethlo.geodata.IoUtils;
import com.ethlo.geodata.ProgressListener;
import com.ethlo.geodata.importer.DataType;
import com.ethlo.geodata.importer.GeonamesImporter;
import com.ethlo.geodata.importer.Operation;

@Component
public class FileGeonamesImporter extends FilePersistentImporter
{
    public static final String FILENAME = "geonames.json";
    
    private static final Logger logger = LoggerFactory.getLogger(FileGeonamesImporter.class);
    
    @Value("${geodata.geonames.source.names}")
    private String geoNamesAllCountriesUrl;
    
    @Value("${geodata.geonames.source.alternatenames}")
    private String geoNamesAlternateNamesUrl;
    
    @Value("${geodata.geonames.source.hierarchy}")
    private String geoNamesHierarchyUrl;
    
    private Set<String> exclusions;
    
    @Value("${geodata.geonames.features.excluded}")
    public void setExclusions(String csv)
    {
        exclusions = StringUtils.commaDelimitedListToSet(csv);
    }
    
    public FileGeonamesImporter(ApplicationEventPublisher publisher)
    {
        super(publisher, FILENAME);
    }
    
    @Override
    public long importData() throws IOException
    {
        final Map.Entry<Date, File> hierarchyFile = fetchResource(DataType.HIERARCHY, geoNamesHierarchyUrl);
        
        final Map.Entry<Date, File> alternateNamesFile = fetchResource(DataType.LOCATION_ALTERNATE_NAMES, geoNamesAlternateNamesUrl);
        
        final Map.Entry<Date, File> allCountriesFile = fetchResource(DataType.LOCATION, geoNamesAllCountriesUrl);
        
        return doUpdate(allCountriesFile.getValue(), alternateNamesFile.getValue(), hierarchyFile.getValue());
    }

    @Override
    public void purge()
    {
        super.delete();
    }

    private long doUpdate(File allCountriesFile, File alternateNamesFile, File hierarchyFile) throws IOException
    {
        logger.info("Counting lines of {}", allCountriesFile);
        final long total = IoUtils.lineCount(allCountriesFile);
        final ProgressListener prg = new ProgressListener(l->publish(new DataLoadedEvent(this, DataType.LOCATION, Operation.IMPORT, l, total)));

        final GeonamesImporter geonamesImporter = new GeonamesImporter.Builder()
            .allCountriesFile(allCountriesFile)
            .alternateNamesFile(alternateNamesFile)
            .onlyHierarchical(false)
            .exclusions(exclusions)
            .hierarchyFile(hierarchyFile)
            .progressListener(prg)
            .build();

        try (final JsonIoWriter<Map> jsonIo = new JsonIoWriter<>(getFile(), Map.class))
        {
            geonamesImporter.processFile(jsonIo::write);
        }
        
        publish(new DataLoadedEvent(this, DataType.LOCATION, Operation.IMPORT, total, total));
        
        return total;
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return new Date(Math.max(getLastModified(geoNamesAllCountriesUrl).getTime(), getLastModified(geoNamesHierarchyUrl).getTime()));
    }
    
    @Override
    protected List<File> getFiles()
    {
        return Arrays.asList(getFile());
    }
}
