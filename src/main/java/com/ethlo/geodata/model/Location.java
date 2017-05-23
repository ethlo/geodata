package com.ethlo.geodata.model;

import javax.validation.constraints.NotNull;

import org.springframework.util.Assert;

public class Location
{
    @NotNull
    private Long id;

    private Long parentLocationId;

    @NotNull
    private String name;

    private Country country;

    @NotNull
    private Coordinate coordinates;

    @NotNull
    private String featureCode;
    
    public String getFeatureCode()
    {
        return featureCode;
    }

    public Long getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public Country getCountry()
    {
        return country;
    }

    public Long getParentLocationId()
    {
        return parentLocationId;
    }
    
    public Coordinate getCoordinates()
    {
        return this.coordinates;
    }

    public static class Builder
    {
        private Long id;
        private Long parentLocationId;
        private Coordinate coordinates;
        private String name;
        private Country country;
        private String featureCode;

        public Builder id(Long id)
        {
            this.id = id;
            return this;
        }

        public Builder parentLocationId(Long parentLocationId)
        {
            this.parentLocationId = parentLocationId;
            return this;
        }

        public Builder name(String name)
        {
            this.name = name;
            return this;
        }
        
        public Builder coordinates(Coordinate coordinates)
        {
            this.coordinates = coordinates;
            return this;
        }

        public Builder country(Country country)
        {
            this.country = country;
            return this;
        }

        public Location build()
        {
            return new Location(this);
        }

        public Builder featureCode(String featureCode)
        {
            this.featureCode = featureCode;
            return this;
        }
    }

    private Location(Builder builder)
    {
        Assert.notNull(builder.id, "id must not be null");
        Assert.notNull(builder.name, "name must not be null");
        
        this.id = builder.id;
        this.parentLocationId = builder.parentLocationId;
        this.coordinates = builder.coordinates;
        this.name = builder.name;
        this.featureCode = builder.featureCode;
        this.country = builder.country;
    }

    @Override
    public String toString()
    {
        return "Location [" + (id != null ? "id=" + id + ", " : "") + (parentLocationId != null ? "parentLocationId=" + parentLocationId + ", " : "") + (name != null ? "name=" + name + ", " : "")
                        + (country != null ? "country=" + country + ", " : "")
                        + (coordinates != null ? "coordinates=" + coordinates : "") + "]";
    }
}
