package com.ethlo.geodata.dao.file;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.model.MapFeature;
import com.ethlo.geodata.dao.FeatureCodeDao;
import com.ethlo.geodata.util.JsonUtil;

@Repository
public class FileFeatureCodeDao implements FeatureCodeDao
{
    public static final String FEATURE_CODES_FILE = "feature_codes.json";
    private final Path basePath;

    public FileFeatureCodeDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.basePath = basePath;
    }

    public Map<Integer, MapFeature> load()
    {
        final Map<String, Integer> featureMap = JsonUtil.read(basePath.resolve(FEATURE_CODES_FILE), Map.class);
        return featureMap.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getValue, e ->
                {
                    final String[] parts = e.getKey().split("\\.");
                    return new MapFeature(parts[0], parts[1]);
                }));
    }

    @Override
    public void save(final Map<String, Integer> featureCodes)
    {
        JsonUtil.write(basePath.resolve(FEATURE_CODES_FILE), featureCodes);
    }
}
