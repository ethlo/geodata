package com.ethlo.geodata.repository;

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
import java.util.AbstractMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.MapUtils;
import com.ethlo.geodata.importer.file.JsonIoReader;
import com.ethlo.geodata.model.CountrySummary;
import com.google.common.collect.Range;

@SuppressWarnings("rawtypes")
@Repository
public class GeoRepository
{
    private File baseDirectory;
    
    private static final String LOCATIONS_FILE = "geonames.json";
    private static final String IP_FILE = "geoip.json";
    
    @Value("${data.directory}")
    public void setBaseDirectory(File baseDirectory)
    {
        this.baseDirectory = new File(baseDirectory.getPath().replaceFirst("^~",System.getProperty("user.home")));
    }
    
    public CloseableIterator<Map> locations(Map<String, CountrySummary> countrySummaries)
    {
        return new JsonIoReader<Map>(new File(baseDirectory, LOCATIONS_FILE), Map.class).iterator();
    }
    
    @SuppressWarnings("unchecked")
    public CloseableIterator<Map.Entry<Long, Range<Long>>> ipRanges()
    {
        return new JsonIoReader<Map>(new File(baseDirectory, IP_FILE), Map.class).iterator(m->mapIpRange(m));
    }
    
    public  Map.Entry<Long, Range<Long>> mapIpRange(Map<String, Object> rs)
    {
        final Long id = MapUtils.getLong(rs, "geoname_id");
        final long lower = MapUtils.getLong(rs, "first");
        final long upper = MapUtils.getLong(rs, "last");
        return new AbstractMap.SimpleEntry<>(id, Range.closed(lower, upper));
    }
}
