package com.ethlo.geodata.model;

import java.io.Serializable;

import javax.validation.constraints.NotNull;

public class GeoLocationDistance implements Serializable
{
    private static final long serialVersionUID = -4591909310445372923L;

    @NotNull
    private GeoLocation location;

    @NotNull
    private double distance;

    public GeoLocation getLocation()
    {
        return location;
    }

    public GeoLocationDistance setLocation(GeoLocation location)
    {
        this.location = location;
        return this;
    }

    public double getDistance()
    {
        return distance;
    }

    public GeoLocationDistance setDistance(double distance)
    {
        this.distance = distance;
        return this;
    }
}
