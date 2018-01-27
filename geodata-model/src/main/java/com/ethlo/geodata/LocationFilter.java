package com.ethlo.geodata;

import java.util.Collections;
import java.util.LinkedList;

/*-
 * #%L
 * geodata-model
 * %%
 * Copyright (C) 2017 - 2018 Morten Haraldsen (ethlo)
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
import java.util.stream.Collectors;

public class LocationFilter
{
    private String name;
    private List<String> countryCodes = new LinkedList<>();
    private List<String> featureClasses = new LinkedList<>();
    private List<String> featureCodes = new LinkedList<>();

    public String getName()
    {
        return name;
    }

    public LocationFilter setName(String name)
    {
        this.name = name;
        return this;
    }

    public List<String> getCountryCodes()
    {
        return countryCodes;
    }

    public LocationFilter setCountryCodes(List<String> countryCodes)
    {
        this.countryCodes = toLowerCase(countryCodes);
        return this;
    }

    public List<String> getFeatureClasses()
    {
        return featureClasses;
    }

    public LocationFilter setFeatureClasses(List<String> featureClasses)
    {
        this.featureClasses = toLowerCase(featureClasses);
        return this;
    }

    public List<String> getFeatureCodes()
    {
        return featureCodes;
    }

    public LocationFilter setFeatureCodes(List<String> featureCodes)
    {
        this.featureCodes = toLowerCase(featureCodes);
        return this;
    }

    private List<String> toLowerCase(List<String> list)
    {
        if (list == null)
        {
            return Collections.emptyList();
        }
        else if (list.isEmpty())
        {
            return list;
        }
        return list.stream().map(String::toLowerCase).collect(Collectors.toList());
    }
}
