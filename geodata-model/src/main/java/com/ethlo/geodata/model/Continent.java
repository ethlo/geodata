package com.ethlo.geodata.model;

public class Continent extends GeoLocation
{
    private String continentCode;

    public Continent(String continentCode, GeoLocation location)
    {
        super(new GeoLocation.Builder()
             .coordinates(location.getCoordinates())
             .country(location.getCountry())
             .featureCode(location.getFeatureCode())
             .id(location.getId())
             .name(location.getName())
             .parentLocationId(location.getParentLocationId()));
        this.continentCode = continentCode;
    }

    public String getContinentCode()
    {
        return continentCode;
    }    
}
