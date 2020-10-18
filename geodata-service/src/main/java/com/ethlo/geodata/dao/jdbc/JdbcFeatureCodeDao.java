package com.ethlo.geodata.dao.jdbc;

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
