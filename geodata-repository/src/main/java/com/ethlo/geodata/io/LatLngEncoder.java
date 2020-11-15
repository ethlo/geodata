package com.ethlo.geodata.io;

/*-
 * #%L
 * geodata-repository
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

import com.ethlo.geodata.model.Coordinates;

/**
 * Simple, potentially lossy encoder that uses 3+3 bytes for lat/lng. Typical resolution of about 11 m.
 */
public class LatLngEncoder
{
    public static byte[] encode(Coordinates coordinate)
    {
        final double lat = coordinate.getLat();
        final double lng = coordinate.getLng();

        // 0-180
        final int latVal = (int) ((lat < 0 ? 180 + lat : lat) * 10_000);

        // 0-360
        final int lngVal = (int) ((lng < 0 ? 360 + lng : lng) * 10_000);

        final byte[] data = new byte[6];
        unsigned24BitInt(latVal, data, 0);
        unsigned24BitInt(lngVal, data, 3);
        return data;
    }

    private static void unsigned24BitInt(final int value, byte[] data, int offset)
    {
        int val = value;
        for (int i = 2; i >= 0; --i)
        {
            data[i + offset] = (byte) ((int) (val & 255L));
            val >>= 8;
        }
    }

    public static int decodeUnsigned(byte[] data, int offset, int length)
    {
        int l = 0;
        for (int i = 0; i < length - 1; ++i)
        {
            byte b = data[i + offset];
            l |= (long) b & 255L;
            l <<= 8;
        }
        byte b = data[offset + length - 1];
        l |= (long) b & 255L;
        return l;
    }

    public static Coordinates decode(byte[] data)
    {
        final double lat = decodeUnsigned(data, 0, 3) / 10_000D;
        final double latSigned = lat > 90 ? lat - 180 : lat;

        final double lng = decodeUnsigned(data, 3, 3) / 10_000D;
        final double lngSigned = lng > 180 ? lng - 360 : lng;

        return Coordinates.from(latSigned, lngSigned);
    }
}
