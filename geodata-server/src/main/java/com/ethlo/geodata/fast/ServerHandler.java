package com.ethlo.geodata.fast;

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

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.ApiError;
import com.ethlo.geodata.GeodataService;
import com.ethlo.geodata.InvalidDataException;
import com.ethlo.geodata.Mapper;
import com.ethlo.geodata.dao.MetaDao;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.View;
import com.ethlo.geodata.rest.v1.model.V1Continent;
import com.ethlo.geodata.rest.v1.model.V1GeoLocation;
import com.ethlo.geodata.rest.v1.model.V1PageContinent;
import com.ethlo.geodata.util.InetUtil;
import com.ethlo.geodata.util.JsonUtil;
import com.ethlo.time.ITU;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.jsoniter.output.EncodingMode;
import com.jsoniter.output.JsonStream;
import com.jsoniter.spi.JsoniterSpi;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.ExceptionHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

public class ServerHandler
{
    private final GeodataService geodataService;
    private final MetaDao metaDao;
    private final Mapper mapper;

    public ServerHandler(final GeodataService geodataService, final MetaDao metaDao)
    {
        this.geodataService = geodataService;
        this.mapper = new Mapper(geodataService);
        this.metaDao = metaDao;

        configureJsoniter();
    }

    private void configureJsoniter()
    {
        JsonStream.setMode(EncodingMode.DYNAMIC_MODE);
        JsoniterSpi.registerTypeEncoder(Date.class, (obj, stream) ->
                stream.writeVal(ITU.formatUtc((Date) obj)));
    }

