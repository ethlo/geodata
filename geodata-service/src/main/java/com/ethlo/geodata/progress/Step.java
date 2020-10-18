package com.ethlo.geodata.progress;

/*-
 * #%L
 * Geodata service
 * %%
 * Copyright (C) 2017 - 2020 Morten Haraldsen (ethlo)
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

import java.time.Duration;
import java.time.OffsetDateTime;

public class Step
{
    private final String name;
    private Integer total;
    private int progress;
    private final OffsetDateTime started = OffsetDateTime.now();
    private OffsetDateTime ended;

    public Step(final String name, final Integer total)
    {
        this.name = name;
        this.total = total;
    }

    public String getName()
    {
        return name;
    }

    public int getProgress()
    {
        return progress;
    }

    public Step setProgress(final int progress)
    {
        this.progress = progress;
        return this;
    }

    public Float getProgressPercentage()
    {
        final int progress = getProgress();
        final Integer total = getTotal();
        if (total == null)
        {
            return null;
        }
        return (progress / (float) total) * 100;
    }

    public Integer getTotal()
    {
        return total;
    }

    public void setTotal(int total)
    {
        this.total = total;
    }

    public OffsetDateTime getStart()
    {
        return started;
    }

    public OffsetDateTime getEnd()
    {
        return ended;
    }

    public Duration getElapsed()
    {
        return Duration.between(started, ended != null ? ended : OffsetDateTime.now());
    }

    public void end()
    {
        this.ended = OffsetDateTime.now();
    }
}
