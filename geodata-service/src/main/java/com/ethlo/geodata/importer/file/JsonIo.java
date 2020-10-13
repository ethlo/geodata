package com.ethlo.geodata.importer.file;

/*-
 * #%L
 * Geodata service
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

import java.io.File;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

public abstract class JsonIo<T>
{
    public static final byte NEW_LINE = (byte) "\n".charAt(0);

    protected static final ObjectMapper mapper;

    static
    {
        final JsonFactory f = new JsonFactory();
        f.enable(JsonGenerator.Feature.ESCAPE_NON_ASCII);
        f.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        mapper = new ObjectMapper(f);
        mapper.registerModule(new AfterburnerModule());
        mapper.setSerializationInclusion(Include.NON_EMPTY);
    }

    protected final File file;
    protected final Class<T> type;

    public JsonIo(File file, Class<T> type)
    {
        this.file = file;
        this.type = type;
    }
}
