package com.ethlo.geodata.ip;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.ethlo.geodata.model.CompactSerializable;

public class IpData implements CompactSerializable
{
    private int geonameId;
    private long lower;
    private long upper;

    private IpData()
    {

    }

    public IpData(final int geonameId, final long lower, final long upper)
    {
        this.geonameId = geonameId;
        this.lower = lower;
        this.upper = upper;
    }

    public int getGeonameId()
    {
        return geonameId;
    }

    public long getLower()
    {
        return lower;
    }

    public long getUpper()
    {
        return upper;
    }

    @Override
    public void write(final DataOutputStream out) throws IOException
    {
        out.writeInt(geonameId);
        out.writeLong(lower);
        out.writeLong(upper);
    }

    @Override
    public void read(final DataInputStream in) throws IOException
    {
        geonameId = in.readInt();
        lower = in.readLong();
        upper = in.readLong();
    }
}
