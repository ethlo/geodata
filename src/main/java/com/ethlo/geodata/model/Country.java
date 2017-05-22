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
}