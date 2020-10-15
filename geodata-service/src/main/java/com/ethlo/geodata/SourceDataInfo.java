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

import java.util.Date;

import com.ethlo.geodata.importer.DataType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SourceDataInfo
{
    private final DataType dataType;
    private final long count;
    private final Date lastModified;
    
    @JsonCreator
    public SourceDataInfo(@JsonProperty("alias") DataType dataType, @JsonProperty("count") long count, @JsonProperty("lastModified") Date lastModified)
    {
        this.dataType = dataType;
        this.count = count;
        this.lastModified = lastModified;
    }

    public DataType getDataType()
    {
        return dataType;
    }

    public long getCount()
    {
        return count;
    }

    public Date getLastModified()
    {
        return lastModified;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dataType == null) ? 0 : dataType.hashCode());
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
        SourceDataInfo other = (SourceDataInfo) obj;
        if (dataType == null)
        {
            if (other.dataType != null)
                return false;
        }
        else if (!dataType.equals(other.dataType))
            return false;
        return true;
    }

    @Override
    public String toString()
    {
        return "SourceDataInfo [dataType=" + dataType + ", count=" + count + ", lastModified=" + lastModified + "]";
    }
}