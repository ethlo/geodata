package com.ethlo.geodata.model;

import java.io.Serializable;

/*-
 * #%L
 * geodata
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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

    public CountrySummary getCountry()
    {
        return country;
    }

    public Long getParentLocationId()
    {
        return parentLocationId;
    }
    
    public Coordinates getCoordinates()
    {
        return this.coordinates;
    }
    
    public GeoLocation setId(Long id)
    {
        this.id = id;
        return this;
    }

    public GeoLocation setParentLocationId(Long parentLocationId)
    {
        this.parentLocationId = parentLocationId;
        return this;
    }

    public GeoLocation setName(String name)
    {
        this.name = name;
        return this;
    }

    public GeoLocation setCountry(CountrySummary country)
    {
        this.country = country;
        return this;
    }

    public GeoLocation setCoordinates(Coordinates coordinates)
    {
        this.coordinates = coordinates;
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
        return "GeoLocation [" + (id != null ? "id=" + id + ", " : "") + (parentLocationId != null ? "parentLocationId=" + parentLocationId + ", " : "") + (name != null ? "name=" + name + ", " : "")
                        + (country != null ? "country=" + country + ", " : "")
                        + (coordinates != null ? "coordinates=" + coordinates : "") + "]";
    }
}
