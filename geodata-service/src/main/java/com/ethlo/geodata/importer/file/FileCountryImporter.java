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

import com.ethlo.geodata.importer.CountryImporter;
import com.ethlo.geodata.util.ResourceUtil;

@Component
public class FileCountryImporter extends FilePersistentImporter
{
    public static final String FILENAME = "geocountries.json";
    
    @Value("${geodata.geonames.source.country}")
    private String url;
    
    public FileCountryImporter()
    {
        super(FILENAME);
    }

    @Override
    public void importData() throws IOException
    {
        final Map.Entry<Date, File> countryFile = ResourceUtil.fetchResource("geocountry", url);
        
        final CountryImporter importer = new CountryImporter(countryFile.getValue());
        try (final JsonIoWriter<Map> jsonIo = new JsonIoWriter<Map>(getFile(), Map.class))
        {
            importer.processFile(map->jsonIo.write(map));
        }
    }

    @Override
    public void purge() throws IOException
    {
        super.delete();
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return ResourceUtil.getLastModified(url);
    }
}
