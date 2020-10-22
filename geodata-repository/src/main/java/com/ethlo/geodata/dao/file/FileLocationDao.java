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
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.model.RawLocation;
import com.ethlo.geodata.util.SerializationUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;

@Repository
public class FileLocationDao implements LocationDao
{
    public static final String LOCATION_FILE = "locations.data";
    private final Path basePath;

    public FileLocationDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.basePath = basePath;
    }

    @Override
    public Map<Integer, RawLocation> load()
    {
        final Map<Integer, RawLocation> locations = new Int2ObjectLinkedOpenHashMap<>(100_000);
        try (final CloseableIterator<RawLocation> locationIter = SerializationUtil.read(basePath.resolve(LOCATION_FILE), RawLocation::new))
        {
            while (locationIter.hasNext())
            {
                final RawLocation l = locationIter.next();
                locations.put(l.getId(), l);
            }
        }
        return locations;
    }
}
