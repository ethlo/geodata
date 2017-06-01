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
