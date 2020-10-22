package com.ethlo.geodata.model;

/*-
 * #%L
 * geodata-model
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

public class RawLocation implements CompactSerializable
{
    private static final long serialVersionUID = -4918529556780291151L;

    private int id;
    private String name;
    private String countryCode;
    private double lat;
    private double lng;
    private int mapFeatureId;
    private long population;
    private int timeZoneId;
    private int elevation;

    public RawLocation()
    {
    }

    public RawLocation(final int id, final String name, final String countryCode, final Coordinates coordinates, final int mapFeatureId, final long population, final int timeZoneId)
    {
        this.id = id;
        this.name = name;
        this.countryCode = countryCode;
        this.lat = coordinates.getLat();
        this.lng = coordinates.getLng();
        this.mapFeatureId = mapFeatureId;
        this.population = population;
        this.timeZoneId = timeZoneId;
    }

    public int getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public String getCountryCode()
    {
        return countryCode;
    }

    public Coordinates getCoordinates()
    {
        return Coordinates.from(lat, lng);
    }

    public int getMapFeatureId()
    {
        return mapFeatureId;
    }

    public long getPopulation()
    {
        return population;
    }

    public int getTimeZoneId()
    {
        return timeZoneId;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RawLocation location = (RawLocation) o;
        return id == location.id &&
                Double.compare(location.lat, lat) == 0 &&
                Double.compare(location.lng, lng) == 0 &&
                mapFeatureId == location.mapFeatureId &&
                population == location.population &&
                timeZoneId == location.timeZoneId &&
                name.equals(location.name) &&
                Objects.equals(countryCode, location.countryCode);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, name);
    }

    @Override
    public void write(final DataOutputStream out) throws IOException
    {
        out.writeInt(id);
        out.writeUTF(name);
        out.writeUTF(countryCode != null ? countryCode : "");
        out.writeDouble(lat);
        out.writeDouble(lng);
        out.writeInt(mapFeatureId);
        out.writeLong(population);
        out.writeInt(timeZoneId);
    }

    @Override
    public void read(final DataInputStream in) throws IOException
    {
        id = in.readInt();
        name = in.readUTF();
        final String cc = in.readUTF();
        countryCode = "".equals(cc) ? null : cc;
        lat = in.readDouble();
        lng = in.readDouble();
        mapFeatureId = in.readInt();
        population = in.readLong();
        timeZoneId = in.readInt();
    }
}
