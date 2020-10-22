package com.ethlo.geodata.dao.file;

import java.nio.file.Path;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.TimeZoneDao;
import com.ethlo.geodata.util.JsonUtil;

@Repository
public class FileTimeZoneDao implements TimeZoneDao
{
    public static final String TIMEZONES_FILE = "timezones.json";
    private final Path basePath;

    public FileTimeZoneDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.basePath = basePath;
    }

    public Map<Integer, String> load()
    {
        return JsonUtil.read(basePath.resolve(TIMEZONES_FILE), Map.class);
    }

    @Override
    public void save(final Map<String, Integer> timezones)
    {
        JsonUtil.write(basePath.resolve(TIMEZONES_FILE), timezones);
    }
}
