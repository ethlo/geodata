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

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import com.ethlo.geodata.ApiError;
import com.ethlo.geodata.GeodataService;
import com.ethlo.geodata.Mapper;
import com.ethlo.geodata.dao.MetaDao;
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
    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);
    private final GeodataService geodataService;
    private final MetaDao metaDao;
    private final Mapper mapper;
    private final HttpHandler errorHandler = createErrorHandler();

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
        {
            stream.writeVal(ITU.formatUtc((Date) obj));
        });
    }

    private HttpHandler createErrorHandler()
    {
        return exchange ->
        {
            final Throwable exc = exchange.getAttachment(ExceptionHandler.THROWABLE);
            logger.info(exc.getMessage(), exc);
            final ApiError error = new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

            json(exchange, error);
        };
    }

    public HttpHandler handler()
    {
        final RoutingHandler routes = Handlers.routing()

                // Source data information
                .add(Methods.GET, "/v1/source", exchange -> json(exchange, metaDao.load()))

                .add(Methods.GET, "/v1/locations/{id}/boundaries", exchange -> exchange.getResponseSender().send("findBoundariesAsGeoJson"))

                .add(Methods.GET, "/v1/locations/{id}/boundaries.wkb", exchange -> exchange.getResponseSender().send("findBoundariesAsWkb"))

                .add(Methods.GET, "/v1/locations/ids", exchange -> exchange.getResponseSender().send("findByIds"))

                .add(Methods.GET, "/v1/locations/ip/{ip}", exchange -> {
                    final String ip = getStringParam(exchange, "ip").orElseThrow(missingParam("ip"));
                    json(exchange, Optional.ofNullable(geodataService.findByIp(InetUtil.inet(ip)))
                            .map(mapper::transform)
                            .orElseThrow(notNull("No location found for IP address " + ip)));
                })

                .add(Methods.GET, "/v1/locations/name/{name}", exchange ->
                {
                    final String name = getStringParam(exchange, "name").orElseThrow(missingParam("name"));
                    json(exchange, Mapper.toGeoLocationsSlice(geodataService.findByName(name, getPageable(exchange)).map(mapper::transform)));

                })

                .add(Methods.GET, "/v1/locations/{id}/children", exchange ->
                {
                    final int id = getIntParam(exchange, "id").orElseThrow(missingParam("id"));
                    json(exchange, Mapper.toGeolocationPage(geodataService.findChildren(id, getPageable(exchange)).map(mapper::transform)));
                })

                .add(Methods.GET, "/v1/continents/{continentCode}", exchange -> exchange.getResponseSender().send("findContinentByCode"))

                .add(Methods.GET, "/v1/countries", exchange -> exchange.getResponseSender().send("findCountries"))

                .add(Methods.GET, "/v1/continents/{continent}/countries", exchange -> exchange.getResponseSender().send("findCountriesOnContinent"))

                .add(Methods.GET, "/v1/countries/{countryCode}", exchange -> exchange.getResponseSender().send("findCountryByCode"))

                .add(Methods.GET, "/v1/locations/phone/{phone}", exchange -> exchange.getResponseSender().send("findCountryByPhone"))

                .add(Methods.GET, "/v1/countries/{countryCode}/children", exchange -> exchange.getResponseSender().send("findCountryChildren"))

                .add(Methods.GET, "/v1/locations/{id}", exchange ->
                {
                    final int id = getIntParam(exchange, "id").orElseThrow(notNull("id"));
                    json(exchange, Optional.ofNullable(geodataService.findById(id)).map(mapper::transform).orElseThrow(notNull("No location with id " + id)));
                })

                .add(Methods.GET, "/v1/locations/proximity", exchange -> exchange.getResponseSender().send("findNear"))

                .add(Methods.GET, "/v1/locations/{id}/parent", exchange -> exchange.getResponseSender().send("findParentLocation"))

                .add(Methods.GET, "/v1/locations/{id}/previewboundaries.wkb", exchange -> exchange.getResponseSender().send("findPreviewBoundaries"))

                .add(Methods.GET, "/v1/locations/{id}/previewboundaries", exchange -> exchange.getResponseSender().send("findPreviewBoundaries1"))

                .add(Methods.GET, "/v1/locations/coordinates", exchange -> exchange.getResponseSender().send("findProximity"))

                .add(Methods.GET, "/v1/locations/{id}/simpleboundaries.wkb", exchange -> exchange.getResponseSender().send("findSimpleBoundaries"))

                .add(Methods.GET, "/v1/locations/{id}/simpleboundaries", exchange -> exchange.getResponseSender().send("findSimpleBoundaries1"))

                .add(Methods.GET, "/v1/locations/contains", exchange -> exchange.getResponseSender().send("findWithin"))

                .add(Methods.GET, "/v1/locations/{id}/insideany/{ids}", exchange -> exchange.getResponseSender().send("insideAny"))

                .add(Methods.GET, "/v1/locations/{id}/contains/{child}", exchange -> exchange.getResponseSender().send("isLocationInside"))

                .add(Methods.GET, "/v1/continents", exchange -> exchange.getResponseSender().send("listContinents"))

                .add(Methods.GET, "/v1/locations/{id}/outsideall/{ids}", new HttpHandler()
                {
                    public void handleRequest(HttpServerExchange exchange) throws Exception
                    {
                        exchange.getResponseSender().send("outsideAll");
                    }
                });

        final PathHandler path = Handlers.path(routes)
                .addPrefixPath("/swagger-ui", new ResourceHandler(new ClassPathResourceManager(getClass().getClassLoader(), "META-INF/resources/webjars/swagger-ui/3.35.2")))
                .addExactPath("/spec.yaml", new ResourceHandler(new ClassPathResourceManager(getClass().getClassLoader(), "public/spec.yaml")))
                .addExactPath("/", new ResourceHandler(new ClassPathResourceManager(getClass().getClassLoader(), "public/index.html")));


        return Handlers.exceptionHandler(path)
                .addExceptionHandler(Exception.class, errorHandler);
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
        return getFirstParam(exchange, name).map(Integer::parseInt);
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
        return () -> new IllegalArgumentException("Missing parameter: " + name);
    }

    private PageRequest getPageable(final HttpServerExchange exchange)
    {
        final int page = getIntParam(exchange, "page").orElse(0);
        final int size = getIntParam(exchange, "size").orElse(25);
        return PageRequest.of(page, size < 0 || size > 10_000 ? size : 25);
    }

    private Supplier<EmptyResultDataAccessException> notNull(String errorMessage)
    {
        return () -> new EmptyResultDataAccessException(errorMessage, 1);
    }
}
