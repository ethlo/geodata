package com.ethlo.geodata.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ethlo.geodata.model.CoordinateDto;
import com.ethlo.geodata.model.LocationDto;

import io.swagger.annotations.ApiOperation;

@RestController
@Validated
@RequestMapping(value="/geolocation", produces = "application/json")
public class GeoLocationController
{           
    @ApiOperation(nickname="findLocationByIp", value="Return the approximate location for the IP-address specified")
    @GetMapping("/v1/locations/ip")
    public LocationDto findLocationByIp(@RequestParam("ip") String ipAddress)
    {
        return null;
    }
    
    @ApiOperation(nickname="findLocation", value="Return the approximate location for the latitude/longitude coordinate provided")
    @GetMapping("/v1/locations/coordinates")
    public LocationDto findLocation(@RequestParam(name="lng") double lng, @RequestParam(name="lat") double lat)
    {
        return null;
    }
    
    @ApiOperation(nickname="findBoundary", value="Return a set of latitude/longitude coordinates suitable for drawing a polygon on a map")
    @GetMapping("/v1/locations/{id}/boundary")
    public List<List<CoordinateDto>> findBoundary(@PathVariable(name="id") long locationId)
    {
        /*
            final List<List<CoordinateDto>> retVal = new LinkedList<>();
            for (Geometry b : boundary.get())
            {
                final List<CoordinateDto> points = new LinkedList<>();
                b.getPoints().forEach((p)->
                {
                    final CoordinateDto dto = new CoordinateDto();
                    dto.setLng(p.getX());
                    dto.setLat(p.getY());
                    points.add(dto);
                });
                retVal.add(points);
            }
            return retVal;
        */
        return null;
    }
    
    @ApiOperation(nickname="findLocation", value="Return the location by id")
    @GetMapping("/v1/locations/{id}")
    public LocationDto findLocation(@PathVariable(name="id") long locationId)
    {
        return null;
    }    
    
    @ApiOperation(nickname="findChildren", value="Return any accessible child locations of the specified location id")
    @GetMapping(value = "/v1/locations/{id}/children")
    public List<LocationDto> findChildren(@PathVariable(name="id") long locationId)
    {
        //return geoLocationsService.getChildren(locationId).stream().map(l->modelMapper.from(l)).collect(Collectors.toList());
        return null;
    }
    
    @RequestMapping(value = "/v1/continents", method = RequestMethod.GET)
    public List<LocationDto> listContinents() 
    {
        //return geoLocationsService.getContinents().stream().map(l -> modelMapper.from(l)).collect(Collectors.toList());
        return null;
    }

    @GetMapping("/v1/continents/{continent}/countries")
    public List<LocationDto> listCountries(@PathVariable("continent") String continentName)
    {
        /*
        final List<FixedLocation> continents = geoLocationsService.getContinents();
        for (FixedLocation continent : continents)
        {
            if (continent.getName().equalsIgnoreCase(continentName))
            {
                return geoLocationsService.getLocationsInside(continent.getId())
                    .stream()
                    .map(l -> modelMapper.from(l))
                    .collect(Collectors.toList());
            }
        }
        throw new EmptyResultDataAccessException("Unknown continent name: " + continentName, 1);
        */
        return null;
    }

    @GetMapping("/v1/countries/{countryCode}/children")
    public List<LocationDto> listStates(@PathVariable("countryCode") String countryCode)
    {
        /*
        final List<FixedLocation> continents = geoLocationsService.getContinents();
        for (FixedLocation continent : continents)
        {
            final List<FixedLocation> countries = geoLocationsService.getLocationsInside(continent.getId());
            final List<FixedLocation> candidates = countries.stream().filter(l -> countryCode.equalsIgnoreCase(l.getCountryCode())).collect(Collectors.toList());
            if (! candidates.isEmpty())
            {
                return geoLocationsService.getLocationsInside(candidates.get(0).getId())
                    .stream()
                    .map(l -> modelMapper.from(l))
                    .collect(Collectors.toList());
            }
        }
        throw new EmptyResultDataAccessException("Could not find country with code " + countryCode, 1);
        */
        return null;
    }
}
