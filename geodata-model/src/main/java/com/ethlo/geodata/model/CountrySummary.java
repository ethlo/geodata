package com.ethlo.geodata.model;

import java.io.Serializable;

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

    public CountrySummary setId(Long id)
    {
        this.id = id;
        return this;
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
