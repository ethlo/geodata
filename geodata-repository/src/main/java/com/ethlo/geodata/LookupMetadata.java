package com.ethlo.geodata;

import org.locationtech.jts.geom.Envelope;

import com.ethlo.geodata.model.GeoLocation;

public class LookupMetadata
{
    private final GeoLocation location;
    private final int subdivideIndex;
    private final Envelope envelope;

    public LookupMetadata(final GeoLocation location, final int subdivideIndex, final Envelope envelope)
    {
        this.location = location;
        this.subdivideIndex = subdivideIndex;
        this.envelope = envelope;
    }

    public GeoLocation getLocation()
    {
        return location;
    }

    public int getSubdivideIndex()
    {
        return subdivideIndex;
    }

    public Envelope getEnvelope()
    {
        return envelope;
    }
}
