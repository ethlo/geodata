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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.MapFeature;
import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.importer.HierachyBuilder;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.RawLocation;
import com.ethlo.geodata.progress.StepProgressListener;

@Repository
public class JdbcLocationDao extends JdbcBaseDao implements LocationDao
{
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

    @Override
    public List<RawLocation> getCountries()
    {
        return jdbcTemplate.query(
                "SELECT * FROM geocountry c, geonames n "
                        + "WHERE c.geoname_id = n.id "
                        + "ORDER BY iso",
                LOCATION_MAPPER
        );
    }

    @Override
    public Map<Integer, Integer> loadHierarchy(final Map<String, Country> countries, final Map<Integer, MapFeature> featureCodes, StepProgressListener progressListener)
    {
        return new HierachyBuilder(dataSource).build(countries, featureCodes, progressListener);
    }
}
