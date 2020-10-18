package com.ethlo.geodata.dao;

import java.util.Map;

import com.ethlo.geodata.MapFeature;

public interface FeatureCodeDao
{
    Map<Integer, MapFeature> findFeatureCodes();
}
