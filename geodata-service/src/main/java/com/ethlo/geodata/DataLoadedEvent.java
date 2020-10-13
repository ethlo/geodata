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

import com.ethlo.geodata.importer.DataType;
import com.ethlo.geodata.importer.Operation;

public class DataLoadedEvent extends ApplicationEvent
{
    private static final long serialVersionUID = 1123581959564040840L;

    private final double progress;
    private final DataType dataType;
    private final Operation operation;
    private final long current;
    private final long total;

    public DataLoadedEvent(Object source, DataType dataType, Operation operation, long current, long total)
    {
        super(source);
        this.dataType = dataType;
        this.operation = operation;
        this.current = current;
        this.total = total;
        this.progress = (double) current / total;
    }

    public DataType getDataType()
    {
        return dataType;
    }

    public Operation getOperation()
    {
        return this.operation;
    }

    public double getProgress()
    {
        return progress;
    }

    public boolean isFinished()
    {
        return current >= total;
    }

    public long getCurrent()
    {
        return current;
    }

    public long getTotal()
    {
        return total;
    }
}
