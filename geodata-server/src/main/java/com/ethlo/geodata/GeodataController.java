package com.ethlo.geodata;

/*-
 * #%L
 * geodata-server
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


import java.io.IOException;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.View;

@RestController
@Validated
@RequestMapping(value = "/", produces = "application/json")
public class GeodataController implements GeodataService
{
    @Autowired
    private GeodataService geodataService;

    @Autowired
    private GeoMetaService geoMetaService;

    /**
     * Find locations matching the specified filter
     *
     * @param filter
     * @param pageable
     * @return
     */
    /*@GetMapping("/v1/locations")
    @Override
    public Page<GeoLocation> filter(LocationFilter filter, Pageable pageable)
    {
        return geodataService.filter(filter, pageable);
    }
    */

    /**
     * Find the approximate location by IP-address
     *
     * @param ipAddress The IP-address to look up location for
     */
    @GetMapping("/v1/locations/ip/{ip:.+}")
    @Override
    public GeoLocation findByIp(@PathVariable("ip") String ipAddress)
    {
        final GeoLocation location = geodataService.findByIp(ipAddress);
        return notNull(location, "No location found for IP address " + ipAddress);
    }

    /**
     * Find country by phone number's dialing code
     */
    @GetMapping("/v1/locations/phone/{phone}")
    @Override
    public Country findByPhonenumber(@PathVariable("phone") String phoneNumber)
    {
        final Country country = geodataService.findByPhonenumber(phoneNumber);
        return notNull(country, "No location found for phone number " + phoneNumber);
    }

    /**
     * Find the smallest location boundary that contains the specified coordinates
     */
    @GetMapping("/v1/locations/contains")
    @Override
    public GeoLocation findWithin(Coordinates coordinates, @RequestParam(name = "maxDistance", required = true) int maxDistance)
    {
        final GeoLocation location = geodataService.findWithin(coordinates, maxDistance);
        return notNull(location, "No location found containing " + coordinates);
    }

    /**
     * Get boundaries for the location in WKB (Well-Known-Text) format
     */
    @GetMapping("/v1/locations/{id}/boundaries.wkb")
    @Override
    public byte[] findBoundaries(@PathVariable(name = "id") int locationId)
    {
        final byte[] boundaries = geodataService.findBoundaries(locationId);
        return notNull(boundaries, "No boundaries found for location " + locationId);
    }

    /**
     * Fetch dynamically simplified boundaries based on the max tolerance allowed in meters
     */
    @GetMapping("/v1/locations/{id}/simpleboundaries.wkb")
    @Override
    public byte[] findBoundaries(@PathVariable(name = "id") int id, @RequestParam(name = "maxTolerance", required = true) double maxTolerance)
    {
        final byte[] boundaries = geodataService.findBoundaries(id, maxTolerance);
        return notNull(boundaries, "No boundaries found for location " + id);
    }

    /**
     * Fetch dynamically simplified boundaries based on the max tolerance allowed (in meters)
     */
    @GetMapping("/v1/locations/{id}/simpleboundaries")
    public void findBoundariesSimple(@PathVariable(name = "id") int locationId, double maxTolerance, HttpServletResponse resp) throws IOException
    {
        final byte[] boundaries = geodataService.findBoundaries(locationId, maxTolerance);
        outputGeoJson(boundaries, resp, locationId);
    }

    @GetMapping("/v1/locations/{id}/boundaries")
    public void findBoundaries(@PathVariable(name = "id") int locationId, HttpServletResponse resp) throws IOException
    {
        final byte[] wkb = geodataService.findBoundaries(locationId);
        outputGeoJson(wkb, resp, locationId);
    }

    private void outputGeoJson(byte[] wkb, HttpServletResponse resp, long locationId) throws IOException
    {
        if (wkb != null)
        {
            final WKBReader r = new WKBReader();
            try
            {
                final Geometry geometry = r.read(wkb);
                final GeoJsonWriter w = new GeoJsonWriter();
                w.write(geometry, resp.getWriter());
            }
            catch (ParseException exc)
            {
                throw new DataAccessResourceFailureException(exc.getMessage(), exc);
            }
        }
        else
        {
            notNull(wkb, "No boundary data found for location " + locationId);
        }
    }

    /**
     * Find location by its geonames.org ID
     */
    @GetMapping("/v1/locations/{id}")
    @Override
    public GeoLocation findById(@PathVariable(name = "id") int locationId)
    {
        final GeoLocation location = geodataService.findById(locationId);
        return notNull(location, "No location found for ID " + locationId);
    }

    /**
     * Find the location that is the specified locations parent
     */
    @GetMapping("/v1/locations/{id}/parent")
    @Override
    public GeoLocation findParent(@PathVariable(name = "id") int locationId)
    {
        findById(locationId);
        return geodataService.findParent(locationId);
    }

    /**
     * Find children of the specified location
     */
    @GetMapping(value = "/v1/locations/{id}/children")
    @Override
    public Page<GeoLocation> findChildren(@PathVariable(name = "id") int locationId, Pageable pageable)
    {
        findById(locationId);
        return geodataService.findChildren(locationId, pageable);
    }

    /**
     * List continents
     */
    @RequestMapping(value = "/v1/continents", method = RequestMethod.GET)
    @Override
    public Page<Continent> findContinents()
    {
        return geodataService.findContinents();
    }

    /**
     * Fetch continent by continent-code
     */
    @RequestMapping(value = "/v1/continents/{continentCode}", method = RequestMethod.GET)
    @Override
    public Continent findContinent(@PathVariable("continentCode") String continentCode)
    {
        final Continent continent = geodataService.findContinent(continentCode);
        return notNull(continent, "Could not find continent " + continentCode);
    }

    /**
     * List countries on the specified continent
     */
    @GetMapping("/v1/continents/{continent}/countries")
    @Override
    public Page<Country> findCountriesOnContinent(@PathVariable("continent") String continentCode, Pageable pageable)
    {
        this.findContinent(continentCode);
        return geodataService.findCountriesOnContinent(continentCode, pageable);
    }

    /**
     * List countries
     */
    @GetMapping("/v1/countries")
    @Override
    public Page<Country> findCountries(Pageable pageable)
    {
        return geodataService.findCountries(pageable);
    }

    /**
     * Fetch country by country-code
     */
    @GetMapping("/v1/countries/{countryCode}")
    @Override
    public Country findCountryByCode(@PathVariable("countryCode") String countryCode)
    {
        final Country location = geodataService.findCountryByCode(countryCode);
        return notNull(location, "Could not find country with code " + countryCode);
    }

    /**
     * Fetch child locations of the specified country code
     */
    @GetMapping("/v1/countries/{countryCode}/children")
    @Override
    public Page<GeoLocation> findChildren(@PathVariable("countryCode") String countryCode, Pageable pageable)
    {
        findCountryByCode(countryCode);
        return geodataService.findChildren(countryCode, pageable);
    }

    /**
     * Multifetch useful for fetching multiple locations by ID in a single request
     */
    @GetMapping("/v1/locations/ids")
    @Override
    public List<GeoLocation> findByIds(@RequestParam(name = "ids") Collection<Integer> ids)
    {
        return geodataService.findByIds(ids);
    }

    @Override
    public Page<GeoLocation> findByName(final String name, final Pageable pageable)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void load(LoadProgressListener loadProgressListener)
    {
        geodataService.load(loadProgressListener);
    }

    /**
     * Check whether the specified location ID is inside any of the specified location IDs
     */
    @GetMapping("/v1/locations/{id}/insideany/{ids}")
    @Override
    public boolean isInsideAny(@PathVariable("ids") List<Integer> ids, @PathVariable("id") int id)
    {
        ids.forEach(this::findById);
        findById(id);
        return geodataService.isInsideAny(ids, id);
    }

    /**
     * Check if the location ID specified is outside all of the specified location IDs
     */
    @GetMapping("/v1/locations/{id}/outsideall/{ids}")
    @Override
    public boolean isOutsideAll(@PathVariable("ids") List<Integer> ids, @PathVariable("id") int id)
    {
        ids.forEach(this::findById);
        findById(id);
        return geodataService.isOutsideAll(ids, id);
    }

    /**
     * Find locations sorted by proximity from the specified coordinates
     */
    @GetMapping("/v1/locations/proximity")
    @Override
    public Page<GeoLocationDistance> findNear(Coordinates point, @RequestParam(name = "maxDistance") int maxDistance, Pageable pageable)
    {
        return geodataService.findNear(point, maxDistance, pageable);
    }

    /**
     * Find the nearest location of the specified coordinates
     */
    @GetMapping("/v1/locations/coordinates")
    @Override
    public GeoLocation findbyCoordinate(Coordinates coordinates, @RequestParam(name = "maxDistance") int maxDistance)
    {
        final GeoLocation location = geodataService.findbyCoordinate(coordinates, maxDistance);
        return notNull(location, "Could not find a location for coordinates " + coordinates);
    }

    /**
     * Check if the location ID is indeed containing the child location ID
     */
    @GetMapping("/v1/locations/{id}/contains/{child}")
    @Override
    public boolean isLocationInside(@PathVariable(name = "id", required = true) int id, @PathVariable(name = "child", required = true) int child)
    {
        findById(id);
        findById(child);
        return geodataService.isLocationInside(child, id);
    }

    private <T> T notNull(T obj, String errorMessage)
    {
        if (obj != null)
        {
            return obj;
        }
        throw new EmptyResultDataAccessException(errorMessage, 1);
    }

    /**
     * Fetch boundaries dynamically simplified to be useful for the specified view dimensions
     */
    @Override
    @GetMapping("/v1/locations/{id}/previewboundaries.wkb")
    public byte[] findBoundaries(@PathVariable(name = "id") int locationId, View view)
    {
        return geodataService.findBoundaries(locationId, view);
    }

    /**
     * Fetch boundaries dynamically simplified to be useful for the specified view dimensions (for example Google Maps overlay)
     */
    @GetMapping("/v1/locations/{id}/previewboundaries")
    public void findPreviewBoundaries(@PathVariable(name = "id") int locationId, @Validated View view, HttpServletResponse resp) throws IOException
    {
        final byte[] boundaries = geodataService.findBoundaries(locationId, view);
        outputGeoJson(boundaries, resp, locationId);
    }

    /**
     * Get data source information
     */
    @GetMapping("/v1/source")
    public SourceDataInfoSet sourceData()
    {
        return geoMetaService.getSourceDataInfo();
    }
}
