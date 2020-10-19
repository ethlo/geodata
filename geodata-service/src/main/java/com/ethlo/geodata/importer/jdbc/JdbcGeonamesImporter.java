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
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.importer.GeonamesImporter;
import com.ethlo.geodata.importer.GeonamesSource;
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
    private Set<String> inclusions;

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

    @Value("${geodata.geonames.features.included}")
    public void setInclusions(String csv)
    {
        inclusions = StringUtils.commaDelimitedListToSet(csv);
    }

    @Override
    public long importData()
    {
        try
        {
            final Map.Entry<Date, File> hierarchyFile = ResourceUtil.fetchResource(GeonamesSource.HIERARCHY, geoNamesHierarchyUrl);
            final Map.Entry<Date, File> alternateNamesFile = ResourceUtil.fetchResource(GeonamesSource.LOCATION_ALTERNATE_NAMES, geoNamesAlternateNamesUrl);
            final Map.Entry<Date, File> allCountriesFile = ResourceUtil.fetchResource(GeonamesSource.LOCATION, geoNamesAllCountriesUrl);
            return doUpdate(allCountriesFile.getValue(), alternateNamesFile.getValue(), hierarchyFile.getValue());
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    @Override
    public void purge()
    {
        jdbcTemplate.update("DELETE FROM geonames", Collections.emptyMap());
    }

    private long doUpdate(File allCountriesFile, File alternateNamesFile, File hierarchyFile) throws IOException
    {
        final GeonamesImporter geonamesImporter = new GeonamesImporter.Builder()
                .allCountriesFile(allCountriesFile)
                .alternateNamesFile(alternateNamesFile)
                .inclusions(inclusions)
                .hierarchyFile(hierarchyFile)
                .build();

        final int bufferSize = 20_000;
        final List<Map<String, ?>> buffer = new ArrayList<>(bufferSize);

        final Map<String, Integer> timezones = new HashMap<>();
        final Map<String, Integer> featureCodes = new HashMap<>();
        final AtomicInteger count = new AtomicInteger();
        final long totalLines = geonamesImporter.processFile(entry ->
        {
            final String timezone = entry.get("timezone");
            final Integer timezoneId = timezones.computeIfAbsent(timezone, this::insertTimezone);

            final String featureClass = entry.get("feature_class");
            final String featureCode = entry.get("feature_code");

            Integer featureCodeId = null;
            if (featureClass == null && featureCode == null)
            {
                logger.debug("No feature code for {}", entry.get("id"));
            }
            else
            {
                featureCodeId = featureCodes.computeIfAbsent(featureClass + "." + featureCode, combined -> insertFeatureCode(featureClass, featureCode, null));
            }

            entry.put("feature_code_id", featureCodeId != null ? Integer.toString(featureCodeId) : null);
            entry.put("timezone_id", timezoneId != null ? Integer.toString(timezoneId) : null);

            buffer.add(entry);
            count.incrementAndGet();

            if (buffer.size() == bufferSize)
            {
                flush(buffer);
            }
        });

        flush(buffer);

        logger.info("Imported {} locations out of a total of {} entries", count.get(), totalLines);
        return count.get();
    }

    private Integer insertTimezone(final String ts)
    {
        if (ts == null)
        {
            return null;
        }
        jdbcTemplate.update("INSERT INTO timezone (id, value) VALUES(null, :ts)", Collections.singletonMap("ts", ts));
        return jdbcTemplate.queryForObject("select last_insert_id()", Collections.emptyMap(), Integer.class);
    }

    private int insertFeatureCode(final String featureClass, final String featureCode, String description)
    {
        final Map<String, Object> params = new TreeMap<>();
        params.put("feature_class", featureClass);
        params.put("feature_code", featureCode);
        params.put("description", description);
        jdbcTemplate.update("INSERT INTO feature_codes (feature_class, feature_code, description) VALUES(:feature_class, :feature_code, :description)", params);
        return jdbcTemplate.queryForObject("select last_insert_id()", Collections.emptyMap(), Integer.class);
    }

    private void flush(final List<Map<String, ?>> buffer)
    {
        Map<String, ?>[] params = buffer.toArray(JdbcGeonamesImporter::newArray);

        txnTemplate.execute((transactionStatus) ->
        {
            jdbcTemplate.batchUpdate("INSERT INTO geonames (id, name, feature_code_id, " +
                            "country_code, population, elevation_meters, timezone_id, " +
                            "last_modified, admin_code1, admin_code2, admin_code3, admin_code4, coord) " +
                            "VALUES (:id, :name, :feature_code_id, :country_code, " +
                            ":population, :elevation_meters, :timezone_id, :last_modified, :admin_code1, :admin_code2, :admin_code3, :admin_code4, " +
                            ":poly)",
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
