package com.ethlo.geodata.dao;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.SourceDataInfoSet;
import com.ethlo.geodata.util.JsonUtil;

@Repository
public class FileMetaDao implements MetaDao
{
    private final String FILE = "metadata.json";
    private final Path basePath;

    public FileMetaDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.basePath = basePath;
    }

    @Override
    public SourceDataInfoSet load()
    {
        return JsonUtil.read(basePath.resolve(FILE), SourceDataInfoSet.class);
    }

    @Override
    public void save(final SourceDataInfoSet dataInfoSet)
    {
        JsonUtil.write(basePath.resolve(FILE), dataInfoSet);
    }
}
