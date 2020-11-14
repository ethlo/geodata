package com.ethlo.geodata;

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
