/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

import static capital.scalable.restdocs.OperationAttributeHelper.getConstraintReader;
import static capital.scalable.restdocs.OperationAttributeHelper.getJavadocReader;
import static capital.scalable.restdocs.OperationAttributeHelper.getObjectMapper;
import static java.util.Collections.singletonList;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.restdocs.operation.Operation;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.util.Assert;
import org.springframework.web.method.HandlerMethod;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import capital.scalable.restdocs.constraints.ConstraintReader;
import capital.scalable.restdocs.jackson.FieldDocumentationGenerator;
import capital.scalable.restdocs.javadoc.JavadocReader;
import capital.scalable.restdocs.payload.JacksonFieldProcessingException;
import capital.scalable.restdocs.section.SectionSupport;
import capital.scalable.restdocs.snippet.StandardTableSnippet;

public abstract class AbstractJacksonFieldSnippet extends StandardTableSnippet implements SectionSupport
{
    protected static final Logger logger = LoggerFactory.getLogger(RequestSchemaSnippet.class);
    protected final ObjectMapper objectMapper;
    private final SnippetSchemaGenerator generator = new SnippetSchemaGenerator();
    
    private static Map<Class<?>, JsonNode> allSchemas = new LinkedHashMap<>();

    protected AbstractJacksonFieldSnippet(String snippetName, Map<String, Object> attributes)
    {
        super(snippetName, attributes);
        this.objectMapper = new ObjectMapper();
    }

    protected Collection<FieldDescriptor> createFieldDescriptors(Operation operation, Type signatureType) {
        ObjectMapper objectMapper = getObjectMapper(operation);
        JavadocReader javadocReader = getJavadocReader(operation);
        ConstraintReader constraintReader = getConstraintReader(operation);

        Map<String, FieldDescriptor> fieldDescriptors = new LinkedHashMap<>();

        try {
            for (Type type : resolveActualTypes(signatureType)) {
                resolveFieldDescriptors(fieldDescriptors, type, objectMapper,
                        javadocReader, constraintReader);
            }
        } catch (JsonMappingException e) {
            throw new JacksonFieldProcessingException("Error while parsing fields", e);
        }
        
        return fieldDescriptors.values();
    }

    protected Type firstGenericType(MethodParameter param)
    {
        return ((ParameterizedType) param.getGenericParameterType()).getActualTypeArguments()[0];
    }

    protected abstract Type getType(HandlerMethod method);

    protected abstract boolean shouldFailOnUndocumentedFields();

    protected boolean isCollection(Class<?> type)
    {
        return Collection.class.isAssignableFrom(type);
    }

    private Collection<Type> resolveActualTypes(Type type) {
        if (type instanceof Class) {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            JsonSubTypes jsonSubTypes = (JsonSubTypes) ((Class) type).getAnnotation(JsonSubTypes.class);
            if (jsonSubTypes != null) {
                Collection<Type> types = new ArrayList<>();
                for (JsonSubTypes.Type subType : jsonSubTypes.value()) {
                    types.add(subType.value());
                }
                return types;
            }
        }

        return singletonList(type);
    }

    private void resolveFieldDescriptors(Map<String, FieldDescriptor> fieldDescriptors,
            Type type, ObjectMapper objectMapper, JavadocReader javadocReader,
            ConstraintReader constraintReader) throws JsonMappingException {
        FieldDocumentationGenerator generator = new FieldDocumentationGenerator(
                objectMapper.writer(), objectMapper.getDeserializationConfig(), javadocReader,
                constraintReader);
        List<FieldDescriptor> descriptors = generator.generateDocumentation(type,
                objectMapper.getTypeFactory());
        for (FieldDescriptor descriptor : descriptors) {
            if (fieldDescriptors.get(descriptor.getPath()) == null) {
                fieldDescriptors.put(descriptor.getPath(), descriptor);
            }
        }
    }
    
    protected Collection<FieldDescriptor> createFieldDescriptors(Operation operation, HandlerMethod handlerMethod) {
        ObjectMapper objectMapper = getObjectMapper(operation);

        JavadocReader javadocReader = getJavadocReader(operation);
        ConstraintReader constraintReader = getConstraintReader(operation);

        Map<String, FieldDescriptor> fieldDescriptors = new LinkedHashMap<>();

        Type signatureType = getType(handlerMethod);
        if (signatureType != null) {
            try {
                for (Type type : resolveActualTypes(signatureType)) {
                    resolveFieldDescriptors(fieldDescriptors, type, objectMapper,
                            javadocReader, constraintReader);
                }
            } catch (JsonMappingException e) {
                throw new JacksonFieldProcessingException("Error while parsing fields", e);
            }
        }
        return fieldDescriptors.values();
    }

