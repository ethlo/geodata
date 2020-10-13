package com.ethlo.geodata.restdocs;

/*-
 * #%L
 * geodata-server
 * %%
 * Copyright (C) 2017 - 2018 Morten Haraldsen (ethlo)
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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kjetland.jackson.jsonSchema.JsonSchemaConfig;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;

public class SnippetSchemaGenerator
{
    private final JsonSchemaGenerator generator;
    private final ObjectMapper objectMapper;

    public SnippetSchemaGenerator()
    {
        this.objectMapper = new ObjectMapper();

        final boolean autoGenerateTitleForProperties = false;
        final Optional<String> defaultArrayFormat = Optional.empty();
        final boolean useOneOfForOption = false;
        final boolean useOneOfForNullables = false;
        final boolean usePropertyOrdering = false;
        final boolean hidePolymorphismTypeProperty = false;
        final boolean disableWarnings = false;
        final boolean useMinLengthForNotNull = false;
        final boolean useTypeIdForDefinitionName = false;
        final Map<String, String> customType2FormatMapping = new LinkedHashMap<>();

        customType2FormatMapping.put(LocalDateTime.class.getCanonicalName(), "datetime-local");
        customType2FormatMapping.put(OffsetDateTime.class.getCanonicalName(), "date-time");
        customType2FormatMapping.put(ZonedDateTime.class.getCanonicalName(), "date-time");
        customType2FormatMapping.put(Date.class.getCanonicalName(), "date-time");

        final boolean useMultipleEditorSelectViaProperty = false;
        final Set<Class<?>> uniqueItemClasses = new java.util.LinkedHashSet<>();
        final Map<Class<?>, Class<?>> classTypeReMapping = new LinkedHashMap<>();

        classTypeReMapping.put(ZonedDateTime.class, String.class);
        classTypeReMapping.put(LocalDateTime.class, String.class);

        final Map<String, Supplier<JsonNode>> jsonSuppliers = new LinkedHashMap<>();

        final JsonSchemaConfig config = JsonSchemaConfig.create(autoGenerateTitleForProperties, defaultArrayFormat,
                useOneOfForOption, useOneOfForNullables, usePropertyOrdering,
                hidePolymorphismTypeProperty, disableWarnings, useMinLengthForNotNull,
                useTypeIdForDefinitionName, customType2FormatMapping,
                useMultipleEditorSelectViaProperty, uniqueItemClasses, classTypeReMapping, jsonSuppliers
        );

        this.generator = new JsonSchemaGenerator(objectMapper, config);
    }

    public JsonNode generateSchema(Class<?> type, boolean isResponse)
    {
        return walk(generator.generateJsonSchema(type), isResponse);
    }

    private JsonNode walk(JsonNode schema, boolean isResponse)
    {
        fix(schema, isResponse);
        for (JsonNode n : schema)
        {
            fix(n, isResponse);
            walk(n, isResponse);
        }
        return schema;
    }

    private void fix(JsonNode n, boolean isResponse)
    {
        if (n.isObject())
        {
            final ObjectNode o = (ObjectNode) n;

            // Fix type of date and date-time
            final String format = n.path("format").textValue();
            if ("date".equalsIgnoreCase(format) || "date-time".equalsIgnoreCase(format))
            {
                o.put("type", "string");
            }

            if (isResponse && o.get("additionalProperties") != null)
            {
                o.put("additionalProperties", true);
            }

            // Remove useless titles
            o.remove("title");
        }
    }
}
