package com.ethlo.geodata.model;

/*-
 * #%L
 * geodata-model
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
