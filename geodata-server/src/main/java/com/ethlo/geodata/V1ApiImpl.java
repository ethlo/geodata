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


import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountrySummary;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.rest.v1.handler.V1ApiDelegate;
import com.ethlo.geodata.rest.v1.model.V1Continent;
import com.ethlo.geodata.rest.v1.model.V1Coordinates;
import com.ethlo.geodata.rest.v1.model.V1Country;
import com.ethlo.geodata.rest.v1.model.V1CountrySummary;
import com.ethlo.geodata.rest.v1.model.V1GeoLocation;
import com.ethlo.geodata.rest.v1.model.V1GeoLocationSummary;
import com.ethlo.geodata.rest.v1.model.V1PageContinent;
import com.ethlo.geodata.rest.v1.model.V1PageCountry;
import com.ethlo.geodata.rest.v1.model.V1PageGeoLocation;
import com.ethlo.geodata.rest.v1.model.V1SliceGeoLocation;
import com.ethlo.geodata.util.InetUtil;

@Component
public class V1ApiImpl implements V1ApiDelegate
{
    private final GeodataService geodataService;

    public V1ApiImpl(GeodataService geodataService)
    {
        this.geodataService = geodataService;
    }

    @Override
    public ResponseEntity<List<V1GeoLocation>> findByIds(final List<Integer> ids)
    {
        return ResponseEntity.ok(geodataService.findByIds(ids).stream().map(this::transform).collect(Collectors.toList()));
    }

    @Override
    public ResponseEntity<V1Country> findCountryByPhone(final String phone)
    {
        final Country country = Optional.ofNullable(geodataService.findByPhoneNumber(phone)).orElseThrow(notNull("Unable to determine country by phone number " + phone));
        return ResponseEntity.ok(transform(country));
    }

    @Override
    public ResponseEntity<V1GeoLocation> findByIp(final String ip)
    {
        return ResponseEntity.ok(Optional.ofNullable(geodataService.findByIp(InetUtil.inet(ip)))
                .map(this::transform)
                .orElseThrow(notNull("No location found for IP address " + ip)));
    }

    private V1GeoLocation transform(final GeoLocation l)
    {
        return new V1GeoLocation()
                .country(transform(l.getCountry()))
                .coordinates(transform(l.getCoordinates()))
                .featureClass(l.getFeatureClass())
                .featureCode(l.getFeatureCode())
                .id(l.getId())
                .name(l.getName())
                .parentLocationId(l.getParentLocationId() != null ? l.getParentLocationId() : null)
                .population(l.getPopulation() != 0 ? l.getPopulation() : null)
                .timeZone(l.getTimeZone())
                .path(transform(geodataService.findPath(l.getId())));
    }

    private List<V1GeoLocationSummary> transform(final List<GeoLocation> path)
    {
        return path.stream().map(l ->
                new V1GeoLocationSummary()
                        .id(l.getId())
                        .name(l.getName())
                        .featureClass(l.getFeatureClass())
                        .featureCode(l.getFeatureCode()))
                .collect(Collectors.toList());
    }

    private V1Continent transform(final Continent c)
    {
        return new V1Continent()
                .id(c.getId())
                .continentCode(c.getContinentCode())
                .name(c.getName())
                .featureClass(c.getFeatureClass())
                .featureCode(c.getFeatureCode())
                .coordinates(this.transform(c.getCoordinates()))
                .population(c.getPopulation());
    }

    private V1Country transform(final Country c)
    {
        final GeoLocation l = geodataService.findById(c.getId());
        return new V1Country()
                .id(c.getId())
                .name(c.getName())
                .languages(c.getLanguages())
                .featureClass(l.getFeatureClass())
                .featureCode(l.getFeatureCode())
                .coordinates(Optional.ofNullable(l.getCoordinates()).map(this::transform).orElse(null))
                .parentLocationId(l.getParentLocationId())
                .population(l.getPopulation())
                .timeZone(l.getTimeZone())
                .path(transform(geodataService.findPath(l.getId())));
    }

    private V1Coordinates transform(final Coordinates coordinates)
    {
        return new V1Coordinates().lat(coordinates.getLat()).lng(coordinates.getLng());
    }

    private V1CountrySummary transform(final CountrySummary country)
    {
        return new V1CountrySummary()
                .code(country.getCode())
                .id(country.getId())
                .name(country.getName());
    }

    @Override
    public ResponseEntity<V1SliceGeoLocation> findByName(final String name, final Integer page, final Integer size)
    {
        final Slice<V1GeoLocation> slice = geodataService.findByName(name, pageable(page, size)).map(this::transform);
        return ResponseEntity.ok(new V1SliceGeoLocation()
                .content(slice.getContent())
                .first(slice.isFirst())
                .last(slice.isLast())
                .number(slice.getNumber())
                .numberOfElements(slice.getNumberOfElements())
                .size(slice.getSize()));
    }

