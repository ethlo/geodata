package com.ethlo.geodata.model;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class CoordinateDto
{
    @NotNull
    @Min(-90)
    @Max(90)
    private double lat;
    
    @NotNull
    @Min(-180)
    @Max(180)
    private double lng;
    
    public double getLat()
    {
        return lat;
    }
    
    public CoordinateDto setLat(double latitude)
    {
        this.lat = latitude;
        return this;
    }
    
    public double getLng()
    {
        return lng;
    }
    
    public CoordinateDto setLng(double longitude)
    {
        this.lng = longitude;
        return this;
    }
}
