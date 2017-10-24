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

import javax.validation.constraints.NotNull;

public class CountrySummary implements Serializable
{
    private static final long serialVersionUID = 3805294728456474230L;

    @NotNull
    private Long id;

    @NotNull
    private String code;
    
    @NotNull
    private String name;
    
    public String getCode()
    {
        return code;
    }
    
    public CountrySummary setId(Long id)
    {
        this.id = id;
        return this;
    }

    public CountrySummary setCode(String code)
    {
        this.code = code;
        return this;
    }
    
    public String getName()
    {
        return name;
    }

    public CountrySummary setName(String name)
    {
        this.name = name;
        return this;
    }

    public CountrySummary withCode(String countryCode)
    {
        this.code = countryCode;
        return this;
    }
    
    public CountrySummary withName(String name)
    {
        this.name = name;
        return this;
    }

    public Long getId()
    {
        return this.id;
    }
    
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((code == null) ? 0 : code.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CountrySummary other = (CountrySummary) obj;
        if (code == null)
        {
            if (other.code != null)
                return false;
        }
        else if (!code.equals(other.code))
            return false;
        if (id == null)
        {
            if (other.id != null)
                return false;
        }
        else if (!id.equals(other.id))
            return false;
        if (name == null)
        {
            if (other.name != null)
                return false;
        }
        else if (!name.equals(other.name))
            return false;
        return true;
    }

    @Override
    public String toString()
    {
        return "CountrySummary [" + (id != null ? "id=" + id + ", " : "") + (code != null ? "code=" + code + ", " : "") + (name != null ? "name=" + name : "") + "]";
    }
}
