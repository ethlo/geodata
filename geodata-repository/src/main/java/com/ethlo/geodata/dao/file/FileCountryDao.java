package com.ethlo.geodata.dao.file;

import java.nio.file.Path;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.CountryDao;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.util.JsonUtil;

@Repository
public class FileCountryDao implements CountryDao
{
    private final String FILE = "countries.json";
    private final Path basePath;

    public FileCountryDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.basePath = basePath;
    }

    public Map<String, Country> load()
    {
        return JsonUtil.read(basePath.resolve(FILE), Map.class);
    }

    @Override
    public void save(final Map<String, Country> featureCodes)
    {
        JsonUtil.write(basePath.resolve(FILE), featureCodes);
    }
}
