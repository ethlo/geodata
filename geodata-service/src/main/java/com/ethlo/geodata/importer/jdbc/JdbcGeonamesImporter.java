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
    private final DataSource dataSource;

    @Value("${geodata.geonames.source.names}")
    private String geoNamesAllCountriesUrl;

    @Value("${geodata.geonames.source.alternatenames}")
    private String geoNamesAlternateNamesUrl;

    @Value("${geodata.geonames.source.hierarchy}")
    private String geoNamesHierarchyUrl;

    private Set<String> exclusions;

    public JdbcGeonamesImporter(final DataSource dataSource, final TransactionTemplate txnTemplate, final NamedParameterJdbcTemplate jdbcTemplate)
    {
        this.dataSource = dataSource;
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

        // Load country code to id
        final Map<String, Long> countryCodeToId = new HashMap<>();
        jdbcTemplate.query("SELECT * FROM geocountry", (rs, rowNum) ->
        {
            final long countryId = rs.getLong("geoname_id");
            final String isoCode = rs.getString("iso");
            countryCodeToId.put(isoCode, countryId);
            return null;
        });

        buildHierarchyDataFromAdminCodes(countryCodeToId);
    }

    private void buildHierarchyDataFromAdminCodes(final Map<String, Long> countryCodeToId) throws SQLException
    {
        final AtomicInteger count = new AtomicInteger();

        // Connect administrative levels
        final Map<Long, Long> childToParent = new HashMap<>(10_000);
        final MysqlCursorUtil cursorUtil = new MysqlCursorUtil(dataSource);
        cursorUtil.query("SELECT id, country_code, name, admin_code1, admin_code2, admin_code3, admin_code4 FROM geonames", Collections.emptyMap(), rs ->
        {
            try
            {
                while (rs.next())
                {
                    final long id = rs.getLong("id");
                    final String name = rs.getString("name");
                    final String adminCode1 = rs.getString("admin_code1");
                    final String adminCode2 = rs.getString("admin_code2");
                    final String adminCode3 = rs.getString("admin_code3");
                    final String adminCode4 = rs.getString("admin_code4");
                    final String countryCode = rs.getString("country_code");

                    String query = "SELECT id FROM geonames WHERE country_code = :country_code";
                    if (adminCode4 != null)
                    {
                        query += " AND admin_code4 = :admin_code4 AND feature_code = 'ADM4'";
                    }
                    else if (adminCode3 != null)
                    {
                        query += " AND admin_code3 = :admin_code3 AND feature_code = 'ADM3'";
                    }
                    else if (adminCode2 != null)
                    {
                        query += " AND admin_code2 = :admin_code2 AND feature_code = 'ADM2'";
                    }
                    else if (adminCode1 != null)
                    {
                        query += " AND admin_code1 = :admin_code1 AND feature_code = 'ADM1'";
                    }

                    final Map<String, Object> params = new TreeMap<>();
                    params.put("country_code", countryCode);
                    params.put("admin_code1", adminCode1);
                    params.put("admin_code2", adminCode2);
                    params.put("admin_code3", adminCode3);
                    params.put("admin_code4", adminCode4);

                    final List<Long> parentIds = jdbcTemplate.queryForList(query, params, Long.class);
                    if (parentIds.size() == 1)
                    {
                        final long parentId = parentIds.get(0);
                        childToParent.put(id, parentId);
                    }
                    else if (countryCode != null)
                    {
                        final Long countryId = countryCodeToId.get(countryCode);
                        if (countryId != null)
                        {
                            childToParent.put(id, countryId);
                        }
                        else
                        {
                            logger.info("No id for country code {}", countryCode);
                        }
                    }
                    else
                    {
                        logger.info("No country nor hierarchy: {} - {}", id, name);
                    }

                    if (childToParent.size() % 2000 == 0)
                    {
                        logger.info("Processed rows: {}", count.get());
                        saveHierarchyData(childToParent);
                        childToParent.clear();
                    }

                    count.incrementAndGet();
                }
            }
            catch (SQLException exc)
            {
                throw new RuntimeException(exc);
            }
        });

        // Save any left-overs
        saveHierarchyData(childToParent);

        logger.info("Processed hierarchy for a total of {} rows", count.get());
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
