Geodata
=========================
[![Maven central](https://maven-badges.herokuapp.com/maven-central/com.ethlo.geodata/geodata/badge.svg)](http://repo1.maven.org/maven2/com/ethlo/geodata/)
[![License: LGPL v3](https://img.shields.io/badge/License-LGPL%20v3-blue.svg)](https://www.gnu.org/licenses/lgpl-3.0)
[![Build Status](https://travis-ci.org/ethlo/geodata.svg?branch=master)](https://travis-ci.org/ethlo/geodata)

Simple library that imports and manages Geonames and Geo-Lite2 data using Open-Source software. 

### Design choices
The system is built for high performance, thus:
* It is somewhat memory hungry (recommended 5GB heap) as all data is loaded in in-memory structures. 
* Loading the data from file-to-memory is fast and very well optimized, however as there are several million locations indexed (usually around 60-70 seconds)

### License note
I would very much like to release this under an even less restrictive license, but since the project relies on JTS, I unfortunately cannot use Apache2 licensing.

### Usage
This project is distributed as both a service and a RESTful web-service.

### Web-service API doc
TBD

### Service methods overview
```
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
```
