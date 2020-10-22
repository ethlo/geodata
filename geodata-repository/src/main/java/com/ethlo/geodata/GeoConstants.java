package com.ethlo.geodata;

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