    @Override
    public ResponseEntity<V1PageGeoLocation> findChildren(final Integer id, final Integer page, final Integer size)
    {
        final Page<V1GeoLocation> res = geodataService.findChildren(id, pageable(page, size)).map(this::transform);
        return ResponseEntity.ok(toGeolocationPage(res));
    }

    private PageRequest pageable(final Integer page, final Integer size)
    {
        return PageRequest.of(page != null ? page : 0, size != null && size > 0 & size <= 10_000 ? size : 25);
    }

    private V1PageGeoLocation toGeolocationPage(final Page<V1GeoLocation> res)
    {
        return new V1PageGeoLocation()
                .content(res.getContent())
                .first(res.isFirst())
                .last(res.isLast())
                .number(res.getNumber())
                .numberOfElements(res.getNumberOfElements())
                .size(res.getSize())
                .totalElements(res.getTotalElements())
                .totalPages(res.getTotalPages());
    }

    @Override
    public ResponseEntity<V1PageCountry> findCountries(final Integer page, final Integer size)
    {
        return ResponseEntity.ok(toCountryPage(geodataService.findCountries(pageable(page, size)).map(this::transform)));
    }

    private V1PageCountry toCountryPage(final Page<V1Country> res)
    {
        return new V1PageCountry()
                .first(res.isFirst())
                .last(res.isLast())
                .number(res.getNumber())
                .numberOfElements(res.getNumberOfElements())
                .content(res.getContent())
                .size(res.getSize())
                .totalElements(res.getTotalElements())
                .totalPages(res.getTotalPages());
    }

    @Override
    public ResponseEntity<V1PageCountry> findCountriesOnContinent(final String continent, final Integer page, final Integer size)
    {
        return ResponseEntity.ok(toCountryPage(geodataService.findCountriesOnContinent(continent, pageable(page, size)).map(this::transform)));
    }

    @Override
    public ResponseEntity<V1Country> findCountryByCode(final String countryCode)
    {
        return ResponseEntity.ok(Optional.ofNullable(geodataService.findCountryByCode(countryCode)).map(this::transform).orElseThrow(notNull("No such country code: " + countryCode)));
    }

    @Override
    public ResponseEntity<V1PageGeoLocation> findCountryChildren(final String countryCode, final Integer page, final Integer size)
    {
        return ResponseEntity.ok(toGeolocationPage(geodataService.findChildren(countryCode, pageable(page, size)).map(this::transform)));
    }

    @Override
    public ResponseEntity<V1GeoLocation> findLocation(final Integer id)
    {
        final Optional<GeoLocation> location = Optional.ofNullable(geodataService.findById(id));
        return ResponseEntity.ok(location.map(this::transform).orElseThrow(notNull("No location found for id " + id)));
    }

    @Override
    public ResponseEntity<Boolean> isLocationInside(final Integer id, final Integer child)
    {
        return ResponseEntity.ok(geodataService.isLocationInside(id, child));
    }

    @Override
    public ResponseEntity<V1GeoLocation> findParentLocation(final Integer id)
    {
        final Optional<GeoLocation> location = Optional.ofNullable(geodataService.findParent(id));
        return ResponseEntity.ok(location.map(this::transform).orElseThrow(notNull("No parent location found for id " + id)));
    }

    @Override
    public ResponseEntity<Boolean> insideAny(final List<Integer> ids, final Integer id)
    {
        ids.forEach(geodataService::findById);
        geodataService.findById(id);
        return ResponseEntity.ok(geodataService.isInsideAny(ids, id));
    }

    @Override
    public ResponseEntity<V1PageContinent> listContinents()
    {
        final Page<V1Continent> page = geodataService.findContinents().map(this::transform);
        return ResponseEntity.ok(new V1PageContinent()
                .content(page.getContent())
                .first(page.isFirst())
                .last(page.isLast())
                .number(page.getNumber())
                .numberOfElements(page.getNumberOfElements())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages()));
    }

    @Override
    public ResponseEntity<Boolean> outsideAll(final List<Integer> ids, final Integer id)
    {
        ids.forEach(geodataService::findById);
        geodataService.findById(id);
        return ResponseEntity.ok(geodataService.isOutsideAll(ids, id));
    }

    private Supplier<EmptyResultDataAccessException> notNull(String errorMessage)
    {
        return () -> new EmptyResultDataAccessException(errorMessage, 1);
    }
}
