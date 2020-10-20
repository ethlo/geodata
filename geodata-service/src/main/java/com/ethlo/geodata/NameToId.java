package com.ethlo.geodata;

import java.io.Serializable;

public class NameToId implements Serializable
{
    private final int id;
    private final String name;

    public NameToId(final int id, final String name)
    {
        this.id = id;
        this.name = name;
    }

    public int getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }
}
