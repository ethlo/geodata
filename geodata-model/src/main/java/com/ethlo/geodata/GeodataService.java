package com.ethlo.geodata;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.GeoLocation;

public interface GeodataService
{
    GeoLocation findByIp(String ip);

    GeoLocation findById(long geoNameId);

    GeoLocation findWithin(Coordinates point, int maxDistanceInKilometers);

    GeoLocation findNear(Coordinates point, int maxDistanceInKilometers);

    byte[] findBoundaries(long id);

    Page<GeoLocation> getChildren(long locationId, Pageable pageable);

    Page<Continent> findContinents();

    Page<Country> findCountriesOnContinent(String continentCode, Pageable pageable);
    
    Page<Country> findCountries(Pageable pageable);

    Country findCountryByCode(String countryCode);

    Page<GeoLocation> findChildren(String countryCode, Pageable pageable);

    Country findByPhonenumber(String phoneNumber);

    GeoLocation findLocationByCountryCode(String cc);
    
    GeoLocation findParent(long id);
    
    GeoLocation findbyCoordinate(Coordinates point); 

    boolean isInsideAny(List<Long> locations, long location);

    boolean isOutsideAll(List<Long> locations, long location);

    boolean isLocationInside(long locationId, long suspectedParentId);

    boolean locationContains(long parentId, long suspectedChild);

    Continent findContinent(String continentCode);

    List<GeoLocation> findByIds(Collection<Long> ids);
}