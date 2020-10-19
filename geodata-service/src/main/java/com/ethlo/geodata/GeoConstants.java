package com.ethlo.geodata;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class GeoConstants
{
    public static final Integer EARTH_ID = 6295630;

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
}
