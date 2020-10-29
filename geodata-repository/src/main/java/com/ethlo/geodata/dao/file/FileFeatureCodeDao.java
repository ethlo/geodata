package com.ethlo.geodata.dao.file;

/*-
 * #%L
 * geodata-common
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

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.FeatureCodeDao;
import com.ethlo.geodata.model.MapFeature;
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
