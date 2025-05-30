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

import org.locationtech.jts.geom.Coordinate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class Coordinates implements Serializable
{
    private static final long serialVersionUID = -3056995518191959558L;

    @NotNull
    @Min(-90)
    @Max(90)
    private double lat;

    @NotNull
    @Min(-180)
    @Max(180)
    private double lng;

    public static Coordinates from(double lat, double lng)
    {
        return new Coordinates().setLat(lat).setLng(lng);
    }

    public static Coordinates of(final Coordinate coord)
    {
        return Coordinates.from(coord.y, coord.x);
    }

    public double getLat()
    {
        return lat;
    }

    public Coordinates setLat(double latitude)
    {
        this.lat = latitude;
        return this;
    }

    public double getLng()
    {
        return lng;
    }

    public Coordinates setLng(double longitude)
    {
        this.lng = longitude;
        return this;
    }

    @Override
    public String toString()
    {
        return "[lat=" + lat + ", lng=" + lng + "]";
    }
}
