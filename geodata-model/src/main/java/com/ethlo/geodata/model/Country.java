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

import java.io.Serializable;
import java.util.List;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "code", "name", "population"})
public class Country extends GeoEntity implements Serializable
{
    private static final long serialVersionUID = -4692269307453103789L;

    /**
     * The official languages of this country
     */
    @NotNull
    private List<String> languages;

    /**
     * The calling code
     */
    private Integer callingCode;

    /**
     * The estimated population
     */
    private Long population;

    /**
     * The 2-letter ISO country code
     */
    @NotNull
    private String code;

    public CountrySummary toSummary(String countryCode)
    {
        return new CountrySummary().setId(getId()).setName(getName()).setCode(countryCode);
    }

    public List<String> getLanguages()
    {
        return languages;
    }

    public Country setLanguages(List<String> languages)
    {
        this.languages = languages;
        return this;
    }

    public Integer getCallingCode()
    {
        return callingCode;
    }

    public Country setCallingCode(Integer callingCode)
    {
        this.callingCode = callingCode;
        return this;
    }

    public Long getPopulation()
    {
        return population;
    }

    public Country setPopulation(Long population)
    {
        this.population = population;
        return this;
    }

    public String getCode()
    {
        return this.code;
    }

    public Country setCode(String code)
    {
        this.code = code;
        return this;
    }
}
