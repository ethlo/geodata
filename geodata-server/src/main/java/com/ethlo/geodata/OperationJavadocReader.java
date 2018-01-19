package com.ethlo.geodata;

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

import static com.google.common.collect.Lists.newArrayList;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import springfox.documentation.builders.OperationBuilder;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.service.Parameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.OperationBuilderPlugin;
import springfox.documentation.spi.service.contexts.OperationContext;
import springfox.documentation.spi.service.contexts.RequestMappingContext;
import springfox.documentation.spring.web.WebMvcRequestHandler;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class OperationJavadocReader implements OperationBuilderPlugin
{
    private static final Logger logger = LoggerFactory.getLogger(OperationJavadocReader.class);

    @Override
    public void apply(OperationContext context)
    {
        final Field requestContextField = ReflectionUtils.findField(OperationContext.class, "requestContext");
        requestContextField.setAccessible(true);
        final Field handlerField = ReflectionUtils.findField(RequestMappingContext.class, "handler");
        handlerField.setAccessible(true);
        final Field parametersField = ReflectionUtils.findField(OperationBuilder.class, "parameters");
        parametersField.setAccessible(true);
        
        try
        {
            final RequestMappingContext reqCtx = (RequestMappingContext) requestContextField.get(context);
            final WebMvcRequestHandler handler = (WebMvcRequestHandler) handlerField.get(reqCtx);
            @SuppressWarnings("unchecked")
            final List<Parameter> existingParams = (List<Parameter>) parametersField.get(context.operationBuilder());
            final Method method = handler.getHandlerMethod().getMethod();
            final String methodName = method.getName();
            
            final ObjectMapper mapper = new ObjectMapper();
            final String path = method.getDeclaringClass().getCanonicalName().replace('.', '/') + ".json";
            final ClassPathResource res = new ClassPathResource(path);
            
            final JsonNode doc = mapper.readTree(res.getInputStream());
            final JsonNode m = doc.path("methods");
            final String description = m.path(methodName).path("comment").textValue();
            context.operationBuilder().summary(description).notes(description);

            final List<Parameter> parameters = newArrayList();
            for (Parameter p : existingParams)
            {
                final String paramName = p.getName();
                final JsonNode jsonParam = m.path(methodName).path("parameters");
                final String paramDesc = jsonParam.path(paramName).textValue();
                parameters.add(new ParameterBuilder().parameterType(p.getParamType()).name(p.getName()).modelRef(p.getModelRef()).description(paramDesc).build());
            }
            
            context.operationBuilder().parameters(parameters);
        }
        catch (IllegalArgumentException | IllegalAccessException | IOException exc)
        {
            logger.warn(exc.getMessage(), exc);
        }
    }

    @Override
    public boolean supports(DocumentationType delimiter)
    {
        return true;
    }
}
