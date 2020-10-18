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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.ArrayUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.MapFeature;
import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.importer.jdbc.MysqlCursorUtil;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.RawLocation;
import com.ethlo.geodata.progress.StepProgressListener;

@Repository
public class JdbcLocationDao extends JdbcBaseDao implements LocationDao
{
    private static final Logger logger = LoggerFactory.getLogger(JdbcLocationDao.class);

    private static final String[] adminCodeLevels = new String[]{"A.ADM1", "A.ADM2", "A.ADM3", "A.ADM4"};

    private static final WKTReader wktReader = new WKTReader();
    private final RowMapper<RawLocation> LOCATION_MAPPER = (rs, rowNum) ->
    {
        final String countryCode = rs.getString("country_code");
        final int id = rs.getInt("id");
        final int featureCodeId = rs.getInt("feature_code_id");
        final String wkt = rs.getString("coord");
        final Coordinates coordinates = getCoordinatesFromPoint(wkt);
        return new RawLocation(id, rs.getString("name"), countryCode, coordinates, featureCodeId, rs.getLong("population"), rs.getInt("timezone_id"));
    };

    public static String nullAfterOffset(final String[] strings, int offset)
    {
        final String[] copy = Arrays.copyOf(strings, strings.length);
        for (int i = offset + 1; i < copy.length; i++)
        {
            copy[i] = null;
        }
        return StringUtils.arrayToDelimitedString(copy, "|");
    }

    private static String getConcatenatedFeatureCode(final Map<Integer, MapFeature> featureCodes, final int featureCodeId)
    {
        return Optional.ofNullable(featureCodes.get(featureCodeId)).map(f -> f.getFeatureClass() + "." + f.getFeatureCode()).orElseThrow(() -> new EmptyResultDataAccessException("No feature code with ID " + featureCodeId, 1));
    }

    private Optional<Integer> getParentId(final long id, final Map<String, Integer> reverseFeatureMap, final String featureCode, final Map<String, Country> countryToId, final Map<String, Integer> cache, final String countryCode, final String[] adminCodeArray)
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
                return Optional.of(countryToId.get(countryCode).getId());
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
        final Integer parentId = cache.get(key);
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