    public HttpHandler handler(Map<Class<? extends Throwable>, Function<Throwable, ApiError>> errorHandlers)
    {
        final RoutingHandler routes = Handlers.routing()

                // Source data information
                .add(Methods.GET, "/v1/source", exchange -> json(exchange, metaDao.load()))

                .add(Methods.GET, "/v1/locations/{id}/boundaries", exchange ->
                {
                    final int id = requireIntParam(exchange, "id");
                    final byte[] boundary = Optional.ofNullable(geodataService.findBoundaries(id)).orElseThrow(notNull("No boundary for id " + id));
                    exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");
                    exchange.getResponseSender().send(ByteBuffer.wrap(boundary));
                })

                .add(Methods.GET, "/v1/locations/{id}/boundaries.wkb", exchange ->
                {
                    final int id = requireIntParam(exchange, "id");
                    final byte[] boundary = Optional.ofNullable(geodataService.findBoundaries(id)).orElseThrow(notNull("No boundary for id " + id));
                    final Geometry geom = new GeoJsonReader().read(new InputStreamReader(new ByteArrayInputStream(boundary), StandardCharsets.UTF_8));
                    final byte[] wkb = new WKBWriter().write(geom);
                    exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/wkb");
                    exchange.getResponseSender().send(ByteBuffer.wrap(wkb));
                })

                .add(Methods.GET, "/v1/locations/ids/{ids}", exchange ->
                {
                    final List<Integer> ids = getIntList(exchange, "ids").orElseThrow(missingParam("ids"));
                    json(exchange, ids.stream().map(geodataService::findById).collect(Collectors.toList()));
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
                    final V1GeoLocation location = Optional.ofNullable(geodataService.findByCoordinate(coordinates, maxDistance)).map(mapper::transform).orElseThrow(notNull("Unable to determine nearest location of " + lat + "," + lng));
                    json(exchange, location);
                })

                .add(Methods.GET, "/v1/locations/{id}/previewboundaries", exchange ->
                {
                    final int id = requireIntParam(exchange, "id");
                    final double minLng = requireDoubleParam(exchange, "minLng");
                    final double maxLng = requireDoubleParam(exchange, "maxLng");
                    final double minLat = requireDoubleParam(exchange, "minLat");
                    final double maxLat = requireDoubleParam(exchange, "maxLat");
                    final int width = requireIntParam(exchange, "width");
                    final int height = requireIntParam(exchange, "height");
                    final Geometry boundary = Optional.ofNullable(geodataService.findBoundaries(id, new View(minLng, maxLng, minLat, maxLat, width, height))).orElseThrow(notNull("No boundary found for location with id " + id));
                    exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");
                    exchange.getResponseSender().send(new GeoJsonWriter().write(boundary));
                })

                .add(Methods.GET, "/v1/locations/contains", exchange -> exchange.getResponseSender().send("findWithin"))

                .add(Methods.GET, "/v1/locations/{id}/outsideall/{ids}", exchange -> exchange.getResponseSender().send("outsideAll"));

        // TODO: Evaluate for removal
        //.add(Methods.GET, "/v1/locations/{id}/previewboundaries.wkb", exchange -> exchange.getResponseSender().send("findPreviewBoundaries"))
        //.add(Methods.GET, "/v1/locations/{id}/simpleboundaries.wkb", exchange -> exchange.getResponseSender().send("findSimpleBoundaries"))
        //.add(Methods.GET, "/v1/locations/{id}/simpleboundaries", exchange -> exchange.getResponseSender().send("findSimpleBoundaries1"))

        final PathHandler path = Handlers.path(routes)
                .addPrefixPath("/swagger-ui", new ResourceHandler(new ClassPathResourceManager(getClass().getClassLoader(), "META-INF/resources/webjars/swagger-ui/3.35.2")))
                .addExactPath("/spec.yaml", new ResourceHandler(new ClassPathResourceManager(getClass().getClassLoader(), "public/spec.yaml")))
                .addExactPath("/", new ResourceHandler(new ClassPathResourceManager(getClass().getClassLoader(), "public/index.html")));


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

    private Pageable pageable(final HttpServerExchange exchange)
    {
        final int page = getIntParam(exchange, "page").orElse(0);
        final int size = getIntParam(exchange, "size").orElse(25);
        return PageRequest.of(page, size);
    }

    private String requireStringParam(final HttpServerExchange exchange, final String name)
    {
        return getStringParam(exchange, name).orElseThrow(missingParam(name));
    }

    private Integer requireIntParam(final HttpServerExchange exchange, final String name)
    {
        return getIntParam(exchange, name).orElseThrow(missingParam(name));
    }

    private double requireDoubleParam(final HttpServerExchange exchange, final String name)
    {
        return getDoubleParam(exchange, name).orElseThrow(missingParam(name));
    }

    private Optional<Double> getDoubleParam(final HttpServerExchange exchange, final String name)
    {
        return getFirstParam(exchange, name).map(this::parseDouble);
    }

    private Optional<List<Integer>> getIntList(final HttpServerExchange exchange, final String name)
    {
        return getStringParam(exchange, name)
                .map(StringUtils::commaDelimitedListToSet)
                .map(c -> c.stream().map(Integer::parseInt)
                        .collect(Collectors.toList()));
    }

    private void json(final HttpServerExchange exchange, final Object obj) throws JsonProcessingException
    {
        exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");
        final boolean pretty = getBooleanParam(exchange, "pretty").orElse(false);
        if (!pretty)
        {
            exchange.getResponseSender().send(JsonStream.serialize(obj));
        }
        else
        {
            exchange.getResponseSender().send(ByteBuffer.wrap(JsonUtil.getMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(obj)));
        }
    }

    private Optional<Integer> getIntParam(final HttpServerExchange exchange, final String name)
    {
        return getFirstParam(exchange, name).map(this::parseInt);
    }

    private int parseInt(final String s)
    {
        try
        {
            return Integer.parseInt(s);
        }
        catch (NumberFormatException exc)
        {
            throw new InvalidDataException("Not an integer value: " + s);
        }
    }

    private double parseDouble(final String s)
    {
        try
        {
            return Double.parseDouble(s);
        }
        catch (NumberFormatException exc)
        {
            throw new InvalidDataException("Not a decimal value: " + s);
        }
    }

    private Optional<Boolean> getBooleanParam(final HttpServerExchange exchange, final String name)
    {
        return getFirstParam(exchange, name).map(Boolean::parseBoolean);
    }

    private Optional<String> getStringParam(final HttpServerExchange exchange, final String name)
    {
        return getFirstParam(exchange, name);
    }

    public Optional<String> getFirstParam(final HttpServerExchange exchange, final String name)
    {
        final Map<String, Deque<String>> params = exchange.getQueryParameters();
        return Optional.ofNullable(params.get(name)).map(Deque::getFirst);
    }

    private Supplier<RuntimeException> missingParam(String name)
    {
        return () -> new MissingParameterException(name);
    }

    private PageRequest getPageable(final HttpServerExchange exchange)
    {
        final int page = getIntParam(exchange, "page").orElse(0);
        final int size = getIntParam(exchange, "size").orElse(25);
        return PageRequest.of(page, size > 0 && size <= 10_000 ? size : 25);
    }

    private Supplier<EmptyResultDataAccessException> notNull(String errorMessage)
    {
        return () -> new EmptyResultDataAccessException(errorMessage, 1);
    }
}