    @Override
    public String getFileName() {
        return getSnippetName();
    }

    @Override
    public boolean hasContent(Operation operation) {
        return getType(getHandlerMethod(operation)) != null;
    }
    
    protected HandlerMethod getHandlerMethod(Operation operation)
    {
        final String key = HandlerMethod.class.getName();
        return (HandlerMethod) operation.getAttributes().get(key);
    }
    
    protected ObjectNode getSchema(Operation operation, final Class<?> pojo, boolean isResponse)
    {
        try
        {
            final ObjectNode schema = (ObjectNode) generator.generateSchema(pojo, isResponse);
            
            final Collection<FieldDescriptor> fieldDescriptors = createFieldDescriptors(operation, pojo);
            
            // Add field descriptions on top of JSON schema
            fieldDescriptors.forEach(desc->
            {
                final String[] pathArr = org.apache.commons.lang3.StringUtils.split(desc.getPath(), '.');
                final Iterator<String> path = Arrays.asList(pathArr).iterator();
                final ObjectNode definitionsNode = schema.get("definitions") != null ? (ObjectNode) schema.get("definitions") : schema.objectNode();
                handleSchema(pojo, schema, definitionsNode, desc, path);
            });
            
            allSchemas.put(pojo, schema);
            ((ObjectNode)schema).remove("definitions");
            
            return schema;
        }
        catch (RuntimeException exc)
        {
            exc.printStackTrace();
            throw exc;
        }
    }
    
    protected String getSchemaString(Operation operation, final Class<?> pojo, boolean isResponse)
    {
        try
        {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(getSchema(operation, pojo, isResponse));
        }
        catch (JsonProcessingException exc)
        {
            throw new RuntimeException(exc);
        }
    }
    
    protected Class<?> extract(Class<?> c)
    {
        return c;
    }
    
    protected void handleSchema(final Class<?> type, final ObjectNode root, final ObjectNode definitions, FieldDescriptor desc, final Iterator<String> path)
    {
        ObjectNode lastNode = (ObjectNode) root.get("properties");
        Class<?> lastField = type;
        while (path.hasNext() && lastNode != null && lastField != null)
        {
            final String p = StringUtils.replace(path.next(), "[]", "");
            final Field f = FieldUtils.getField(lastField, p, true);
            
            final Class<?> field = f != null ? (Collection.class.isAssignableFrom(f.getType()) ? (Class<?>)((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0] : f.getType()) : null;
            final JsonNode currentNode = lastNode.get(p);
            if (currentNode instanceof ObjectNode && field != null)
            {
                if (! path.hasNext())
                {
                    // Leaf of path definition, set description
                    final String description = desc.getDescription().toString();
                    ((ObjectNode) currentNode).put("description", description);
                }
                
                final String typeDef = currentNode.path("type").asText();
                if (! StringUtils.isNotEmpty(typeDef))
                {
                    handleRef(field, root, definitions, (ObjectNode) currentNode, desc, path);
                }
            }
            
            lastNode = currentNode instanceof ObjectNode ? (ObjectNode)currentNode : null;
            lastField = field;
        }
    }
    
    private void handleRef(Class<?> type, ObjectNode root, ObjectNode definitionsNode, ObjectNode currentNode, FieldDescriptor descriptor, Iterator<String> pathParts)
    {
        final String typeStr = currentNode.fieldNames().next();
        switch (typeStr)
        {
            case "$ref":
                final String refValue = currentNode.get(typeStr).asText();
                Assert.notNull(refValue, "Value of $ref should not be null");
                final String typeName = refValue.substring(refValue.lastIndexOf('/') + 1);
                final ObjectNode refType = (ObjectNode) definitionsNode.get(typeName);
                handleSchema(type, refType, definitionsNode, descriptor, pathParts);
                allSchemas.put(type, refType);
                break;
                
            default:
                logger.warn("Unhandled type: {}", typeStr);
        }
    }

    public static Map<Class<?>, JsonNode> getAllSchemas()
    {
        return allSchemas;
    }
}
