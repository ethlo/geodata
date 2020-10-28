package com.ethlo.geodata;

/*-
 * #%L
 * geodata-model
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
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

import java.net.InetAddress;
import java.util.Collection;
import java.util.List;

import javax.validation.Valid;

import org.locationtech.jts.geom.Geometry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.View;

public interface GeodataService
{
    GeoLocation findByIp(InetAddress ip);

    GeoLocation findById(int geoNameId);

    GeoLocation findWithin(@Valid Coordinates point, int maxDistanceInKilometers);

    Page<GeoLocationDistance> findNear(Coordinates point, int maxDistanceInKilometers, Pageable pageable);

    byte[] findBoundaries(int id);

    byte[] findBoundaries(int id, double maxTolerance);

    Geometry findBoundaries(int id, View view);

    Page<GeoLocation> findChildren(int locationId, Pageable pageable);

    Page<Continent> findContinents();

    Page<Country> findCountriesOnContinent(String continentCode, Pageable pageable);

    Page<Country> findCountries(Pageable pageable);

    Country findCountryByCode(String countryCode);

    Page<GeoLocation> findChildren(String countryCode, Pageable pageable);

    Country findByPhoneNumber(String phoneNumber);

    GeoLocation findParent(int id);

    GeoLocation findByCoordinate(Coordinates point, int distance);

    boolean isInsideAny(List<Integer> locations, int location);

    boolean isOutsideAll(List<Integer> locations, int location);

    boolean isLocationInside(int locationId, int suspectedParentId);

    Continent findContinent(String continentCode);

    List<GeoLocation> findByIds(Collection<Integer> ids);

    Slice<GeoLocation> findByName(String name, Pageable pageable);

    void load(LoadProgressListener loadProgressListener);

    List<GeoLocation> findPath(int id);
}