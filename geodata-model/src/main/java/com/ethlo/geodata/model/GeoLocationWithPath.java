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

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "parentLocationId", "name", "featureClass", "featureCode", "country", "coordinates", "timeZone", "population", "path"})
public class GeoLocationWithPath
{
    private final GeoLocation location;
    private final List<GeoLocationSummary> path;

    public GeoLocationWithPath(GeoLocation location, List<GeoLocation> path)
    {
        this.location = location;
        this.path = path.stream().map(GeoLocationSummary::new).collect(Collectors.toList());
    }

    public String getFeatureCode()
    {
        return location.getFeatureCode();
    }

    public int getId()
    {
        return location.getId();
    }

    public String getName()
    {
        return location.getName();
    }

    public CountrySummary getCountry()
    {
        return location.getCountry();
    }

    public Integer getParentLocationId()
    {
        return location.getParentLocationId();
    }

    public Coordinates getCoordinates()
    {
        return location.getCoordinates();
    }

    public long getPopulation()
    {
        return location.getPopulation();
    }

    public String getFeatureClass()
    {
        return location.getFeatureClass();
    }

    public String getTimeZone()
    {
        return location.getTimeZone();
    }

    public List<GeoLocationSummary> getPath()
    {
        return path;
    }
}
