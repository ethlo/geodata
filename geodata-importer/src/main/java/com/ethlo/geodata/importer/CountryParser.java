package com.ethlo.geodata.importer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.util.CloseableIterator;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.model.Coordinates;
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
            final String continentCode = c.get("continent");
            final List<String> languages = new ArrayList<>(StringUtils.commaDelimitedListToSet(c.get("languages")));
            final Country country = new Country(id, name, countryCode, continentCode, languages);
            countryMap.put(countryCode, country);
        }
        return countryMap;
    }
}
