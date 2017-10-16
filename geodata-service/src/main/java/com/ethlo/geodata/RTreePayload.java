package com.ethlo.geodata;

import com.vividsolutions.jts.geom.Envelope;

public class RTreePayload
{
    private long id;
    private double area;
    private Envelope envelope;
    
    public RTreePayload(long id, double area, Envelope envelope)
    {
        this.id = id;
        this.area = area;
        this.envelope = envelope;
    }

    public long getId()
    {
        return id;
    }

    public double getArea()
    {
        return area;
    }

    public Envelope getEnvelope()
    {
        return envelope;
    }
}
