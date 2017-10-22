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

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.importer.file.JsonIoReader;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountrySummary;
import com.ethlo.geodata.model.GeoLocation;
import com.google.common.collect.Range;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;

@Repository
public class GeoRepository
{
    @Value("${data.directory}")
    private File baseDirectory;
    
    private static final String LOCATIONS_FILE = "geonames.json";
    private static final String IP_FILE = "geoip.json";
    
    public CloseableIterator<GeoLocation> locations()
    {
        return new JsonIoReader<Map>(new File(baseDirectory, LOCATIONS_FILE), Map.class)
            .iterator(m->mapLocation(new GeoLocation(), m));
    }
    
    public CloseableIterator<Map.Entry<Long, Range<Long>>> ipRanges()
    {
        return new JsonIoReader<Map>(new File(baseDirectory, IP_FILE), Map.class)
            .iterator(m->mapIpRange(m));
    }
    
    public  Map.Entry<Long, Range<Long>> mapIpRange(Map<String, Object> rs)
    {
        final Long id = MapUtils.getLong(rs, "geoname_id");
        final long lower = MapUtils.getLong(rs, "first");
        final long upper = MapUtils.getLong(rs, "last");
        return new AbstractMap.SimpleEntry<>(id, Range.closed(lower, upper));
    }
    
    private <T extends GeoLocation> T mapLocation(T t, Map<String, Object> rs)
    {
        final Long parentId = MapUtils.getLong(rs, "parent_id") != null ? MapUtils.getLong(rs, "parent_id") : null;
        final String countryCode = MapUtils.getString(rs, "country_code");

        // TODO: Implement me
        final Country country = null;
        final CountrySummary countrySummary = country != null ? country.toSummary(countryCode) : null;
        
        t
            .setId(MapUtils.getLong(rs, "id"))
            .setName(MapUtils.getString(rs, "name"))
            .setFeatureCode(MapUtils.getString(rs, "feature_code"))
            .setFeatureClass(MapUtils.getString(rs, "feature_class"))
            .setPopulation(MapUtils.getLong(rs, "population"))
            .setCoordinates(Coordinates.from(MapUtils.getDouble(rs, "lat"), MapUtils.getDouble(rs, "lng")))
            .setParentLocationId(parentId)
            .setCountry(countrySummary);
        return t;
    }
    
    public Country mapRow(Map<String, Object> rs)
    {
        final GeoLocation location = new GeoLocation();
        mapLocation(location, rs);            
        final Country c = Country.from(location);
        c.setCountry(c.toSummary(MapUtils.getString(rs, "iso")));
        return c;
    } 

}
