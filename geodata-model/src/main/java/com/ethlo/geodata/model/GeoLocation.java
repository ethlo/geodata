package com.ethlo.geodata.model;

import java.io.Serializable;

import javax.validation.constraints.NotNull;

public class GeoLocation extends GeoEntity implements Serializable
{
    private static final long serialVersionUID = -4591909310445372923L;

    /**
     * The parent location of this location. Typically a state has a country as parent, and a country is part of a continent, etc.
     */
    private Long parentLocationId;

    /**
     * The country this location belong to
     */
    private CountrySummary country;

    /**
     * The feature-class as defined by geonames.org
     */
    @NotNull
    private String featureClass;

    /**
     * The feature-code as defined by geonames.org
     */
    @NotNull
    private String featureCode;

    /**
     * The estimated population of this location
     */
    private Long population;

    public String getFeatureCode()
    {
        return featureCode != null ? featureCode.toUpperCase() : null;
    }

    public GeoLocation setFeatureCode(String featureCode)
    {
        this.featureCode = featureCode;
        return this;
    }

    public CountrySummary getCountry()
    {
        return country;
    }

    public GeoLocation setCountry(CountrySummary country)
    {
        this.country = country;
        return this;
    }

    public Long getParentLocationId()
    {
        return parentLocationId;
    }

    public GeoLocation setParentLocationId(Long parentLocationId)
    {
        this.parentLocationId = parentLocationId;
        return this;
    }

    @Override
    public String toString()
    {
        return "GeoLocation [" + (getId() != null ? "id=" + getId() + ", " : "")
                + (parentLocationId != null ? "parentLocationId=" + parentLocationId + ", " : "")
                + (getName() != null ? "name=" + getName() + ", " : "")
                + (country != null ? "country=" + country + ", " : "")
                + (getCoordinates() != null ? "coordinates=" + getCoordinates() : "") + "]";
    }

    public Long getPopulation()
    {
        return population;
    }

    public GeoLocation setPopulation(Long population)
    {
        this.population = population;
        return this;
    }

    public String getFeatureClass()
    {
        return featureClass != null ? featureClass.toUpperCase() : null;
    }

    public GeoLocation setFeatureClass(String featureClass)
    {
        this.featureClass = featureClass;
        return this;
    }
}
