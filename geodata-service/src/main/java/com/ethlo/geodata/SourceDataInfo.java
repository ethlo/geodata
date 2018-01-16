package com.ethlo.geodata;

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
}
