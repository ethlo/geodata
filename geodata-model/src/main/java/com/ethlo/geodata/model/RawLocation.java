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

public class RawLocation implements Serializable
{
    private final int id;
    private final String name;
    private final String countryCode;
    private final double lat;
    private final double lng;
    private final int mapFeatureId;
    private final long population;
    private final int timeZoneId;

    public RawLocation(final int id, final String name, final String countryCode, final Coordinates coordinates, final int mapFeatureId, final long population, final int timeZoneId)
    {
        this.id = id;
        this.name = name;
        this.countryCode = countryCode;
        this.lat = coordinates.getLat();
        this.lng = coordinates.getLng();
        this.mapFeatureId = mapFeatureId;
        this.population = population;
        this.timeZoneId = timeZoneId;
    }

    public int getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public String getCountryCode()
    {
        return countryCode;
    }

    public Coordinates getCoordinates()
    {
        return Coordinates.from(lat, lng);
    }

    public int getMapFeatureId()
    {
        return mapFeatureId;
    }

    public long getPopulation()
    {
        return population;
    }

    public int getTimeZoneId()
    {
        return timeZoneId;
    }
}
