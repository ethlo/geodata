package com.ethlo.geodata.dao;

import com.ethlo.geodata.SourceDataInfoSet;

public interface MetaDao
{
    SourceDataInfoSet load();

    void save(SourceDataInfoSet dataInfoSet);
}
