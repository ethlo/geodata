package com.ethlo.geodata.model;

import javax.validation.constraints.NotNull;

public class Country
{
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
    
    public Country setId(Long id)
    {
        this.id = id;
        return this;
    }

    public Country setCode(String code)
    {
        this.code = code;
        return this;
    }
    
    public String getName()
    {
        return name;
    }

    public Country setName(String name)
    {
        this.name = name;
        return this;
    }

    public Country withCode(String countryCode)
    {
        this.code = countryCode;
        return this;
    }
    
    public Country withName(String name)
    {
        this.name = name;
        return this;
    }

    public long getId()
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
        Country other = (Country) obj;
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
        return "Country [" + (id != null ? "id=" + id + ", " : "") + (code != null ? "code=" + code + ", " : "") + (name != null ? "name=" + name : "") + "]";
    }
}