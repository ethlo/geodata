package com.ethlo.geodata.model;

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

import com.ethlo.geodata.util.Assert;

public class GeoLocation
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

        public GeoLocation build()
        {
            return new GeoLocation(this);
        }

        public Builder featureCode(String featureCode)
        {
            this.featureCode = featureCode;
            return this;
        }
    }

    private GeoLocation(Builder builder)
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
