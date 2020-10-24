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
import java.util.stream.Collectors;

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
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ethlo.geodata.dao.MetaDao;
import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountrySummary;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.GeoLocationWithPath;
import com.ethlo.geodata.model.View;
import com.ethlo.geodata.rest.v1.handler.V1ApiDelegate;
import com.ethlo.geodata.rest.v1.model.V1Continent;
import com.ethlo.geodata.rest.v1.model.V1Coordinates;
import com.ethlo.geodata.rest.v1.model.V1Country;
import com.ethlo.geodata.rest.v1.model.V1CountrySummary;
import com.ethlo.geodata.rest.v1.model.V1GeoLocation;
import com.ethlo.geodata.rest.v1.model.V1PageContinent;
import com.ethlo.geodata.rest.v1.model.V1PageCountry;
import com.ethlo.geodata.rest.v1.model.V1PageGeoLocation;
import com.ethlo.geodata.rest.v1.model.V1PageGeoLocationDistance;
import com.google.common.collect.Lists;

@Component
public class V1ApiImpl implements V1ApiDelegate
{
    private final GeodataService geodataService;

    public V1ApiImpl(GeodataService geodataService)
    {
        this.geodataService = geodataService;
    }

    @Override
    public ResponseEntity<byte[]> findBoundaries(final Long id)
    {
        return null;
    }

    @Override
    public ResponseEntity<Void> findBoundaries1(final Long id)
    {
        return null;
    }

    @Override
    public ResponseEntity<List<V1GeoLocation>> findByIds(final List<Long> ids)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1GeoLocation> findByIp(final String ip)
    {
        final GeoLocation location = geodataService.findByIp(ip);
        return ResponseEntity.ok(transform(notNull(location, "No location found for IP address " + ip)));
    }

    private V1GeoLocation transform(final GeoLocation l)
    {
        return new V1GeoLocation()
                .country(transform(l.getCountry()))
                .coordinates(transform(l.getCoordinates()))
                .featureCode(l.getFeatureCode())
                .id((long) l.getId())
                .name(l.getName())
                .parentLocationId(l.getParentLocationId() != null ? (long) l.getParentLocationId() : null)
                .population(l.getPopulation());
    }

    private V1Coordinates transform(final Coordinates coordinates)
    {
        return new V1Coordinates().lat(coordinates.getLat()).lng(coordinates.getLng());
    }

    private V1CountrySummary transform(final CountrySummary country)
    {
        return new V1CountrySummary()
                .code(country.getCode())
                .id((long)country.getId())
                .name(country.getName());
    }

    @Override
    public ResponseEntity<V1PageGeoLocation> findByName(final String name, final Integer page, final Integer size)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1PageGeoLocation> findChildren(final Long id, final Integer page, final Integer size)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1Continent> findContinentByCode(final String continentCode)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1PageCountry> findCountries(final Integer page, final Integer size)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1PageCountry> findCountriesOnContient(final String continent, final Integer page, final Integer size)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1Country> findCountryByCode(final String countryCode)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1Country> findCountryByPhone(final String phone)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1PageGeoLocation> findCountryChildren(final String countryCode, final Integer page, final Integer size)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1GeoLocation> findLocation(final Long id)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1GeoLocation> findLocation1(final Integer maxDistance, final Double lat, final Double lng)
    {
        return null;
    }

    @Override
    public ResponseEntity<Boolean> findLocation2(final Long id, final Long child)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1PageGeoLocationDistance> findNear(final Integer maxDistance, final Integer page, final Integer size, final Double lat, final Double lng)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1GeoLocation> findParentLocation(final Long id)
    {
        return null;
    }

    @Override
    public ResponseEntity<byte[]> findPreviewBoundaries(final Long id, final Double minLng, final Double maxLng, final Double minLat, final Double maxLat, final Integer height, final Integer width)
    {
        return null;
    }

    @Override
    public ResponseEntity<Void> findPreviewBoundaries1(final Long id, final Double minLng, final Double maxLng, final Double minLat, final Double maxLat, final Integer height, final Integer width)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1GeoLocation> findProximity(final Integer maxDistance, final Double lat, final Double lng)
    {
        return null;
    }

    @Override
    public ResponseEntity<byte[]> findSimpleBoundaries(final Long id, final Double maxTolerance)
    {
        return null;
    }

    @Override
    public ResponseEntity<Void> findSimpleBoundaries1(final Long id, final Double maxTolerance)
    {
        return null;
    }

    @Override
    public ResponseEntity<Boolean> insideAny(final String ids, final Long id)
    {
        return null;
    }

    @Override
    public ResponseEntity<V1PageContinent> listContinents()
    {
        return null;
    }

    @Override
    public ResponseEntity<Boolean> outsideAll(final String ids, final Long id)
    {
        return null;
    }

    private <T> T notNull(T obj, String errorMessage)
    {
        if (obj != null)
        {
            return obj;
        }
        throw new EmptyResultDataAccessException(errorMessage, 1);
    }
}
