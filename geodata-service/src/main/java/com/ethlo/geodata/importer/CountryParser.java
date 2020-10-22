package com.ethlo.geodata.importer;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.util.CloseableIterator;

import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountrySummary;
import com.ethlo.geodata.model.GeoLocation;

public class CountryParser
{
    public static Map<String, Country> build(final CloseableIterator<Map<String, String>> countryFile)
    {
        final Map<String, Country> countryMap = new HashMap<>();
        while (countryFile.hasNext())
        {
            final Map<String, String> c = countryFile.next();
            final GeoLocation l = new GeoLocation();
            l.setId(Integer.parseInt(c.get("geonameid")));
            final String cc = c.get("iso");
            l.setCountry(new CountrySummary().setCode(cc));
            l.setName(c.get("country"));
            final Country country = Country.from(l);
            countryMap.put(country.getCountry().getCode(), country);
        }
        return countryMap;
    }
}
