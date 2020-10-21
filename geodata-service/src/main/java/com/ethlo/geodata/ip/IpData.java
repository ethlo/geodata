package com.ethlo.geodata.ip;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class IpData implements Externalizable
{
    private int geonameId;
    private long lower;
    private long upper;

    public IpData()
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
    public void writeExternal(final ObjectOutput out) throws IOException
    {
        out.writeInt(geonameId);
        out.writeLong(lower);
        out.writeLong(upper);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException
    {
        geonameId = in.readInt();
        lower = in.readLong();
        upper = in.readLong();
    }
}
