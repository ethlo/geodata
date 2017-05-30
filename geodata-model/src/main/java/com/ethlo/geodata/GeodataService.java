package com.ethlo.geodata;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinate;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountryInfo;
import com.ethlo.geodata.model.GeoLocation;

public interface GeodataService
{
    GeoLocation findByIp(String ip);

    GeoLocation findById(long geoNameId);

    GeoLocation findWithin(Coordinate point, int maxDistanceInKilometers);

    GeoLocation findNear(Coordinate point, int maxDistanceInKilometers);

    byte[] findBoundaries(long id);

    Page<GeoLocation> getChildren(long locationId, Pageable pageable);

    Page<Continent> getContinents();

    Page<CountryInfo> findCountriesOnContinent(String continentCode, Pageable pageable);
    
    Page<CountryInfo> findCountries(Pageable pageable);

    CountryInfo findCountryByCode(String countryCode);

    Page<GeoLocation> getChildren(Country country, Pageable pageable);

    CountryInfo findPhoneLocation(String phoneNumber);

    GeoLocation findLocationByCountryCode(String cc);

    boolean isInsideAny(List<Long> locations, long location);

    boolean isOutsideAll(List<Long> locations, Long location);

    boolean isLocationInside(long locationId, long suspectedParentId);

    boolean locationContains(long parentId, long suspectedChild);

    Continent findContinent(String continentCode);

    List<GeoLocation> findByIds(Collection<Long> ids);
}