package com.ethlo.geodata;

/*-
 * #%L
 * Geodata service
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

    public static Integer getInt(Map<?,?> map, String key)
    {
        final Object obj = map.get(key);
        if (obj == null)
        {
            return null;
        }
        return Integer.valueOf(obj.toString());
    }

}
