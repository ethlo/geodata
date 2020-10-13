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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.core.MethodParameter;
import org.springframework.restdocs.operation.Operation;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class RequestSchemaSnippet extends AbstractJacksonFieldSnippet
{
    public static final String NAME = "request-schema";

    protected final SnippetSchemaGenerator jsonSchemaGenerator;

    public RequestSchemaSnippet()
    {
        super(NAME, Collections.emptyMap());
        this.jsonSchemaGenerator = new SnippetSchemaGenerator();
    }

    @Override
    public String getFileName()
    {
        return NAME;
    }

    @Override
    public boolean hasContent(Operation operation)
    {
        return isPutOrPost(operation) && getType(getHandlerMethod(operation)) != null;
    }

    @Override
    protected Map<String, Object> createModel(Operation operation)
    {
        final HandlerMethod method = getHandlerMethod(operation);
        final Map<String, Object> model = new TreeMap<>();
        model.put("request", "");
        if (isPutOrPost(operation))
        {
            final Class<?> requestBodyType = (Class<?>) getType(method);
            final ObjectNode schema = (ObjectNode) getSchema(operation, requestBodyType, false);

            try
            {
                model.put("request", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema));
            }
            catch (JsonProcessingException exc)
            {
                throw new RuntimeException(exc);
            }
        }
        return model;
    }

    private boolean isRequestBody(MethodParameter param)
    {
        return param.getParameterAnnotation(RequestBody.class) != null;
    }

    private boolean isModelAttribute(MethodParameter param)
    {
        return param.getParameterAnnotation(ModelAttribute.class) != null;
    }

    private boolean isPutOrPost(Operation operation)
    {
        return Arrays.asList("POST", "PUT").contains(operation.getRequest().getMethod().toString().toUpperCase());
    }

    @Override
    protected Type getType(HandlerMethod method)
    {
        for (MethodParameter param : method.getMethodParameters())
        {
            if (isRequestBody(param) || isModelAttribute(param))
            {
                return getType(param);
            }
        }
        return null;
    }

    private Type getType(final MethodParameter param)
    {
        if (super.isCollection(param.getParameterType()))
        {
            return new GenericArrayType()
            {

                @Override
                public Type getGenericComponentType()
                {
                    return firstGenericType(param);
                }
            };
        }
        else
        {
            return param.getParameterType();
        }
    }

    @Override
    protected boolean shouldFailOnUndocumentedFields()
    {
        return false;
    }

    @Override
    public String getHeaderKey()
    {
        return NAME;
    }

    @Override
    protected String[] getTranslationKeys()
    {
        return null;
    }
}
