package com.ethlo.geodata.util;


import com.goebl.simplify.PointExtractor;
import com.vividsolutions.jts.geom.Coordinate;

public class JtsPointExtractor implements PointExtractor<Coordinate>
{
    public static final double MULIPLICATOR = 100_000_000;

	@Override
    public double getX(Coordinate point)
    {
        return point.x * MULIPLICATOR;
    }

    @Override
    public double getY(Coordinate point)
    {
        return point.y * MULIPLICATOR;
    }
}

