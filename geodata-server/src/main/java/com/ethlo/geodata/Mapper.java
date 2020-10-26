package com.ethlo.geodata;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountrySummary;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.rest.v1.model.V1Continent;
import com.ethlo.geodata.rest.v1.model.V1Coordinates;
import com.ethlo.geodata.rest.v1.model.V1Country;
import com.ethlo.geodata.rest.v1.model.V1CountrySummary;
import com.ethlo.geodata.rest.v1.model.V1GeoLocation;
import com.ethlo.geodata.rest.v1.model.V1GeoLocationSummary;
import com.ethlo.geodata.rest.v1.model.V1PageCountry;
import com.ethlo.geodata.rest.v1.model.V1PageGeoLocation;

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

    public static V1PageGeoLocation toGeolocationPage(final Page<V1GeoLocation> res)
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
}
