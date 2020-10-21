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
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.importer.GeoFabrikBoundaryLoader;
import com.ethlo.geodata.importer.GeonamesSource;
import com.ethlo.geodata.importer.jdbc.JdbcCountryImporter;
import com.ethlo.geodata.importer.jdbc.JdbcGeonamesBoundaryImporter;
import com.ethlo.geodata.importer.jdbc.JdbcGeonamesImporter;
import com.ethlo.geodata.importer.jdbc.JdbcIpLookupImporter;

@Service
public class GeoMetaService
{
    private final GeoFabrikBoundaryLoader geoFabrikBoundaryLoader = new GeoFabrikBoundaryLoader();

    @Autowired
    private JdbcCountryImporter countryImporter;
    @Autowired
    private JdbcIpLookupImporter ipLookupImporter;
    @Autowired
    private JdbcGeonamesImporter geonamesImporter;
    @Autowired
    private JdbcGeonamesBoundaryImporter geonamesBoundaryImporter;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${geodata.max-data-age}")
    private Duration maxDataAge;

    public Optional<Date> getLastModified(GeonamesSource alias)
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

    public void setStatus(GeonamesSource type, Date lastModified, final long count)
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
        ifExpired(GeonamesSource.COUNTRY, countryTimestamp, () ->
        {
            countryImporter.purgeData();
            return countryImporter.importData();
        });

        ifExpired(GeonamesSource.LOCATION, geonamesImporter.lastRemoteModified(), () ->
        {
            geonamesImporter.purgeData();
            return geonamesImporter.importData();
        });

        //ifExpired(GeonamesSource.IP, ipLookupImporter.lastRemoteModified(), () ->
        //{
            ipLookupImporter.purgeData();
            ipLookupImporter.importData();
        //});

        /*final Date boundariesTimestamp = boundaryImporter.lastRemoteModified();
        if (boundariesTimestamp.getTime() > getLastModified("geoboundaries") + maxDataAgeMillis)
        {
            boundaryImporter.purge();
            boundaryImporter.importData();
            setLastModified("geoboundaries", boundariesTimestamp);
        }
        */
    }

    private void ifExpired(final GeonamesSource type, final Date sourceTimestamp, final Supplier<Long> updater)
    {
        final Optional<Date> localDataModifiedAt = getLastModified(type);
        if (localDataModifiedAt.isEmpty() || sourceTimestamp.getTime() > localDataModifiedAt.get().getTime() + maxDataAge.toMillis())
        {
            final long count = updater.get();
            setStatus(type, sourceTimestamp, count);
        }
    }

    public SourceDataInfoSet getSourceDataInfo()
    {
        final SourceDataInfoSet result = new SourceDataInfoSet();
        result.addAll(jdbcTemplate.query("SELECT alias, entry_count, last_modified FROM metadata", Collections.emptyMap(), (rs, rowNum) ->
                new SourceDataInfo(GeonamesSource.valueOf(rs.getString("alias").toUpperCase()), rs.getInt("entry_count"), rs.getDate("last_modified"))));
        return result;
    }
}
