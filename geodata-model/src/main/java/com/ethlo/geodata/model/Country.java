package com.ethlo.geodata.model;

import java.io.Serializable;
import java.util.List;

public class Country extends GeoLocation implements Serializable
{
    private static final long serialVersionUID = -4692269307453103789L;
        
    private List<String> languages;
    
    protected Country()
    {
        
    }
    
    public CountrySummary toSummary(String countryCode)
    {
        return new CountrySummary().setId(getId()).setName(getName()).setCode(countryCode);
    }

    public static Country from(GeoLocation location)
    {
        final Country country = new Country();
        country.setName(location.getName());
        country.setCoordinates(location.getCoordinates());
        country.setId(location.getId());
        country.setParentLocationId(location.getParentLocationId());
        country.setFeatureCode(location.getFeatureCode());
        country.setPopulation(location.getPopulation());
        return country;
    }

    public List<String> getLanguages()
    {
        return languages;
    }

    public Country setLanguages(List<String> languages)
    {
        this.languages = languages;
        return this;
    }
}
