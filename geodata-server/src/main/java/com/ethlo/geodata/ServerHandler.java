package com.ethlo.geodata;

/*-
 * #%L
 * geodata-fast-server
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

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import com.ethlo.geodata.dao.MetaDao;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.View;
import com.ethlo.geodata.rest.v1.model.V1Continent;
import com.ethlo.geodata.rest.v1.model.V1GeoLocation;
import com.ethlo.geodata.rest.v1.model.V1PageContinent;
import com.ethlo.geodata.util.InetUtil;
import com.ethlo.geodata.util.MemoryUsageUtil;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.ExceptionHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

public class ServerHandler extends BaseServerHandler
{
    private final GeodataService geodataService;
    private final MetaDao metaDao;
    private final Mapper mapper;

    public ServerHandler(final GeodataService geodataService, final MetaDao metaDao)
    {
        this.geodataService = geodataService;
        this.mapper = new Mapper(geodataService);
        this.metaDao = metaDao;
    }

    public HttpHandler handler(Map<Class<? extends Throwable>, Function<Throwable, ApiError>> errorHandlers)
    {
        final RoutingHandler routes = Handlers.routing()

                .add(Methods.GET, "/v1/locations/ids", exchange ->
                {
                    final List<Integer> ids = getIntList(exchange, "ids").orElseThrow(missingParam("ids"));
                    json(exchange, ids.stream().map(geodataService::findById).collect(Collectors.toList()));
                })

                .add(Methods.GET, "/v1/locations/{id}/boundaries", exchange ->
                {
                    final int id = requireIntParam(exchange, "id");
                    final Geometry boundary = geodataService.findBoundaries(id).orElseThrow(notNull("No boundary for id " + id));
                    sendGeoJson(exchange, boundary);
                })

                .add(Methods.GET, "/v1/locations/{id}/boundaries.wkb", exchange ->
                {
                    final int id = requireIntParam(exchange, "id");
                    final Geometry boundary = geodataService.findBoundaries(id).orElseThrow(notNull("No boundary for id " + id));
                    sendWkb(exchange, boundary);
                })

                .add(Methods.GET, "/v1/locations/ip/{ip}", exchange ->
                {
                    final String ip = requireStringParam(exchange, "ip");
                    json(exchange, Optional.ofNullable(geodataService.findByIp(InetUtil.inet(ip)))
                            .map(mapper::transform)
                            .orElseThrow(notNull("No location found for IP address " + ip)));
                })

                .add(Methods.GET, "/v1/locations/name/{name}", exchange ->
                {
                    final String name = requireStringParam(exchange, "name");
                    final Pageable pageable = getPageable(exchange);
                    final Slice<V1GeoLocation> slice = geodataService.findByName(name, pageable).map(mapper::transform);
                    final int total = slice.hasNext() ? slice.getContent().size() + 1 : slice.getContent().size();
                    json(exchange, Mapper.toGeoLocationPage(new PageImpl<>(slice.getContent(), pageable, total)));
                })

                .add(Methods.GET, "/v1/locations/{id}/children", exchange ->
                {
                    final int id = requireIntParam(exchange, "id");
                    json(exchange, Mapper.toGeoLocationPage(geodataService.findChildren(id, getPageable(exchange)).map(mapper::transform)));
                })

                .add(Methods.GET, "/v1/continents/{continentCode}", exchange ->
                {
                    final String continentCode = requireStringParam(exchange, "continentCode");
                    json(exchange, Optional.ofNullable(geodataService.findContinent(continentCode)).map(mapper::transform).orElseThrow(notNull("No continent found for continent code " + continentCode)));
                })

                .add(Methods.GET, "/v1/countries", exchange ->
                        json(exchange, mapper.toCountryPage(geodataService.findCountries(pageable(exchange)).map(mapper::transform))))

                .add(Methods.GET, "/v1/countries/{countryCode}/children", exchange ->
                {
                    final String countryCode = requireStringParam(exchange, "countryCode");
                    json(exchange, Mapper.toGeoLocationPage(geodataService.findChildren(countryCode, pageable(exchange)).map(mapper::transform)));
                })

                .add(Methods.GET, "/v1/locations/{id}", exchange ->
                {
                    final int id = requireIntParam(exchange, "id");
                    json(exchange, Optional.ofNullable(geodataService.findById(id)).map(mapper::transform).orElseThrow(notNull("No location with id " + id)));
                })

                .add(Methods.GET, "/v1/locations/{id}/parent", exchange ->
                {
                    final int id = requireIntParam(exchange, "id");
                    json(exchange, Optional.ofNullable(geodataService.findParent(id))
                            .map(mapper::transform)
                            .orElseThrow(notNull("No parent location found for id " + id)));
                })

                .add(Methods.GET, "/v1/locations/{id}/insideany/{ids}", exchange ->
                {
                    final int id = requireIntParam(exchange, "id");
                    final List<Integer> ids = getIntList(exchange, "ids").orElseThrow(missingParam("ids"));
                    json(exchange, geodataService.isInsideAny(ids, id));
                })

                .add(Methods.GET, "/v1/locations/{id}/contains/{child}", exchange ->
                {
                    final int id = requireIntParam(exchange, "id");
                    final int child = requireIntParam(exchange, "child");
                    json(exchange, geodataService.isLocationInside(child, id));
                })

                .add(Methods.GET, "/v1/continents", exchange ->
                {
                    final Page<V1Continent> page = geodataService.findContinents().map(mapper::transform);
                    json(exchange, new V1PageContinent()
                            .content(page.getContent())
                            .first(page.isFirst())
                            .last(page.isLast())
                            .number(page.getNumber())
                            .numberOfElements(page.getNumberOfElements())
                            .size(page.getSize())
                            .totalElements(page.getTotalElements())
                            .totalPages(page.getTotalPages()));
                })

                .add(Methods.GET, "/v1/continents/{continent}/countries", exchange ->
                {
                    final String continent = requireStringParam(exchange, "continent");
                    json(exchange, mapper.toCountryPage(geodataService.findCountriesOnContinent(continent, pageable(exchange)).map(mapper::transform)));
                })

                .add(Methods.GET, "/v1/countries/{countryCode}", exchange ->
                {
                    final String countryCode = requireStringParam(exchange, "countryCode");
                    json(exchange, Optional.ofNullable(geodataService.findCountryByCode(countryCode)).map(mapper::transform).orElseThrow(notNull("No such country code: " + countryCode)));
                })

                .add(Methods.GET, "/v1/locations/phone/{phone}", exchange ->
                {
                    final String phone = requireStringParam(exchange, "phone");
                    final Country country = Optional.ofNullable(geodataService.findByPhoneNumber(phone)).orElseThrow(notNull("Unable to determine country by phone number " + phone));
                    json(exchange, mapper.transform(country));
                })

                .add(Methods.GET, "/v1/locations/proximity", exchange ->
                {
                    final Pageable pageable = pageable(exchange);
                    final double lat = requireDoubleParam(exchange, "lat");
                    final double lng = requireDoubleParam(exchange, "lng");
                    final int maxDistance = getIntParam(exchange, "maxDistance").orElse(Integer.MAX_VALUE);
                    final Page<GeoLocationDistance> locationAndDistance = geodataService.findNear(Coordinates.from(lat, lng), maxDistance, pageable);
                    json(exchange, mapper.toGeolocationDistancePage(locationAndDistance));
                })

                .add(Methods.GET, "/v1/locations/coordinates", exchange ->
                {
                    final double lat = requireDoubleParam(exchange, "lat");
                    final double lng = requireDoubleParam(exchange, "lng");
                    final Coordinates coordinates = Coordinates.from(lat, lng);
                    final int maxDistance = getIntParam(exchange, "maxDistance").orElse(Integer.MAX_VALUE);
                    final Optional<LookupMetadata> lookupMetadata = geodataService.findByCoordinate(coordinates, maxDistance);

                    final Optional<GeoLocation> location = Optional.ofNullable(lookupMetadata
                            .map(LookupMetadata::getLocation)
                            .orElseGet(() ->
                            {
                                final Page<GeoLocationDistance> nearest = geodataService.findNear(coordinates, maxDistance, PageRequest.of(0, 1));
                                return nearest.hasContent() ? nearest.getContent().get(0).getLocation() : null;
                            }));

                    final V1GeoLocation l = location
                            .map(mapper::transform)
                            .orElseThrow(notNull("Unable to determine location of " + lat + "," + lng));

                    // Add some metadata about the lookup
                    lookupMetadata.ifPresent(lmd ->
                    {
                        final String lookupHeader = lmd.getLocation().getId() + "-" + lmd.getSubdivideIndex() + "-" + lmd.getEnvelope().getArea();
                        exchange.getResponseHeaders().add(new HttpString("X-Boundary-Lookup"), lookupHeader);
                    });

                    json(exchange, l);
                })

                .add(Methods.GET, "/v1/locations/{id}/previewboundaries", exchange ->
                {
                    final Geometry boundary = getPreviewGeometry(exchange);
                    sendGeoJson(exchange, boundary);
                })

                .add(Methods.GET, "/v1/locations/{id}/previewboundaries.wkb", exchange ->
                {
                    final Geometry boundary = getPreviewGeometry(exchange);
                    sendWkb(exchange, boundary);
                })

                .add(Methods.GET, "/v1/locations/contains", exchange ->
                {
                    final double lat = requireDoubleParam(exchange, "lat");
                    final double lng = requireDoubleParam(exchange, "lng");
                    final Coordinates coordinates = Coordinates.from(lat, lng);
                    final int maxDistance = getIntParam(exchange, "maxDistance").orElse(Integer.MAX_VALUE);
                    json(exchange, geodataService.findWithin(coordinates, maxDistance)
                            .map(lmd ->
                            {
                                // Add some metadata about the lookup
                                final String lookupHeader = lmd.getLocation().getId() + "-" + lmd.getSubdivideIndex() + "-" + lmd.getEnvelope().getDiameter() * (6371 / 2D);
                                exchange.getResponseHeaders().add(new HttpString("X-Boundary-Lookup"), lookupHeader);
                                return lmd.getLocation();
                            })
                            .map(mapper::transform)
                            .orElseThrow(notNull("No boundaries containing " + lat + "," + lng + " found")));
                })

                .add(Methods.GET, "/v1/locations/{id}/outsideall/{ids}", exchange ->
                {
                    final int id = requireIntParam(exchange, "id");
                    final List<Integer> ids = getIntList(exchange, "ids").orElseThrow(missingParam("ids"));
                    json(exchange, geodataService.isOutsideAll(ids, id));
                })

                .add(Methods.GET, "/v1/locations/{id}/simpleboundaries.wkb", exchange ->
                {
                    final Geometry boundary = getSimpleBoundary(exchange);
                    sendWkb(exchange, boundary);
                })

                .add(Methods.GET, "/v1/locations/{id}/simpleboundaries", exchange ->
                {
                    final Geometry boundary = getSimpleBoundary(exchange);
                    sendGeoJson(exchange, boundary);
                });

        // Performance logging handler
        final HttpHandler performanceHandler = new PerformanceHandler(routes);

        // Static content
        final PathHandler path = Handlers.path(performanceHandler)
                .addPrefixPath("/swagger-ui", new ResourceHandler(classpathResource("META-INF/resources/webjars/swagger-ui/3.35.2")))
                .addExactPath("/spec.yaml", new ResourceHandler(classpathResource("public/spec.yaml")))
                .addExactPath("/", new ResourceHandler(classpathResource("public/index.html")));
        
        // Source data information
        path.addExactPath("/sysadmin/source", exchange -> json(exchange, metaDao.load()));

        // Version info
        final Map<String, Object> versionInfo = new LinkedHashMap<>();
        path.addExactPath("/sysadmin/info", exchange ->
        {
            final Properties gitProperties = new Properties();
            gitProperties.load(new ClassPathResource("git.properties").getInputStream());
            versionInfo.put("git", gitProperties);
            json(exchange, versionInfo);
        });

        // Health
        path.addExactPath("/sysadmin/health", exchange ->
                json(exchange, Collections.singletonMap("status", "UP")));

        // Memory
        path.addExactPath("/sysadmin/memory", exchange ->
                json(exchange, MemoryUsageUtil.getInfoMap()));


        // Exception handlers
        final ExceptionHandler exceptionHandler = Handlers.exceptionHandler(path);
        for (Map.Entry<Class<? extends Throwable>, Function<Throwable, ApiError>> e : errorHandlers.entrySet())
        {
            exceptionHandler.addExceptionHandler(e.getKey(), exchange ->
            {
                final Throwable exc = exchange.getAttachment(ExceptionHandler.THROWABLE);
                final ApiError response = e.getValue().apply(exc);
                exchange.setStatusCode(response.getStatus());
                json(exchange, response);
            });
        }

        return exceptionHandler;
    }

    private Geometry getSimpleBoundary(final HttpServerExchange exchange)
    {
        final int id = requireIntParam(exchange, "id");
        final double maxTolerance = requireDoubleParam(exchange, "maxTolerance");
        return geodataService.findBoundaries(id, maxTolerance).orElseThrow(notNull("No boundary found for location with id " + id));
    }

    private void sendGeoJson(final HttpServerExchange exchange, final Geometry boundary)
    {
        exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(new GeoJsonWriter().write(boundary));
    }

    private void sendWkb(final HttpServerExchange exchange, final Geometry boundary)
    {
        exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/octet-stream");
        exchange.getResponseSender().send(ByteBuffer.wrap(new WKBWriter().write(boundary)));
    }

    private Geometry getPreviewGeometry(final HttpServerExchange exchange)
    {
        final int id = requireIntParam(exchange, "id");
        final double minLng = requireDoubleParam(exchange, "minLng");
        final double maxLng = requireDoubleParam(exchange, "maxLng");
        final double minLat = requireDoubleParam(exchange, "minLat");
        final double maxLat = requireDoubleParam(exchange, "maxLat");
        final int width = requireIntParam(exchange, "width");
        final int height = requireIntParam(exchange, "height");
        return geodataService.findBoundaries(id, new View(minLng, maxLng, minLat, maxLat, width, height)).orElseThrow(notNull("No boundary found for location with id " + id));
    }
}