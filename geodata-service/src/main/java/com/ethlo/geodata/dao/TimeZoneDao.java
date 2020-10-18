package com.ethlo.geodata.dao;

import java.util.Map;

public interface TimeZoneDao
{
    Map<Integer, String> findTimeZones();
}
