package com.ethlo.geodata;

import org.springframework.context.ApplicationEvent;

public class DataLoadedEvent extends ApplicationEvent
{
    private static final long serialVersionUID = 1123581959564040840L;

    private final String name;
    private final boolean finished;
    
    public DataLoadedEvent(Object source, String name)
    {
        this(source, name, false);
    }
    
    public DataLoadedEvent(Object source, String name, boolean finished)
    {
        super(source);
        this.name = name;
        this.finished = finished;
    }

    public String getName()
    {
        return name;
    }

    public boolean isFinished()
    {
        return finished;
    }
}
