package com.ethlo.geodata;

import java.util.Map;

public class MapUtils
{
    private MapUtils()
    {
    }
    
    public static Long getLong(Map<?,?> map, Object key)
    {
        final Object obj = map.get(key);
        if (obj == null)
        {
            return null;
        }
        return Long.valueOf(obj.toString());
    }

    public static Double getDouble(Map<?,?> map, String key)
    {
        final Object obj = map.get(key);
        if (obj == null)
        {
            return null;
        }
        return Double.valueOf(obj.toString());
    }
    
    public static String getString(Map<?,?> map, String key)
    {
        final Object obj = map.get(key);
        if (obj == null)
        {
            return null;
        }
        return obj.toString();
    }
}
