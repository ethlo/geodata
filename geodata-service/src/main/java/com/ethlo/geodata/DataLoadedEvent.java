package com.ethlo.geodata;

/*-
 * #%L
 * Geodata service
 * %%
 * Copyright (C) 2017 - 2018 Morten Haraldsen (ethlo)
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

import org.springframework.context.ApplicationEvent;

public class DataLoadedEvent extends ApplicationEvent
{
    private static final long serialVersionUID = 1123581959564040840L;

    private final String name;
    private final double progress;
    private final boolean finished;
    
    public DataLoadedEvent(Object source, String name)
    {
        this(source, name, 0D);
    }
    
    public DataLoadedEvent(Object source, String name, double progress)
    {
        super(source);
        this.name = name;
        this.progress = progress;
        this.finished = progress == 1D;
    }

    public String getName()
    {
        return name;
    }

    public double getProgress()
    {
        return progress;
    }
    
    public boolean isFinished()
    {
        return finished;
    }
}
