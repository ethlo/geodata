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
import java.util.Date;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ethlo.geodata.importer.jdbc.PersistentImporter;
import com.ethlo.geodata.util.ResourceUtil;
import com.google.common.io.Files;

@Component
public class FileGeonamesHierarchyImporter implements PersistentImporter
{
    @Value("${geodata.geonames.source.hierarchy}")
    private String geoNamesHierarchyUrl;

    private File file;
    
    @Override
    public void importData() throws IOException
    {
        final Map.Entry<Date, File> hierarchyFile = ResourceUtil.fetchResource("geonames_hierarchy", geoNamesHierarchyUrl);
        Files.copy(hierarchyFile.getValue(), file);
    }

    @Override
    public void purge()
    {
        file.delete();
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return new Date(ResourceUtil.getLastModified(geoNamesHierarchyUrl).getTime());
    }
}
