package com.ethlo.geodata;

public class MapFeature
{
    private final String featureClass;
    private final String featureCode;

    public MapFeature(final String featureClass, final String featureCode)
    {
        this.featureClass = featureClass;
        this.featureCode = featureCode;
    }

    public String getFeatureClass()
    {
        return featureClass;
    }

    public String getFeatureCode()
    {
        return featureCode;
    }
}
