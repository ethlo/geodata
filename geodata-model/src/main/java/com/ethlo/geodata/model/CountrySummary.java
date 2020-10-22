package com.ethlo.geodata.model;

import java.io.Serializable;
import java.util.Objects;

import javax.validation.constraints.NotNull;

public class CountrySummary implements Serializable
{
    private static final long serialVersionUID = 3805294728456474230L;

    private int id;

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

    public int getId()
    {
        return this.id;
    }

    public CountrySummary setId(int id)
    {
        this.id = id;
        return this;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final CountrySummary that = (CountrySummary) o;
        return id == that.id &&
                code.equals(that.code);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, code);
    }

    @Override
    public String toString()
    {
        return "CountrySummary{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
