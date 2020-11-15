package com.ethlo.geodata.io;

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