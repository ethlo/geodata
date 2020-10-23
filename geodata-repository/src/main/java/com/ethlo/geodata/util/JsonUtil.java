package com.ethlo.geodata.util;

/*-
 * #%L
 * geodata-common
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonUtil
{
    private static final ObjectMapper mapper;

    static
    {
        mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        mapper.findAndRegisterModules();
    }

    public static <T> T read(final Path path, final Class<T> type)
    {
        try
        {
            return mapper.readValue(path.toFile(), type);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    public static <T> T read(final Path path, final TypeReference<T> type)
    {
        try
        {
            return mapper.readValue(path.toFile(), type);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    public static void write(final Path path, final Object data)
    {
        try
        {
            mapper.writeValue(path.toFile(), data);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }
}
