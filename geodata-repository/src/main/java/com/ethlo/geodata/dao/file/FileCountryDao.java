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
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.CountryDao;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;

@Repository
public class FileCountryDao implements CountryDao
{
    private final String FILE = "countries.json";
    private final Path basePath;

    public FileCountryDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.basePath = basePath;
    }

    public List<Country> load()
    {
        return JsonUtil.read(basePath.resolve(FILE), new TypeReference<>()
        {
        });
    }

    @Override
    public void save(final Collection<Country> featureCodes)
    {
        JsonUtil.write(basePath.resolve(FILE), featureCodes);
    }
}
