package com.ethlo.geodata.dao;

import java.util.Map;

import org.springframework.data.domain.Pageable;

import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.RawLocation;

public interface ReverseGeocodingDao
{
    Map<Integer, Double> findNearest(Coordinates point, int maxDistance, Pageable pageable);

    RawLocation findContaining(Coordinates point, int range);
}
