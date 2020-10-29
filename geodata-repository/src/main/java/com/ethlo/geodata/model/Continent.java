package com.ethlo.geodata.model;

/*-
 * #%L
 * geodata-model
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
        setFeatureClass(location.getFeatureClass());
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
