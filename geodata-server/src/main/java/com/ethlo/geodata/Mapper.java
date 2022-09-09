package com.ethlo.geodata;

/*-
 * #%L
 * geodata-server
 * %%
 * Copyright (C) 2017 - 2020 Morten Haraldsen (ethlo)
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
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountrySummary;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.rest.v1.model.V1Continent;
import com.ethlo.geodata.rest.v1.model.V1Coordinates;
import com.ethlo.geodata.rest.v1.model.V1Country;
import com.ethlo.geodata.rest.v1.model.V1CountrySummary;
import com.ethlo.geodata.rest.v1.model.V1GeoLocation;
import com.ethlo.geodata.rest.v1.model.V1GeoLocationDistance;
import com.ethlo.geodata.rest.v1.model.V1GeoLocationSummary;
import com.ethlo.geodata.rest.v1.model.V1PageCountry;
import com.ethlo.geodata.rest.v1.model.V1PageGeoLocation;
import com.ethlo.geodata.rest.v1.model.V1PageGeoLocationDistance;
import com.ethlo.geodata.rest.v1.model.V1SliceGeoLocation;

public class Mapper
{
    private final GeodataService geodataService;

    public Mapper(final GeodataService geodataService)
    {
        this.geodataService = geodataService;
    }

    public static PageRequest pageable(final Integer page, final Integer size)
    {
        return PageRequest.of(page != null ? page : 0, size != null && size > 0 & size <= 10_000 ? size : 25);
    }

    public static V1PageGeoLocation toGeoLocationPage(final Page<V1GeoLocation> res)
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

    public static V1SliceGeoLocation toGeoLocationSlice(final Slice<V1GeoLocation> slice)
    {
        return new V1SliceGeoLocation()
                .content(slice.getContent())
                .first(slice.isFirst())
                .last(slice.isLast())
                .number(slice.getNumber())
                .numberOfElements(slice.getNumberOfElements())
                .size(slice.getSize());
    }

    public V1GeoLocation transform(final GeoLocation l)
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
                .hasBoundary(geodataService.hasBoundary(l.getId()))
                .hasChildren(geodataService.hasRealChildren(l.getId()))
                .path(transform(geodataService.findPath(l.getId())));
    }

    public List<V1GeoLocationSummary> transform(final List<GeoLocation> path)
    {
        return path.stream().map(l ->
                        new V1GeoLocationSummary()
                                .id(l.getId())
                                .name(l.getName())
                                .featureClass(l.getFeatureClass())
                                .featureCode(l.getFeatureCode()))
                .collect(Collectors.toList());
    }

    public V1Continent transform(final Continent c)
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

    public V1Country transform(final Country c)
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

    public V1CountrySummary transform(final CountrySummary country)
    {
        return country != null ? new V1CountrySummary()
                .code(country.getCode())
                .id(country.getId())
                .name(country.getName()) : null;
    }

    public V1PageCountry toCountryPage(final Page<V1Country> res)
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

    public V1PageGeoLocationDistance toGeolocationDistancePage(final Page<GeoLocationDistance> locationAndDistance)
    {
        final List<V1GeoLocationDistance> content = locationAndDistance.getContent().stream()
                .map(ld -> new V1GeoLocationDistance()
                        .location(transform(ld.getLocation()))
                        .distance(ld.getDistance())
                ).collect(Collectors.toList());

        return new V1PageGeoLocationDistance()
                .first(locationAndDistance.isFirst())
                .last(locationAndDistance.isLast())
                .content(content)
                .number(locationAndDistance.getNumber())
                .numberOfElements(locationAndDistance.getNumberOfElements());
    }
}
