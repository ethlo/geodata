package com.ethlo.geodata.model;

/*-
 * #%L
 * geodata-model
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Country
{
    private final int id;
    private final String name;
    private final String countryCode;
    private final String continentCode;
    private final List<String> languages;

    @JsonCreator
    public Country(@JsonProperty("id") final int id,
                   @JsonProperty("name") final String name,
                   @JsonProperty("country_code") final String countryCode,
                   @JsonProperty("continent") String continentCode,
                   @JsonProperty("languages") List<String> languages)
    {
        this.id = id;
        this.name = name;
        this.countryCode = countryCode;
        this.continentCode = continentCode;
        this.languages = languages;
    }

    public CountrySummary toSummary(String countryCode)
    {
        return new CountrySummary().setId(id).setName(name).setCode(countryCode);
    }

    public int getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public String getCountryCode()
    {
        return countryCode;
    }

    public String getContinentCode()
    {
        return continentCode;
    }

    public List<String> getLanguages()
    {
        return languages;
    }

}
