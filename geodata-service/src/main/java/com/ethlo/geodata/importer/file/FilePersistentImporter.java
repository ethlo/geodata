package com.ethlo.geodata.importer.file;

/*-
 * #%L
 * Geodata service
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
import java.nio.file.Files;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;

import com.ethlo.geodata.DataLoadedEvent;
import com.ethlo.geodata.importer.PersistentImporter;

public abstract class FilePersistentImporter implements PersistentImporter
{
    @Value("${data.directory}")
    public void setBaseDirectory(File baseDirectory)
    {
        this.baseDirectory = new File(baseDirectory.getPath().replaceFirst("^~",System.getProperty("user.home")));
    }

    private File baseDirectory;
    private ApplicationEventPublisher publisher;
    private String name;
    
    public FilePersistentImporter(ApplicationEventPublisher publisher, String name)
    {
        this.publisher = publisher;
        this.name = name;
    }
    
    protected File getFile()
    {
        return new File(baseDirectory, name);
    }
    
    protected void publish (DataLoadedEvent event)
    {
        this.publisher.publishEvent(event);
    }
    
    protected void delete()
    {
        if (getFile().exists())
        {
            final boolean deleted = getFile().delete();
            if (! deleted)
            {
                throw new DataAccessResourceFailureException("Could not delete " + getFile());
            }
        }
    }

    /**
     * Returns a list of the files this importer produces
     * @return a list of the files this importer produces
     */
    protected abstract List<File> getFiles();
    
    public boolean allFilesExists()
    {
        return getFiles().size() == getFiles().stream().filter(File::exists).count();
    }
}
