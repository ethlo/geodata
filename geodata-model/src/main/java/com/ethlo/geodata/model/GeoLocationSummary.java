package com.ethlo.geodata.model;

public class GeoLocationSummary
{
    private final int id;
    private final String name;
    private final String featureClass;
    private final String featureCode;

    public GeoLocationSummary(GeoLocation location)
    {
        this.id = location.getId();
        this.name = location.getName();
        this.featureClass = location.getFeatureClass();
        this.featureCode = location.getFeatureCode();
    }

    public int getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
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
