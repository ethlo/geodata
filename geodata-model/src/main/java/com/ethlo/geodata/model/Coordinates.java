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

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
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
