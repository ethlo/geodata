package com.ethlo.geodata;

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

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.importer.DataType;
import com.ethlo.geodata.importer.jdbc.JdbcCountryImporter;
import com.ethlo.geodata.importer.jdbc.JdbcGeonamesBoundaryImporter;
import com.ethlo.geodata.importer.jdbc.JdbcGeonamesHierarchyImporter;
import com.ethlo.geodata.importer.jdbc.JdbcGeonamesImporter;
import com.ethlo.geodata.importer.jdbc.JdbcIpLookupImporter;

@Service
public class GeoMetaService
{
    @Autowired
    private JdbcCountryImporter countryImporter;

    @Autowired
    private JdbcIpLookupImporter ipLookupImporter;

    @Autowired
    private JdbcGeonamesImporter geonamesImporter;

    @Autowired
    private JdbcGeonamesBoundaryImporter boundaryImporter;

    @Autowired
    private JdbcGeonamesHierarchyImporter hierarchyImporter;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private long maxDataAgeMillis;

    @Value("${geodata.max-data-age}")
    public void setMaxDataAge(String age)
    {
        final Duration d = Duration.parse("P" + age);
        maxDataAgeMillis = d.toMillis();
    }

    public Optional<Date> getLastModified(DataType alias)
    {
        final String sql = "SELECT last_modified from metadata where alias = :alias";
        return jdbcTemplate.query(sql, Collections.singletonMap("alias", alias.name().toLowerCase()), rs ->
        {
            if (rs.next())
            {
                return Optional.of(rs.getTimestamp("last_modified"));
            }
            return Optional.empty();
        });
    }

    public void setLastModified(DataType type, Date lastModified, final long count)
    {
        final String sql = "REPLACE INTO `metadata` (`alias`, `last_modified`, `entry_count`) VALUES (:alias, :last_modified, :entry_count)";
        final Map<String, Object> params = new TreeMap<>();
        params.put("alias", type.name().toLowerCase());
        params.put("last_modified", lastModified);
        params.put("entry_count", count);
        jdbcTemplate.update(sql, params);
    }

    public void update() throws IOException
    {
        final Date countryTimestamp = countryImporter.lastRemoteModified();
        ifExpired(DataType.COUNTRY, countryTimestamp, () ->
        {
            countryImporter.purge();
            return countryImporter.importData();
        });

        ifExpired(DataType.HIERARCHY, hierarchyImporter.lastRemoteModified(), () ->
        {
            hierarchyImporter.purge();
            return hierarchyImporter.importData();
        });

        ifExpired(DataType.LOCATION, geonamesImporter.lastRemoteModified(), () ->
        {
            geonamesImporter.purge();
            return geonamesImporter.importData();
        });
        
        /*final Date boundariesTimestamp = boundaryImporter.lastRemoteModified();
        if (boundariesTimestamp.getTime() > getLastModified("geoboundaries") + maxDataAgeMillis)
        {
            boundaryImporter.purge();
            boundaryImporter.importData();
            setLastModified("geoboundaries", boundariesTimestamp);
        }
        */

        ifExpired(DataType.IP, ipLookupImporter.lastRemoteModified(), () ->
        {
            ipLookupImporter.purge();
            return ipLookupImporter.importData();
        });
    }

    private void ifExpired(final DataType type, final Date sourceTimestamp, final Supplier<Long> updater)
    {
        final Optional<Date> modified = getLastModified(type);
        if (modified.isEmpty() || sourceTimestamp.getTime() > +maxDataAgeMillis + modified.get().getTime())
        {
            final long count = updater.get();
            setLastModified(type, sourceTimestamp, count);
        }
    }

    public Map<String, Date> getLastModified()
    {
        final Map<String, Date> retVal = new TreeMap<>();
        jdbcTemplate.query("SELECT alias, last_modified FROM metadata", Collections.emptyMap(), (RowMapper<Void>) (rs, rowNum) ->
        {
            retVal.put(rs.getString("alias"), rs.getTimestamp("last_modified"));
            return null;
        });
        return retVal;
    }

    public SourceDataInfoSet getSourceDataInfo()
    {
        SourceDataInfoSet result = new SourceDataInfoSet();
        result.addAll(Arrays.stream(DataType.values()).map(type ->
        {
            final Date lastModified = getLastModified(type).orElse(null);
            return new SourceDataInfo(type, 0, lastModified);
        }).collect(Collectors.toList()));
        return result;
    }
}
