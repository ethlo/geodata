package com.ethlo.geodata.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "name", "featureClass", "featureCode"})
public class GeoLocationSummary
{
    private final GeoLocation location;

    public GeoLocationSummary(GeoLocation location)
    {
        this.location = location;
    }

    public String getFeatureClass()
    {
        return location.getFeatureClass();
    }

    public String getFeatureCode()
    {
        return location.getFeatureCode();
    }

    public int getId()
    {
        return location.getId();
    }

    public String getName()
    {
        return location.getName();
    }

    public GeoLocation setFeatureClass(final String featureClass)
    {
        return location.setFeatureClass(featureClass);
    }
}
