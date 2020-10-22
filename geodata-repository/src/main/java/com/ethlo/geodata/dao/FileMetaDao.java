package com.ethlo.geodata.dao;

/*-
 * #%L
 * geodata-common
 * %%
 * Copyright (C) 2017 - 2020 Morten Haraldsen (ethlo)
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

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.SourceDataInfoSet;
import com.ethlo.geodata.util.JsonUtil;

@Repository
public class FileMetaDao implements MetaDao
{
    private final String FILE = "metadata.json";
    private final Path basePath;

    public FileMetaDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.basePath = basePath;
    }

    @Override
    public SourceDataInfoSet load()
    {
        return JsonUtil.read(basePath.resolve(FILE), SourceDataInfoSet.class);
    }

    @Override
    public void save(final SourceDataInfoSet dataInfoSet)
    {
        JsonUtil.write(basePath.resolve(FILE), dataInfoSet);
    }
}
