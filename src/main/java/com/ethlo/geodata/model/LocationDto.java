package com.ethlo.geodata.model;

import javax.validation.constraints.NotNull;

public class LocationDto
{
    @NotNull
    private Long id;

    private Long parentLocationId;

    @NotNull
    private String name;

    private String city;

    private String address;

    private CountryDto country;

    private CoordinateDto coordinates;

    public Long getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public String getCity()
    {
        return city;
    }

    public String getAddress()
    {
        return address;
    }

    public CountryDto getCountry()
    {
        return country;
    }

    public Long getParentLocationId()
    {
        return parentLocationId;
    }
    
    public CoordinateDto getCoordinates()
    {
        return this.coordinates;
    }

    public static class Builder
    {
        private Long id;
        private Long parentLocationId;
        private CoordinateDto coordinates;
        private String name;
        private String city;
        private String address;
        private CountryDto country;

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
        
        public Builder coordinates(CoordinateDto coordinates)
        {
            this.coordinates = coordinates;
            return this;
        }

        public Builder city(String city)
        {
            this.city = city;
            return this;
        }

        public Builder address(String address)
        {
            this.address = address;
            return this;
        }

        public Builder country(CountryDto country)
        {
            this.country = country;
            return this;
        }

        public LocationDto build()
        {
            return new LocationDto(this);
        }
    }

    private LocationDto(Builder builder)
    {
        this.id = builder.id;
        this.parentLocationId = builder.parentLocationId;
        this.coordinates = builder.coordinates;
        this.name = builder.name;
        this.city = builder.city;
        this.address = builder.address;
        this.country = builder.country;
    }
}
