package com.ethlo.geodata.model;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "parentLocationId", "name", "featureClass", "featureCode", "country", "coordinates", "timeZone", "population", "path"})
public class GeoLocationWithPath
{
    private final GeoLocation location;
    private final List<GeoLocationSummary> path;

    public GeoLocationWithPath(GeoLocation location, List<GeoLocation> path)
    {
        this.location = location;
        this.path = path.stream().map(GeoLocationSummary::new).collect(Collectors.toList());
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

    public CountrySummary getCountry()
    {
        return location.getCountry();
    }

    public Integer getParentLocationId()
    {
        return location.getParentLocationId();
    }

    public Coordinates getCoordinates()
    {
        return location.getCoordinates();
    }

    public long getPopulation()
    {
        return location.getPopulation();
    }

    public String getFeatureClass()
    {
        return location.getFeatureClass();
    }

    public String getTimeZone()
    {
        return location.getTimeZone();
    }

    public List<GeoLocationSummary> getPath()
    {
        return path;
    }
}
