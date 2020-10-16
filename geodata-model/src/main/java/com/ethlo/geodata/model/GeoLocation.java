package com.ethlo.geodata.model;

/*-
 * #%L
 * geodata-model
 * %%
 * Copyright (C) 2017 - 2020 Morten Haraldsen (ethlo)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

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
    private String featureClass;

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

    public String getFeatureClass()
    {
        return featureClass;
    }

    public GeoLocation setFeatureClass(final String featureClass)
    {
        this.featureClass = featureClass;
        return this;
    }
}
