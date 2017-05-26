package com.ethlo.geodata.util;

public class Assert
{
    public static void notNull(Object obj, String message)
    {
        if (obj == null)
        {
            throw new IllegalArgumentException(message);
        }
    }
}
