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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.DataLoadedEvent;
import com.ethlo.geodata.IoUtils;
import com.ethlo.geodata.ProgressListener;
import com.ethlo.geodata.importer.GeonamesImporter;
import com.ethlo.geodata.util.ResourceUtil;

@Component
public class FileGeonamesImporter extends FilePersistentImporter
{
    public static final String FILENAME = "geonames.json";
    
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
    public void importData() throws IOException
    {
        final Map.Entry<Date, File> hierarchyFile = ResourceUtil.fetchResource("geonames_hierarchy", geoNamesHierarchyUrl);
        
        final Map.Entry<Date, File> alternateNamesFile = ResourceUtil.fetchResource("geonames_alternatenames", geoNamesAlternateNamesUrl);
        
        final Map.Entry<Date, File> allCountriesFile = ResourceUtil.fetchResource("geonames", geoNamesAllCountriesUrl);
        
        doUpdate(allCountriesFile.getValue(), alternateNamesFile.getValue(), hierarchyFile.getValue());
    }

    @Override
    public void purge()
    {
        super.delete();
    }

    private void doUpdate(File allCountriesFile, File alternateNamesFile, File hierarchyFile) throws IOException
    {
        final ProgressListener prg = new ProgressListener(IoUtils.lineCount(allCountriesFile), d->publish(new DataLoadedEvent(this, "locations", d)));

        final GeonamesImporter geonamesImporter = new GeonamesImporter.Builder()
            .allCountriesFile(allCountriesFile)
            .alternateNamesFile(alternateNamesFile)
            .onlyHierarchical(false)
            .exclusions(exclusions)
            .hierarchyFile(hierarchyFile)
            .progressListener(prg)
            .build();

        try (final JsonIoWriter<Map> jsonIo = new JsonIoWriter<Map>(getFile(), Map.class))
        {
            geonamesImporter.processFile(jsonIo::write);
        }
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return new Date(Math.max(ResourceUtil.getLastModified(geoNamesAllCountriesUrl).getTime(), ResourceUtil.getLastModified(geoNamesHierarchyUrl).getTime()));
    }
    
    @Override
    protected List<File> getFiles()
    {
        return Arrays.asList(getFile());
    }
}
