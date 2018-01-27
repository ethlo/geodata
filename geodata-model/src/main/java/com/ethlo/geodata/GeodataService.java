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

import java.util.Collection;
import java.util.List;

import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.View;

public interface GeodataService
{
    GeoLocation findByIp(String ip);

    GeoLocation findById(long geoNameId);

    GeoLocation findWithin(@Valid Coordinates point, int maxDistanceInKilometers);

    public Page<GeoLocationDistance> findNear(Coordinates point, int maxDistanceInKilometers, Pageable pageable);

    byte[] findBoundaries(long id);
    
    byte[] findBoundaries(long id, double maxTolerance);
    
    byte[] findBoundaries(long id, View view);

    Page<GeoLocation> findChildren(long locationId, Pageable pageable);

    Page<Continent> findContinents();

    Page<Country> findCountriesOnContinent(String continentCode, Pageable pageable);
    
    Page<Country> findCountries(Pageable pageable);

    Country findCountryByCode(String countryCode);

    Page<GeoLocation> findChildren(String countryCode, Pageable pageable);

    Country findByPhonenumber(String phoneNumber);

    GeoLocation findParent(long id);
    
    GeoLocation findbyCoordinate(Coordinates point, int distance); 

    boolean isInsideAny(List<Long> locations, long location);

    boolean isOutsideAll(List<Long> locations, long location);

    boolean isLocationInside(long locationId, long suspectedParentId);

    Continent findContinent(String continentCode);

    List<GeoLocation> findByIds(Collection<Long> ids);
    
    Page<GeoLocation> filter(LocationFilter filter, Pageable pageable);
}
