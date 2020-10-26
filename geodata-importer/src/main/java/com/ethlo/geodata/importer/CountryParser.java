package com.ethlo.geodata.importer;

/*-
 * #%L
 * geodata-importer
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.util.CloseableIterator;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.model.Country;

public class CountryParser
{
    public static Map<String, Country> build(final CloseableIterator<Map<String, String>> countryFile)
    {
        final Map<String, Country> countryMap = new HashMap<>();
        while (countryFile.hasNext())
        {
            final Map<String, String> c = countryFile.next();
            final int id = Integer.parseInt(c.get("geonameid"));
            final String countryCode = c.get("iso");
            final String name = c.get("country");
            final String phone = c.get("phone");
            final String continentCode = c.get("continent");
            final List<String> languages = new ArrayList<>(StringUtils.commaDelimitedListToSet(c.get("languages")));
            final Country country = new Country(id, name, countryCode, continentCode, languages, phone);
            countryMap.put(countryCode, country);
        }
        return countryMap;
    }
}
