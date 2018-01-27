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

import java.util.Objects;

import javax.validation.constraints.NotNull;

public class GeoEntity
{
    /**
     * The Geonames.org assigned ID of this location
     */
    @NotNull
    private Long id;
    
    /**
     * The common name of this location
     */
    @NotNull
    private String name;

    /**
     * The geographical coordinates of this location in WGS-84 datum
     */
    @NotNull
    private Coordinates coordinates;

    public Long getId()
    {
        return id;
    }

    public GeoEntity setId(Long id)
    {
        this.id = id;
        return this;
    }

    public String getName()
    {
        return name;
    }
    
    public GeoEntity setName(String name)
    {
        this.name = name;
        return this;
    }

    public Coordinates getCoordinates()
    {
        return coordinates;
    }

    public GeoEntity setCoordinates(Coordinates coordinates)
    {
        this.coordinates = coordinates;
        return this;
    }
    
    @Override
    public int hashCode()
    {
        return id == null ? 0 : Long.hashCode(id);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        
        if (obj instanceof GeoEntity) 
        {
            return Objects.equals(id, ((GeoEntity) obj).id);
        }
        
        return false;
    }
}
