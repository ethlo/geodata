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

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.restdocs.operation.Operation;
import org.springframework.web.method.HandlerMethod;

public class ResponseSchemaSnippet extends AbstractJacksonFieldSnippet
{
    public static final String NAME = "response-schema";
    
    public ResponseSchemaSnippet()
    {
        super(NAME, Collections.emptyMap());
    }
    
    @Override
    public String getFileName()
    {
        return NAME;
    }

    @Override
    public boolean hasContent(Operation operation)
    {
        return getType(getHandlerMethod(operation)) != null;
    }

    @Override
    protected Map<String, Object> createModel(Operation operation)
    {
        final HandlerMethod method = getHandlerMethod(operation);
        final Map<String, Object> model = new TreeMap<>();
        final Class<?> type = (Class<?>) getType(method);
        final String schemaString = type != null ? getSchemaString(operation, type, true) : "";
        model.put("response", schemaString);

        final Class<?> methodType = method.getReturnType().getParameterType();
        if (methodType == Page.class)
        {
            model.put("isPage", true);
        }
        else if (isCollection(methodType))
        {
            model.put("isCollection", true);
        }
        return model;
    }

    @Override
    protected Type getType(HandlerMethod method)
    {
        Class<?> returnType = method.getReturnType().getParameterType();
        if (returnType == ResponseEntity.class) {
            return firstGenericType(method.getReturnType());
        } else if (returnType == HttpEntity.class) {
            return firstGenericType(method.getReturnType());
        } else if (returnType == Page.class) {
            return firstGenericType(method.getReturnType());
        } else if (isCollection(returnType)) {
            return firstGenericType(method.getReturnType());
        } else if ("void".equals(returnType.getName())) {
            return null;
        } else {
            return returnType;
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
