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

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
public class Coordinates implements Serializable
{
    private static final long serialVersionUID = -3056995518191959558L;

    /**
     * The latitude of the coordinate
     */
    @NotNull
    @Min(-90)
    @Max(90)
    private double lat;
    
    /**
     * The longitude of the coordinate
     */
    @NotNull
    @Min(-180)
    @Max(180)
    private double lng;
    
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

    public static Coordinates from(double lat, double lng)
    {
        return new Coordinates().setLat(lat).setLng(lng);
    }
}
