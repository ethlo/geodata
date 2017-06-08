package com.ethlo.geodata.model;

public class Continent extends GeoLocation
{
    private static final long serialVersionUID = -1858011430618536247L;
    
    private String continentCode;

    protected Continent()
    {
        
    }
    
    public Continent(String continentCode, GeoLocation location)
    {
        setCoordinates(location.getCoordinates());
        setCountry(location.getCountry());
        setFeatureCode(location.getFeatureCode());
        setId(location.getId());
        setName(location.getName());
        setParentLocationId(location.getParentLocationId());
        setPopulation(location.getPopulation());
        this.continentCode = continentCode;
    }

    public String getContinentCode()
    {
        return continentCode;
    }    
}
