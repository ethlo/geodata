package com.ethlo.geodata.model;

public class RawLocation
{
    private final int id;
    private final String name;
    private final String countryCode;
    private final Coordinates coordinates;
    private final int mapFeatureId;
    private final long population;
    private final int timeZoneId;

    public RawLocation(final int id, final String name, final String countryCode, final Coordinates coordinates, final int mapFeatureId, final long population, final int timeZoneId)
    {
        this.id = id;
        this.name = name;
        this.countryCode = countryCode;
        this.coordinates = coordinates;
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
        return coordinates;
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
}
