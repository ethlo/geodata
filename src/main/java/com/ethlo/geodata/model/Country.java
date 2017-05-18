package com.ethlo.geodata.model;

import javax.validation.constraints.NotNull;

public class Country
{
    @NotNull
    private String code;
    
    @NotNull
    private String name;
    
    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }
    
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
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
}