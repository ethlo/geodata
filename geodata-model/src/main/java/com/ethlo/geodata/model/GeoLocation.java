package com.ethlo.geodata.model;

import java.io.Serializable;

/*-
 * #%L
 * geodata
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
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
        return featureCode;
    }

    public CountrySummary getCountry()
    {
        return country;
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

    public GeoLocation setCountry(CountrySummary country)
    {
        this.country = country;
        return this;
    }

    public GeoLocation setFeatureCode(String featureCode)
    {
        this.featureCode = featureCode;
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
        return featureClass;
    }

    public GeoLocation setFeatureClass(String featureClass)
    {
        this.featureClass = featureClass;
        return this;
    }
}
