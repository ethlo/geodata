package com.ethlo.geodata.model;

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
