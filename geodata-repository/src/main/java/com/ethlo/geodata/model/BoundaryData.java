package com.ethlo.geodata.model;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

public class BoundaryData extends BoundaryMetadata
{
    private final Geometry geometry;

    public BoundaryData(final int id, final int subDivideIndex, final Envelope mbr, final double area, final Geometry geometry)
    {
        super(id, subDivideIndex, mbr, area);
        this.geometry = geometry;
    }

    public Geometry getGeometry()
    {
        return geometry;
    }
}
