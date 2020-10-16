package com.ethlo.geodata.dao.jdbc;

/*-
 * #%L
 * Geodata service
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.MapFeature;
import com.ethlo.geodata.importer.jdbc.MysqlCursorUtil;

@Repository
public class JdbcGeonamesDao
{
    private static final Logger logger = LoggerFactory.getLogger(JdbcGeonamesDao.class);

    private static final String[] adminCodeLevels = new String[]{"A.ADM1", "A.ADM2", "A.ADM3", "A.ADM4"};

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public static String nullAfterOffset(final String[] strings, int offset)
    {
        final String[] copy = Arrays.copyOf(strings, strings.length);
        for (int i = offset + 1; i < copy.length; i++)
        {
            copy[i] = null;
        }
        return StringUtils.arrayToDelimitedString(copy, "|");
    }

    public Map<Long, Long> buildHierarchyDataFromAdminCodes() throws SQLException
    {
        final Map<Integer, MapFeature> featureCodes = loadFeatureCodes();

        logger.info("Loading administrative levels for hierarchy building");
        final Map<String, Long> countryToId = loadCountryToId();

        final AtomicInteger count = new AtomicInteger();
        final Map<String, Long> cache = new HashMap<>();
        final List<Integer> administrativeLevelIds = getAdministrativeLevelIds(featureCodes);

        final String sqlAdminCodes = "SELECT id, country_code, name, feature_code_id, admin_code1, admin_code2, admin_code3, admin_code4 FROM geonames WHERE feature_code_id in (" + StringUtils.collectionToCommaDelimitedString(administrativeLevelIds) + ")";
        final MysqlCursorUtil cursorUtil = new MysqlCursorUtil(dataSource);
        cursorUtil.query(sqlAdminCodes, Collections.emptyMap(), rs ->
        {
            while (rs.next())
            {
                final long id = rs.getLong("id");
                final String countryCode = rs.getString("country_code");
                final int featureCodeId = rs.getInt("feature_code_id");
                final String adminCode = getConcatenatedAdminCodes(rs);
                addToCache(cache, featureCodeId, countryCode, adminCode, id);
            }
        });

        final Map<String, Integer> reverseFeatureMap = new HashMap<>();
        loadFeatureCodes().forEach((k, v) -> reverseFeatureMap.put(v.getFeatureClass() + "." + v.getFeatureCode(), k));

        logger.info("Loaded {} administrative levels", cache.size());

        final String sqlBase = "SELECT $fields FROM geonames" +
                " WHERE admin_code1 is not null" +
                " OR admin_code2 is not null" +
                " OR admin_code3 is not null" +
                " OR admin_code4 is not null";
        final String sqlAllCount = sqlBase.replace("$fields", "count(id)");
        final String sqlAll = sqlBase.replace("$fields", "id, country_code, name, feature_code_id, admin_code1, admin_code2, admin_code3, admin_code4");

        final int adminLevelCount = jdbcTemplate.queryForObject(sqlAllCount, Collections.emptyMap(), Integer.class);
        logger.info("Processing {} administrative level hierarchy nodes", adminLevelCount);
        final Map<Long, Long> childToParent = new HashMap<>(adminLevelCount + 1_000_000);
        final AtomicInteger noMatch = new AtomicInteger();
        final AtomicInteger selfMatch = new AtomicInteger();
        cursorUtil.query(sqlAll, Collections.emptyMap(), rs ->
        {
            while (rs.next())
            {
                final long id = rs.getLong("id");
                //final String name = rs.getString("name");
                final int featureId = rs.getInt("feature_code_id");
                final String featureCode = featureId != 0 ? getConcatenatedFeatureCode(featureCodes, featureId) : null;
                final String countryCode = rs.getString("country_code");
                final String[] adminCodeArray = getAdminCodeArray(rs);

                //logger.info("Finding parent of {} {} {} {}", id, name, countryCode, featureCode);
                Long parentId = getParentId(id, reverseFeatureMap, featureCode, countryToId, cache, countryCode, adminCodeArray).orElse(null);

                if (parentId == null)
                {
                    noMatch.incrementAndGet();
                }
                else if (id == parentId)
                {
                    logger.debug("Self-reference: {}", id);
                    selfMatch.incrementAndGet();
                }
                else
                {
                    childToParent.put(id, parentId);
                }

                if (count.get() % 200_000 == 0 && count.get() > 0)
                {
                    logger.info("Processed {} locations ({}%)", count.get(), (count.get() / (float) adminLevelCount) * 100);
                }

                count.incrementAndGet();
            }
        });

        logger.info("Processed hierarchy for a total of {} rows, {} nodes. No parent match: {}. Self match: {}", count.get(), childToParent.size(), noMatch.get(), selfMatch.get());
        return childToParent;
    }

    private String getConcatenatedFeatureCode(final Map<Integer, MapFeature> featureCodes, final int featureCodeId)
    {
        return Optional.ofNullable(featureCodes.get(featureCodeId)).map(f -> f.getFeatureClass() + "." + f.getFeatureCode()).orElseThrow(() -> new EmptyResultDataAccessException("No feature code with ID " + featureCodeId, 1));
    }

    private List<Integer> getAdministrativeLevelIds(final Map<Integer, MapFeature> featureCodes)
    {
        final Set<String> levelNames = new HashSet<>(Arrays.asList("ADM1", "ADM2", "ADM3", "ADM4"));
        final List<Integer> result = new ArrayList<>();
        featureCodes.forEach((id, feature) ->
        {
            if (levelNames.contains(feature.getFeatureCode()))
            {
                result.add(id);
            }
        });
        return result;
    }

    private void addToCache(Map<String, Long> cache, int featureCodeId, String countryCode, String adminCode, long id)
    {
        final String key = countryCode + "|" + adminCode + "|" + featureCodeId;
        final Long old = cache.put(key, id);
        if (old != null)
        {
            throw new IllegalArgumentException("Attempted to add key " + key + " with id " + id + ", but already seen with id " + old);
        }
    }

    private Optional<Long> getParentId(final long id, final Map<String, Integer> reverseFeatureMap, final String featureCode, final Map<String, Long> countryToId, final Map<String, Long> cache, final String countryCode, final String[] adminCodeArray)
    {
        String adminLevelCode;
        String adminCodes;

        if (ArrayUtils.contains(adminCodeLevels, featureCode))
        {
            // We are processing an ADMx level
            final int index = Arrays.binarySearch(adminCodeLevels, featureCode);
            if (index == 0)
            {
                // Country level
                return Optional.ofNullable(countryToId.get(countryCode));
            }

            adminLevelCode = adminCodeLevels[index - 1];
            adminCodes = nullAfterOffset(adminCodeArray, index - 1);
        }
        else
        {
            // We are processing a non-ADMx location
            final int lastNonNullIndex = lastOfNonNull(adminCodeArray);
            adminLevelCode = adminCodeLevels[lastNonNullIndex];
            adminCodes = StringUtils.arrayToDelimitedString(adminCodeArray, "|");
        }

        final String key = countryCode + "|" + adminCodes + "|" + reverseFeatureMap.get(adminLevelCode);
        final Long parentId = cache.get(key);
        if (parentId == null)
        {
            logger.debug("No match for parent for {} - {} - {}: {}", id, featureCode, countryCode, key);
        }
        return Optional.ofNullable(parentId);
    }

    private int lastOfNonNull(final String[] codes)
    {
        for (int i = codes.length - 1; i >= 0; i--)
        {
            if (codes[i] != null)
            {
                return i;
            }
        }
        return -1;
    }

    private String getConcatenatedAdminCodes(final ResultSet rs) throws SQLException
    {
        final String[] adminCodes = getAdminCodeArray(rs);
        return StringUtils.arrayToDelimitedString(adminCodes, "|");
    }

    private String[] getAdminCodeArray(final ResultSet rs) throws SQLException
    {
        final String[] result = new String[4];
        result[0] = rs.getString("admin_code1");
        result[1] = rs.getString("admin_code2");
        result[2] = rs.getString("admin_code3");
        result[3] = rs.getString("admin_code4");
        return result;
    }

    private Map<String, Long> loadCountryToId()
    {
        final Map<String, Long> countryCodeToId = new HashMap<>();
        jdbcTemplate.query("SELECT * FROM geocountry", (rs, rowNum) ->
        {
            final long countryId = rs.getLong("geoname_id");
            final String isoCode = rs.getString("iso");
            countryCodeToId.put(isoCode, countryId);
            return null;
        });
        return countryCodeToId;
    }

    public Map<Integer, MapFeature> loadFeatureCodes()
    {
        final Map<Integer, MapFeature> featureCodes = new HashMap<>();
        jdbcTemplate.query("SELECT * FROM feature_codes", rs ->
        {
            final int id = rs.getInt("id");
            final String featureClass = rs.getString("feature_class");
            final String featureCode = rs.getString("feature_code");
            featureCodes.put(id, new MapFeature(featureClass, featureCode));
        });
        return featureCodes;
    }
}
