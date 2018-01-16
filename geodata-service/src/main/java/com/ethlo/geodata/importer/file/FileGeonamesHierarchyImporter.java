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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.ethlo.geodata.IoUtils;
import com.ethlo.geodata.importer.DataType;
import com.google.common.io.Files;

@Component
public class FileGeonamesHierarchyImporter extends FilePersistentImporter
{
    @Value("${geodata.geonames.source.hierarchy}")
    private String geoNamesHierarchyUrl;

    public FileGeonamesHierarchyImporter(ApplicationEventPublisher publisher)
    {
        super(publisher, "hierarchy");
    }
    
    @Override
    public long importData() throws IOException
    {
        final Map.Entry<Date, File> hierarchyFile = fetchResource(DataType.HIERARCHY, geoNamesHierarchyUrl);
        Files.copy(hierarchyFile.getValue(), getFile());
        return IoUtils.lineCount(getFile());
    }

    @Override
    public void purge()
    {
        super.delete();
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return new Date(getLastModified(geoNamesHierarchyUrl).getTime());
    }
    
    @Override
    protected List<File> getFiles()
    {
        return Arrays.asList(getFile());
    }
}
