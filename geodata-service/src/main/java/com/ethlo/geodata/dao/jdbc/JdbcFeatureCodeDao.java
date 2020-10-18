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

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.ethlo.geodata.MapFeature;
import com.ethlo.geodata.dao.FeatureCodeDao;

@Repository
public class JdbcFeatureCodeDao extends JdbcBaseDao implements FeatureCodeDao
{
    @Override
    public Map<Integer, MapFeature> findFeatureCodes()
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
