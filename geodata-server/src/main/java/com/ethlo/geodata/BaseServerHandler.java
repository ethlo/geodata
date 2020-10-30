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

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.util.JsonUtil;
import com.ethlo.time.ITU;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.jsoniter.output.EncodingMode;
import com.jsoniter.output.JsonStream;
import com.jsoniter.spi.JsoniterSpi;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.util.Headers;

public class BaseServerHandler
{
    public BaseServerHandler()
    {
        JsonStream.setMode(EncodingMode.DYNAMIC_MODE);
        JsoniterSpi.registerTypeEncoder(Date.class, (obj, stream) ->
                stream.writeVal(ITU.formatUtc((Date) obj)));
    }

    protected Pageable pageable(final HttpServerExchange exchange)
    {
        final int page = getIntParam(exchange, "page").orElse(0);
        final int size = getIntParam(exchange, "size").orElse(25);
        return PageRequest.of(page, size);
    }

    protected String requireStringParam(final HttpServerExchange exchange, final String name)
    {
        return getStringParam(exchange, name).orElseThrow(missingParam(name));
    }

    protected Integer requireIntParam(final HttpServerExchange exchange, final String name)
    {
        return getIntParam(exchange, name).orElseThrow(missingParam(name));
    }

    protected double requireDoubleParam(final HttpServerExchange exchange, final String name)
    {
        return getDoubleParam(exchange, name).orElseThrow(missingParam(name));
    }

    protected Optional<Double> getDoubleParam(final HttpServerExchange exchange, final String name)
    {
        return getFirstParam(exchange, name).map(this::parseDouble);
    }

    protected Optional<List<Integer>> getIntList(final HttpServerExchange exchange, final String name)
    {
        return getStringParam(exchange, name)
                .map(StringUtils::commaDelimitedListToSet)
                .map(c -> c.stream().map(Integer::parseInt)
                        .collect(Collectors.toList()));
    }

    protected void json(final HttpServerExchange exchange, final Object obj) throws JsonProcessingException
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

    protected Optional<Integer> getIntParam(final HttpServerExchange exchange, final String name)
    {
        return getFirstParam(exchange, name).map(this::parseInt);
    }

    protected int parseInt(final String s)
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

    protected double parseDouble(final String s)
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

    protected Optional<Boolean> getBooleanParam(final HttpServerExchange exchange, final String name)
    {
        return getFirstParam(exchange, name).map(Boolean::parseBoolean);
    }

    protected Optional<String> getStringParam(final HttpServerExchange exchange, final String name)
    {
        return getFirstParam(exchange, name);
    }

    public Optional<String> getFirstParam(final HttpServerExchange exchange, final String name)
    {
        final Map<String, Deque<String>> params = exchange.getQueryParameters();
        return Optional.ofNullable(params.get(name)).map(Deque::getFirst);
    }

    protected Supplier<RuntimeException> missingParam(String name)
    {
        return () -> new MissingParameterException(name);
    }

    protected PageRequest getPageable(final HttpServerExchange exchange)
    {
        final int page = getIntParam(exchange, "page").orElse(0);
        final int size = getIntParam(exchange, "size").orElse(25);
        return PageRequest.of(page, size > 0 && size <= 10_000 ? size : 25);
    }

    protected Supplier<EmptyResultDataAccessException> notNull(String errorMessage)
    {
        return () -> new EmptyResultDataAccessException(errorMessage, 1);
    }

    protected ClassPathResourceManager classpathResource(final String s)
    {
        return new ClassPathResourceManager(getClass().getClassLoader(), s);
    }
}