    @Override
    public Map<String, Integer> loadAdminLevels(final Map<Integer, MapFeature> featureCodes, final StepProgressListener listener)
    {
        final List<Integer> administrativeLevelIds = getAdministrativeLevelIds(featureCodes);

        final Map<String, Integer> cache = new HashMap<>();
        if (administrativeLevelIds.isEmpty())
        {
            return Collections.emptyMap();
        }

        final String sqlAdminCodes = "SELECT id, country_code, name, feature_code_id, admin_code1, admin_code2, admin_code3, admin_code4 FROM geonames WHERE feature_code_id in (" + StringUtils.collectionToCommaDelimitedString(administrativeLevelIds) + ")";
        final MysqlCursorUtil cursorUtil = new MysqlCursorUtil(dataSource);
        cursorUtil.query(sqlAdminCodes, Collections.emptyMap(), rs ->
        {
            while (rs.next())
            {
                final int id = rs.getInt("id");
                final String countryCode = rs.getString("country_code");
                final int featureCodeId = rs.getInt("feature_code_id");
                final String adminCode = getConcatenatedAdminCodes(rs);
                addToCache(cache, featureCodeId, countryCode, adminCode, id);
                listener.progress(cache.size(), null);
            }
        });
        return cache;
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

    private void addToCache(Map<String, Integer> cache, int featureCodeId, String countryCode, String adminCode, int id)
    {
        final String key = countryCode + "|" + adminCode + "|" + featureCodeId;
        final Integer old = cache.put(key, id);
        if (old != null)
        {
            throw new IllegalArgumentException("Attempted to add key " + key + " with id " + id + ", but already seen with id " + old);
        }
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

    @Override
    public Map<Integer, Integer> processChildToParent(final StepProgressListener listener, final Map<String, Country> countries, final Map<Integer, MapFeature> featureCodes, final Map<String, Integer> adminLevels, final Map<String, Integer> reverseFeatureMap, final int adminLevelCount)
    {
        final Map<Integer, Integer> childToParent = new HashMap<>(adminLevelCount + 1_000_000);
        final AtomicInteger noMatch = new AtomicInteger();
        final AtomicInteger selfMatch = new AtomicInteger();
        final AtomicInteger count = new AtomicInteger();

        final MysqlCursorUtil cursorUtil = new MysqlCursorUtil(dataSource);
        final String sqlAll = "SELECT id, country_code, name, feature_code_id, admin_code1, admin_code2, admin_code3, admin_code4 FROM geonames" +
                " WHERE admin_code1 is not null" +
                " OR admin_code2 is not null" +
                " OR admin_code3 is not null" +
                " OR admin_code4 is not null";

        cursorUtil.query(sqlAll, Collections.emptyMap(), rs ->
        {
            while (rs.next())
            {
                final int id = rs.getInt("id");
                final int featureId = rs.getInt("feature_code_id");
                final String featureCode = featureId != 0 ? getConcatenatedFeatureCode(featureCodes, featureId) : null;
                final String countryCode = rs.getString("country_code");
                final String[] adminCodeArray = getAdminCodeArray(rs);
                Integer parentId = getParentId(id, reverseFeatureMap, featureCode, countries, adminLevels, countryCode, adminCodeArray).orElse(null);

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

                if (count.get() % 200_000 == 0)
                {
                    logger.info("Processed {} locations ({}%)", count.get(), (count.get() / (float) adminLevelCount) * 100);
                }

                if (count.get() % 1_000 == 0)
                {
                    listener.progress(count.get(), adminLevelCount);
                }

                count.incrementAndGet();
            }
        });

        logger.info("Processed hierarchy for a total of {} rows, {} nodes. No parent match: {}. Self match: {}", count.get(), childToParent.size(), noMatch.get(), selfMatch.get());
        return childToParent;
    }

    private RawLocation mapLocation(ResultSet rs) throws SQLException
    {
        final String countryCode = rs.getString("country_code");
        final int id = rs.getInt("id");
        final int featureCodeId = rs.getInt("feature_code_id");
        final String wkt = rs.getString("coord");
        final Coordinates coordinates = getCoordinatesFromPoint(wkt);
        return new RawLocation(id, rs.getString("name"), countryCode, coordinates, featureCodeId, rs.getLong("population"), rs.getInt("timezone_id"));
    }

    private Coordinates getCoordinatesFromPoint(final String wkt)
    {
        if (wkt == null)
        {
            return null;
        }

        try
        {
            final Geometry geom = wktReader.read(wkt);
            final Coordinate c = geom.getCoordinate();
            return Coordinates.from(c.y, c.x);
        }
        catch (ParseException e)
        {
            throw new IllegalArgumentException("Invalid WKT: " + wkt, e);
        }
    }

    public Page<RawLocation> findByName(final String name, final Pageable pageable)
    {
        final Map<String, Object> params = new TreeMap<>();
        params.put("name", name);
        params.put("offset", pageable.getOffset());
        params.put("limit", pageable.getPageSize());

        final String findByNameSql = "SELECT *, MATCH(name) AGAINST (:name) as relevance FROM geonames WHERE MATCH (name) AGAINST(:name IN  BOOLEAN MODE)  ORDER  BY relevance DESC, population DESC LIMIT :offset, :limit";
        final String countByNameSql = "SELECT count(id) FROM geonames WHERE MATCH (name) AGAINST(:name IN  BOOLEAN MODE)";

        final List<RawLocation> content = jdbcTemplate.query(findByNameSql, params, LOCATION_MAPPER);
        final long total = count(countByNameSql, params);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<RawLocation> findChildren(String countryCode, int adm1FeatureCodeId, Pageable pageable)
    {
        final Map<String, Object> params = new TreeMap<>();
        params.put("cc", countryCode.toUpperCase());
        params.put("feature_code_id", adm1FeatureCodeId);
        params.put("offset", pageable.getOffset());
        params.put("max", pageable.getPageSize());
        final String findCountryChildrenSql = "select * from geonames where country_code = :cc and feature_code_id = :feature_code_id LIMIT :offset,:max";
        final String countCountryChildrenSql = "select COUNT(id) from geonames where country_code = :cc and feature_code_id = :feature_code_id";

        final List<RawLocation> content = jdbcTemplate.query(findCountryChildrenSql, params, LOCATION_MAPPER);
        final long total = count(countCountryChildrenSql, params);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Optional<RawLocation> findById(final int id)
    {
        return jdbcTemplate.query("SELECT * from geonames WHERE id = :id", Collections.singletonMap("id", id), rs ->
        {
            if (rs.next())
            {
                return Optional.of(mapLocation(rs));
            }
            return Optional.empty();
        });
    }

    @Override
    public Page<RawLocation> findCountriesOnContinent(final String continentCode, final Pageable pageable)
    {
        final Map<String, Object> params = new TreeMap<>();
        params.put("continentCode", continentCode);
        params.put("offset", pageable.getOffset());
        params.put("max", pageable.getPageSize());
        final String findCountriesOnContinentSql = "SELECT * FROM geocountry c, geonames n WHERE c.geoname_id = n.id AND continent = :continentCode LIMIT :offset,:max";
        final List<RawLocation> locations = jdbcTemplate.query(findCountriesOnContinentSql, params, LOCATION_MAPPER);
        final String countCountriesOnContinentSql = "SELECT COUNT(iso) FROM geocountry WHERE continent = :continentCode";
        final long count = count(countCountriesOnContinentSql, params);
        return new PageImpl<>(locations, pageable, count);
    }

    public List<RawLocation> findByIds(final Collection<Integer> ids)
    {
        final String sql = "SELECT * from geonames WHERE id in (:ids)";
        return jdbcTemplate.query(sql, Collections.singletonMap("ids", ids), LOCATION_MAPPER);
    }

    @Override
    public List<RawLocation> findCountries()
    {
        return jdbcTemplate.query(
                "SELECT * FROM geocountry c, geonames n "
                        + "WHERE c.geoname_id = n.id "
                        + "ORDER BY iso", LOCATION_MAPPER);
    }

    @Override
    public List<Integer> findByPhoneNumber(final String phoneNumber)
    {
        final String sql = "SELECT id FROM geocountry WHERE :phone like CONCAT(phone, '%') ORDER BY population DESC";
        return jdbcTemplate.query(sql, Collections.singletonMap("phone", phoneNumber), (rs, num) -> rs.getInt("id"));
    }
}
