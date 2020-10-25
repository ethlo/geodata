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

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.data.domain.PageRequest;

import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationWithPath;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import com.jsoniter.output.EncodingMode;
import com.jsoniter.output.JsonStream;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

public class ServerHandler
{
    private final GeodataService geodataService;

    public ServerHandler(final GeodataService geodataService)
    {
        this.geodataService = geodataService;

        JsonStream.setMode(EncodingMode.DYNAMIC_MODE);
    }

    public RoutingHandler handler()
    {
        return Handlers.routing()

                .add(Methods.GET, "/v1/locations/{id}/boundaries", exchange -> exchange.getResponseSender().send("findBoundariesAsGeoJson"))

                .add(Methods.GET, "/v1/locations/{id}/boundaries.wkb", exchange -> exchange.getResponseSender().send("findBoundariesAsWkb"))

                .add(Methods.GET, "/v1/locations/ids", exchange -> exchange.getResponseSender().send("findByIds"))

                .add(Methods.GET, "/v1/locations/ip/{ip}", exchange -> exchange.getResponseSender().send("findByIp"))

                .add(Methods.GET, "/v1/locations/name/{name}", exchange -> exchange.getResponseSender().send("findByName"))

                .add(Methods.GET, "/v1/locations/{id}/children", exchange ->
                {
                    final int id = getIntParam(exchange, "id");
                    json(exchange, geodataService.findChildren(id, pageable(exchange)));
                })

                .add(Methods.GET, "/v1/continents/{continentCode}", exchange -> exchange.getResponseSender().send("findContinentByCode"))

                .add(Methods.GET, "/v1/countries", exchange -> exchange.getResponseSender().send("findCountries"))

                .add(Methods.GET, "/v1/continents/{continent}/countries", exchange -> exchange.getResponseSender().send("findCountriesOnContinent"))

                .add(Methods.GET, "/v1/countries/{countryCode}", exchange -> exchange.getResponseSender().send("findCountryByCode"))

                .add(Methods.GET, "/v1/locations/phone/{phone}", exchange -> exchange.getResponseSender().send("findCountryByPhone"))

                .add(Methods.GET, "/v1/countries/{countryCode}/children", exchange -> exchange.getResponseSender().send("findCountryChildren"))

                .add(Methods.GET, "/v1/locations/{id}", exchange ->
                {
                    final int id = getIntParam(exchange, "id");
                    json(exchange, withPath(geodataService.findById(id)));
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

    }

    private void json(final HttpServerExchange exchange, final Object obj) throws JsonProcessingException
    {
        exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(JsonStream.serialize(obj));
    }

    private Integer getIntParam(final HttpServerExchange exchange, final String name)
    {
        final Map<String, Deque<String>> params = exchange.getQueryParameters();
        return Optional.ofNullable(params.get(name)).map(Deque::getFirst).map(Integer::parseInt).orElse(null);
    }

    private Supplier<RuntimeException> missingParam(String name)
    {
        return () -> new IllegalArgumentException("Missing parameter: " + name);
    }

    private GeoLocationWithPath withPath(final GeoLocation location)
    {
        final List<GeoLocation> path = Lists.reverse(geodataService.findPath(location.getId()));
        return new GeoLocationWithPath(location, path);
    }

    private PageRequest pageable(final HttpServerExchange exchange)
    {
        final Integer page = getIntParam(exchange, "page");
        final Integer size = getIntParam(exchange, "size");
        return PageRequest.of(page != null ? page : 0, size != null && size > 0 & size <= 10_000 ? size : 25);
    }
}
