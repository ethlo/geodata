package com.ethlo.geodata.model;

/*-
 * #%L
 * geodata-repository
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

public class GeoLocationDistance implements Serializable
{
    private static final long serialVersionUID = -4591909310445372923L;

    @NotNull
    private GeoLocation location;

    @NotNull
    private double distance;

    public GeoLocation getLocation()
    {
        return location;
    }

    public GeoLocationDistance setLocation(GeoLocation location)
    {
        this.location = location;
        return this;
    }

    public double getDistance()
    {
        return distance;
    }

    public GeoLocationDistance setDistance(double distance)
    {
        this.distance = distance;
        return this;
    }
}
