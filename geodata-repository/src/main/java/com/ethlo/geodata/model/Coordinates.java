package com.ethlo.geodata.model;

import java.io.Serializable;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class Coordinates implements Serializable
{
    private static final long serialVersionUID = -3056995518191959558L;

    @NotNull
    @Min(-90)
    @Max(90)
    private double lat;

    @NotNull
    @Min(-180)
    @Max(180)
    private double lng;

    public static Coordinates from(double lat, double lng)
    {
        return new Coordinates().setLat(lat).setLng(lng);
    }

    public double getLat()
    {
        return lat;
    }

    public Coordinates setLat(double latitude)
    {
        this.lat = latitude;
        return this;
    }

    public double getLng()
    {
        return lng;
    }

    public Coordinates setLng(double longitude)
    {
        this.lng = longitude;
        return this;
    }

    @Override
    public String toString()
    {
        return "[lat=" + lat + ", lng=" + lng + "]";
    }
}
