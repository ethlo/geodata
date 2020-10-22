package com.ethlo.geodata;

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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GeoConstants
{
    public static final Integer EARTH_ID = 6295630;
    public static final String CONTINENT_LEVEL_FEATURE = "L.CONT";
    public static final String ADMINISTRATIVE_ZONE = "A.ZN";
    public static final List<String> ADMINISTRATIVE_LEVEL_FEATURES = Arrays.asList("A.ADM1", "A.ADM2", "A.ADM3", "A.ADM4", "A.ADM5");
    public static final List<String> COUNTRY_LEVEL_FEATURES = Arrays.asList("A.PCLI", "A.PCLIX", "A.PCLS", "A.PCLF", "A.PCLD");
    public static final Set<String> ADMINISTRATIVE_OR_ABOVE = new LinkedHashSet<>();
    public static Map<String, Integer> CONTINENTS = Collections.unmodifiableMap(new LinkedHashMap<>()
    {
        {
            put("AF", 6255146);
            put("AS", 6255147);
            put("EU", 6255148);
            put("NA", 6255149);
            put("OC", 6255151);
            put("SA", 6255150);
            put("AN", 6255152);
        }
    });

    static
    {
        ADMINISTRATIVE_OR_ABOVE.add(CONTINENT_LEVEL_FEATURE);
        ADMINISTRATIVE_OR_ABOVE.add(ADMINISTRATIVE_ZONE);
        ADMINISTRATIVE_OR_ABOVE.addAll(COUNTRY_LEVEL_FEATURES);
        ADMINISTRATIVE_OR_ABOVE.addAll(ADMINISTRATIVE_LEVEL_FEATURES);
    }
}
