package com.ethlo.geodata.dao.file;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.ReverseGeocodingDao;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.RawLocation;

@Repository
public class JdbcReverseGeocodingDao implements ReverseGeocodingDao
{
    public static final double RAD_TO_KM_RATIO = 111.195D;

    @Override
    public Map<Integer, Double> findNearest(Coordinates point, int maxDistance, Pageable pageable)
    {
        // Switch Lat/long
        final Coordinates coordinates = new Coordinates().setLat(point.getLng()).setLng(point.getLat());

        final Map<String, Object> params = createParams(coordinates, maxDistance, pageable);
        final String nearestSql = "SELECT id, st_distance(POINT(:x, :y), coord) AS distance FROM geonames WHERE st_within(coord, st_envelope(linestring(point(:minX, :minY), point(:maxX,:maxY)))) ORDER BY distance ASC LIMIT :offset, :limit";
        final Map<Integer, Double> result = new LinkedHashMap<>();
        /*jdbcTemplate.query(nearestSql, params, rs ->
        {
            while (rs.next())
            {
                final int id = rs.getInt("id");
                final double distance = BigDecimal.valueOf(rs.getDouble("distance") * RAD_TO_KM_RATIO).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
                result.put(id, distance);
            }
        });
        */
        return result;
    }

    @Override
    public RawLocation findContaining(final Coordinates point, final int range)
    {
        // TODO:
        return null;
    }

    private List<Integer> doFindContaining(Coordinates point, int maxDistanceInKm)
    {
        final Map<String, Object> params = createParams(point, maxDistanceInKm, PageRequest.of(0, 1));
        final String findWithinBoundariesSql = "SELECT id FROM geoboundaries WHERE st_within(coord, st_envelope(linestring(point(:minX, :minY), point(:maxX, :maxY)))) AND st_contains(raw_polygon, POINT(:x,:y)) ORDER BY area ASC LIMIT :limit";
        return null;
    }

    private Map<String, Object> createParams(Coordinates point, int maxDistanceInKm, Pageable pageable)
    {
        final double lat = point.getLat();
        final double lon = point.getLng();
        final double R = 6371;  // earth radius in km
        final double v = Math.toDegrees(maxDistanceInKm / R / Math.cos(Math.toRadians(lat)));
        double x1 = lon - v;
        double x2 = lon + v;
        double y1 = lat - Math.toDegrees(maxDistanceInKm / R);
        double y2 = lat + Math.toDegrees(maxDistanceInKm / R);

        final Map<String, Object> params = new TreeMap<>();
        params.put("point", "POINT(" + point.getLng() + " " + point.getLat() + ")");
        params.put("minPoint", "POINT(" + x1 + " " + y1 + ")");
        params.put("maxPoint", "POINT(" + x2 + " " + y2 + ")");
        params.put("x", point.getLng());
        params.put("y", point.getLat());
        params.put("minX", x1);
        params.put("minY", y1);
        params.put("maxX", x2);
        params.put("maxY", y2);
        params.put("offset", pageable.getOffset());
        params.put("limit", pageable.getPageSize());
        return params;
    }
}
