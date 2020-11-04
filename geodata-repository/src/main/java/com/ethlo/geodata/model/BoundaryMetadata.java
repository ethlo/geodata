package com.ethlo.geodata.model;

import org.locationtech.jts.geom.Envelope;

public class BoundaryMetadata implements IntIdentifiable
{
    private final int id;
    private final Envelope mbr;
    private final double area;
    private final int subDivideIndex;

    public BoundaryMetadata(final int id, final int subDivideIndex, final Envelope mbr, final double area)
    {
        this.id = id;
        this.subDivideIndex = subDivideIndex;
        this.mbr = mbr;
        this.area = area;
    }

    public Envelope getMbr()
    {
        return mbr;
    }

    public double getArea()
    {
        return area;
    }

    @Override
    public int getId()
    {
        return id;
    }

    public int getSubDivideIndex()
    {
        return subDivideIndex;
    }
}
