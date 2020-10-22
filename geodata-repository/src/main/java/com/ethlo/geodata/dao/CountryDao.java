package com.ethlo.geodata.dao;

import java.util.Map;

import com.ethlo.geodata.model.Country;

public interface CountryDao
{
    void save(Map<String, Country> countries);

    Map<String, Country> load();
}
