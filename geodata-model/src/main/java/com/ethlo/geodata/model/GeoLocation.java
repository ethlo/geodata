package com.ethlo.geodata.model;

import java.io.Serializable;

import javax.validation.constraints.NotNull;

public class GeoLocation implements Serializable
{
    private static final long serialVersionUID = -4591909310445372923L;

    @NotNull
    private Long id;

    private Long parentLocationId;

    @NotNull
    private String name;

    private CountrySummary country;

    @NotNull
    private Coordinates coordinates;

    @NotNull
    private String featureCode;

    private long population;

    public String getFeatureCode()
    {
        return featureCode;
    }

    public GeoLocation setFeatureCode(String featureCode)
    {
        this.featureCode = featureCode;
        return this;
    }

    public Long getId()
    {
        return id;
    }

    public GeoLocation setId(Long id)
    {
        this.id = id;
        return this;
    }

    public String getName()
    {
        return name;
    }

    public GeoLocation setName(String name)
    {
        this.name = name;
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

    public Coordinates getCoordinates()
    {
        return this.coordinates;
    }

    public GeoLocation setCoordinates(Coordinates coordinates)
    {
        this.coordinates = coordinates;
        return this;
    }

    @Override
    public String toString()
    {
        return "GeoLocation [" + (id != null ? "id=" + id + ", " : "") + (parentLocationId != null ? "parentLocationId=" + parentLocationId + ", " : "") + (name != null ? "name=" + name + ", " : "")
                + (country != null ? "country=" + country + ", " : "")
                + (coordinates != null ? "coordinates=" + coordinates : "") + "]";
    }

    public long getPopulation()
    {
        return population;
    }

    public GeoLocation setPopulation(long population)
    {
        this.population = population;
        return this;
    }
}
