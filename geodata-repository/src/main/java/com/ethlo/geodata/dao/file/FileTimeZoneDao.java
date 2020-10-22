package com.ethlo.geodata.dao.file;

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
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.TimeZoneDao;
import com.ethlo.geodata.util.JsonUtil;

@Repository
public class FileTimeZoneDao implements TimeZoneDao
{
    public static final String TIMEZONES_FILE = "timezones.json";
    private final Path basePath;

    public FileTimeZoneDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.basePath = basePath;
    }

    public Map<Integer, String> load()
    {
        return JsonUtil.read(basePath.resolve(TIMEZONES_FILE), Map.class);
    }

    @Override
    public void save(final Map<String, Integer> timezones)
    {
        JsonUtil.write(basePath.resolve(TIMEZONES_FILE), timezones);
    }
}
