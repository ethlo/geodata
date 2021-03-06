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

import java.time.OffsetDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SourceDataInfo
{
    private final int count;
    private final OffsetDateTime lastModified;
    private final String type;

    @JsonCreator
    public SourceDataInfo(@JsonProperty("type") String type, @JsonProperty("count") int count, @JsonProperty("lastModified") OffsetDateTime lastModified)
    {
        this.type = type;
        this.count = count;
        this.lastModified = lastModified;
    }

    public int getCount()
    {
        return count;
    }

    public OffsetDateTime getLastModified()
    {
        return lastModified;
    }

    public String getType()
    {
        return type;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SourceDataInfo that = (SourceDataInfo) o;
        return type.equals(that.type);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(type);
    }

    @Override
    public String toString()
    {
        return "type=" + type +
                ", count=" + count +
                ", lastModified=" + lastModified;
    }
}