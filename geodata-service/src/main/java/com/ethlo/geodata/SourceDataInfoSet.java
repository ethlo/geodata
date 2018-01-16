package com.ethlo.geodata;

import java.util.LinkedHashSet;
import java.util.Optional;

import com.ethlo.geodata.importer.DataType;

public class SourceDataInfoSet extends LinkedHashSet<SourceDataInfo>
{
    private static final long serialVersionUID = 8084440663064395217L;

    public SourceDataInfo get(DataType type)
    {
        final Optional<SourceDataInfo> entry = stream().filter(e->e.getDataType() == type).findFirst();
        return entry.isPresent() ? entry.get() : null;
    }
}
