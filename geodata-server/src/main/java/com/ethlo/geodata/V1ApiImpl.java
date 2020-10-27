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
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.rest.v1.handler.V1ApiDelegate;
import com.ethlo.geodata.rest.v1.model.V1Continent;
import com.ethlo.geodata.rest.v1.model.V1Country;
import com.ethlo.geodata.rest.v1.model.V1GeoLocation;
import com.ethlo.geodata.rest.v1.model.V1PageContinent;
import com.ethlo.geodata.rest.v1.model.V1PageCountry;
import com.ethlo.geodata.rest.v1.model.V1PageGeoLocation;
import com.ethlo.geodata.rest.v1.model.V1SliceGeoLocation;
import com.ethlo.geodata.util.InetUtil;

@Component
public class V1ApiImpl implements V1ApiDelegate
{
    private final GeodataService geodataService;
    private final Mapper mapper;

    public V1ApiImpl(GeodataService geodataService)
    {
        this.geodataService = geodataService;
        this.mapper = new Mapper(geodataService);
    }

    @Override
    public ResponseEntity<List<V1GeoLocation>> findByIds(final List<Integer> ids)
    {
        return ResponseEntity.ok(geodataService.findByIds(ids).stream().map(mapper::transform).collect(Collectors.toList()));
    }

    @Override
    public ResponseEntity<V1Continent> findContinentByCode(final String continentCode)
    {
        return ResponseEntity.ok(Optional.ofNullable(geodataService.findContinent(continentCode)).map(mapper::transform).orElseThrow(notNull("No continent found for continent code " + continentCode)));
    }

    @Override
    public ResponseEntity<V1Country> findCountryByPhone(final String phone)
    {
        final Country country = Optional.ofNullable(geodataService.findByPhoneNumber(phone)).orElseThrow(notNull("Unable to determine country by phone number " + phone));
        return ResponseEntity.ok(mapper.transform(country));
    }

    @Override
    public ResponseEntity<V1GeoLocation> findByIp(final String ip)
    {
        return ResponseEntity.ok(Optional.ofNullable(geodataService.findByIp(InetUtil.inet(ip)))
                .map(mapper::transform)
                .orElseThrow(notNull("No location found for IP address " + ip)));
    }

    @Override
    public ResponseEntity<V1SliceGeoLocation> findByName(final String name, final Integer page, final Integer size)
    {
        final Slice<V1GeoLocation> slice = geodataService.findByName(name, Mapper.pageable(page, size)).map(mapper::transform);
        return ResponseEntity.ok(Mapper.toGeoLocationsSlice(slice));
    }

    @Override
    public ResponseEntity<V1PageGeoLocation> findChildren(final Integer id, final Integer page, final Integer size)
    {
        final Page<V1GeoLocation> res = geodataService.findChildren(id, Mapper.pageable(page, size)).map(mapper::transform);
        return ResponseEntity.ok(Mapper.toGeolocationPage(res));
    }

    @Override
    public ResponseEntity<V1PageCountry> findCountries(final Integer page, final Integer size)
    {
        return ResponseEntity.ok(mapper.toCountryPage(geodataService.findCountries(Mapper.pageable(page, size)).map(mapper::transform)));
    }

    @Override
    public ResponseEntity<V1PageCountry> findCountriesOnContinent(final String continent, final Integer page, final Integer size)
    {
        return ResponseEntity.ok(mapper.toCountryPage(geodataService.findCountriesOnContinent(continent, Mapper.pageable(page, size)).map(mapper::transform)));
    }

    @Override
    public ResponseEntity<V1Country> findCountryByCode(final String countryCode)
    {
        return ResponseEntity.ok(Optional.ofNullable(geodataService.findCountryByCode(countryCode)).map(mapper::transform).orElseThrow(notNull("No such country code: " + countryCode)));
    }

    @Override
    public ResponseEntity<V1PageGeoLocation> findCountryChildren(final String countryCode, final Integer page, final Integer size)
    {
        return ResponseEntity.ok(Mapper.toGeolocationPage(geodataService.findChildren(countryCode, Mapper.pageable(page, size)).map(mapper::transform)));
    }

    @Override
    public ResponseEntity<V1GeoLocation> findLocation(final Integer id)
    {
        final Optional<GeoLocation> location = Optional.ofNullable(geodataService.findById(id));
        return ResponseEntity.ok(location.map(mapper::transform).orElseThrow(notNull("No location found for id " + id)));
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
        return ResponseEntity.ok(location.map(mapper::transform).orElseThrow(notNull("No parent location found for id " + id)));
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
        final Page<V1Continent> page = geodataService.findContinents().map(mapper::transform);
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
