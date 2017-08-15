package com.ethlo.geodata;

/*-
 * #%L
 * geodata-model
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    
    Page<GeoLocation> findByName(String name, Pageable pageable);
}
