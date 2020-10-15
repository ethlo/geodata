package com.ethlo.geodata.importer.jdbc;

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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.locationtech.jts.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.importer.GeonamesImporter;
import com.ethlo.geodata.util.ResourceUtil;

@Component
public class JdbcGeonamesImporter implements PersistentImporter
{
    private static final Logger logger = LoggerFactory.getLogger(JdbcGeonamesImporter.class);

    private final TransactionTemplate txnTemplate;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${geodata.geonames.source.names}")
    private String geoNamesAllCountriesUrl;

    @Value("${geodata.geonames.source.alternatenames}")
    private String geoNamesAlternateNamesUrl;

    @Value("${geodata.geonames.source.hierarchy}")
    private String geoNamesHierarchyUrl;
    private Set<String> exclusions;

    public JdbcGeonamesImporter(final TransactionTemplate txnTemplate, final NamedParameterJdbcTemplate jdbcTemplate)
    {
        this.txnTemplate = txnTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, ?>[] newArray(int n)
    {
        return new Map[n];
    }

    @Value("${geodata.geonames.features.excluded}")
    public void setExclusions(String csv)
    {
        exclusions = StringUtils.commaDelimitedListToSet(csv);
    }

    @Override
    public void importData() throws IOException, SQLException
    {
        final Map.Entry<Date, File> hierarchyFile = ResourceUtil.fetchResource("geonames_hierarchy", geoNamesHierarchyUrl);

        final Map.Entry<Date, File> alternateNamesFile = ResourceUtil.fetchResource("geonames_alternatenames", geoNamesAlternateNamesUrl);

        final Map.Entry<Date, File> allCountriesFile = ResourceUtil.fetchResource("geonames", geoNamesAllCountriesUrl);

        doUpdate(allCountriesFile.getValue(), alternateNamesFile.getValue(), hierarchyFile.getValue());
    }

    @Override
    public void purge()
    {
        jdbcTemplate.update("DELETE FROM geonames", Collections.emptyMap());
    }

    private void doUpdate(File allCountriesFile, File alternateNamesFile, File hierarchyFile) throws IOException, SQLException
    {
        final GeonamesImporter geonamesImporter = new GeonamesImporter.Builder()
                .allCountriesFile(allCountriesFile)
                .alternateNamesFile(alternateNamesFile)
                .exclusions(exclusions)
                .hierarchyFile(hierarchyFile)
                .build();

        final int bufferSize = 20_000;
        final List<Map<String, ?>> buffer = new ArrayList<>(bufferSize);

        geonamesImporter.processFile(entry ->
        {
            buffer.add(entry);

            if (buffer.size() == bufferSize)
            {
                flush(buffer);
            }
        });

        flush(buffer);
    }


    private void saveHierarchyData(final Map<Long, Long> childToParent)
    {
        final AtomicInteger index = new AtomicInteger();
        final Map<String, Object>[] params = new Map[childToParent.size()];
        childToParent.forEach((child, parent) ->
        {
            final Map<String, Object> map = new TreeMap<>();
            map.put("id", child);
            map.put("parent_id", parent);
            params[index.getAndIncrement()] = map;
        });

        txnTemplate.execute(t ->
        {
            jdbcTemplate.batchUpdate("INSERT INTO geohierarchy (id, parent_id) VALUES(:id, :parent_id)", params);
            logger.info("Inserted hierarchy data");
            return null;
        });
    }

    private void flush(final List<Map<String, ?>> buffer)
    {
        Map<String, ?>[] params = buffer.toArray(JdbcGeonamesImporter::newArray);

        txnTemplate.execute((transactionStatus) ->
        {
            jdbcTemplate.batchUpdate("INSERT INTO geonames (id, parent_id, name, feature_class, " +
                            "feature_code, country_code, population, elevation_meters, timezone, " +
                            "last_modified, admin_code1, admin_code2, admin_code3, admin_code4, lat, lng, coord) " +
                            "VALUES (:id, :parent_id, :name, :feature_class, :feature_code, :country_code, " +
                            ":population, :elevation_meters, :timezone, :last_modified, :admin_code1, :admin_code2, :admin_code3, :admin_code4, " +
                            ":lat, :lng, ST_GeomFromText(:poly))",
                    params
            );
            buffer.clear();
            return null;
        });
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return new Date(Math.max(ResourceUtil.getLastModified(geoNamesAllCountriesUrl).getTime(), ResourceUtil.getLastModified(geoNamesHierarchyUrl).getTime()));
    }
}
