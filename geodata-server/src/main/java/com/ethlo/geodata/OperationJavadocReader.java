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

import com.fasterxml.classmate.members.ResolvedField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import springfox.documentation.builders.OperationBuilder;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.service.Parameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.ExpandedParameterBuilderPlugin;
import springfox.documentation.spi.service.OperationBuilderPlugin;
import springfox.documentation.spi.service.contexts.OperationContext;
import springfox.documentation.spi.service.contexts.ParameterExpansionContext;
import springfox.documentation.spi.service.contexts.RequestMappingContext;
import springfox.documentation.spring.web.WebMvcRequestHandler;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class OperationJavadocReader implements OperationBuilderPlugin, ExpandedParameterBuilderPlugin
{
    private static final Logger logger = LoggerFactory.getLogger(OperationJavadocReader.class);
    private static final ObjectMapper mapper = new ObjectMapper();

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
            @SuppressWarnings("unchecked") final List<Parameter> existingParams = (List<Parameter>) parametersField.get(context.operationBuilder());
            final Method method = handler.getHandlerMethod().getMethod();
            final String description = getMethodDescription(method);
            context.operationBuilder().summary(description).notes(description);

            final List<Parameter> parameters = newArrayList();
            for (Parameter p : existingParams)
            {
                final String paramDesc = getParameterDescription(method, p.getName());
                parameters.add(new ParameterBuilder().parameterType(p.getParamType()).name(p.getName()).modelRef(p.getModelRef()).description(paramDesc).build());
            }

            context.operationBuilder().parameters(parameters);
        }
        catch (IllegalArgumentException | IllegalAccessException | IOException exc)
        {
            logger.warn(exc.getMessage(), exc);
        }
    }

    private String getParameterDescription(Method method, String name) throws IOException
    {
        final JsonNode m = loadClassJavadoc(method.getDeclaringClass());
        final JsonNode jsonParam = m.path(method.getName()).path("parameters");
        return jsonParam.path(name).textValue();
    }

    private String getMethodDescription(final Method method) throws IOException
    {
        final JsonNode doc = loadClassJavadoc(method.getDeclaringClass());
        return doc.path("methods").path(method.getName()).path("comment").textValue();
    }

    private JsonNode loadClassJavadoc(final Class<?> type) throws IOException
    {
        final String path = type.getCanonicalName().replace('.', '/') + ".json";
        final ClassPathResource res = new ClassPathResource(path);
        return mapper.readTree(res.getInputStream());
    }

    @Override
    public boolean supports(DocumentationType delimiter)
    {
        return true;
    }

    @Override
    public void apply(ParameterExpansionContext context)
    {
        final ResolvedField rField = context.getField();
        final Field field = rField.getRawMember();
        try
        {
            final JsonNode classDoc = loadClassJavadoc(field.getDeclaringClass());
            final String description = classDoc.path("fields").path(field.getName()).path("comment").textValue();
            context.getParameterBuilder().description(description);
        }
        catch (IOException exc)
        {
            throw new RuntimeException(exc);
        }
    }
}
