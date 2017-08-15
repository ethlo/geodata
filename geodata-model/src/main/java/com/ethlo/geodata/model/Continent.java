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

public class Continent extends GeoLocation
{
    private static final long serialVersionUID = -1858011430618536247L;
    
    private String continentCode;

    protected Continent()
    {
        
    }
    
    public Continent(String continentCode, GeoLocation location)
    {
        setCoordinates(location.getCoordinates());
        setCountry(location.getCountry());
        setFeatureCode(location.getFeatureCode());
        setId(location.getId());
        setName(location.getName());
        setParentLocationId(location.getParentLocationId());
        setPopulation(location.getPopulation());
        this.continentCode = continentCode;
    }

    public String getContinentCode()
    {
        return continentCode;
    }    
}
