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
import static springfox.documentation.spi.schema.contexts.ModelContext.inputParam;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.ethlo.geodata.model.Coordinates;
import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.google.common.base.Function;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.schema.ModelReference;
import springfox.documentation.schema.ResolvedTypes;
import springfox.documentation.schema.TypeNameExtractor;
import springfox.documentation.service.Parameter;
import springfox.documentation.service.ResolvedMethodParameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.schema.contexts.ModelContext;
import springfox.documentation.spi.service.OperationBuilderPlugin;
import springfox.documentation.spi.service.contexts.OperationContext;
import springfox.documentation.spi.service.contexts.ParameterContext;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class OperationPageableParameterReader implements OperationBuilderPlugin
{
    private final TypeNameExtractor nameExtractor;
    private final TypeResolver resolver;
    private final ResolvedType pageableType;
    private final ResolvedType coordinatesType;

    @Autowired
    public OperationPageableParameterReader(TypeNameExtractor nameExtractor, TypeResolver resolver)
    {
        this.nameExtractor = nameExtractor;
        this.resolver = resolver;
        this.pageableType = resolver.resolve(Pageable.class);
        this.coordinatesType = resolver.resolve(Coordinates.class);
    }

    @Override
    public void apply(OperationContext context)
    {
        List<ResolvedMethodParameter> methodParameters = context.getParameters();
        List<Parameter> parameters = newArrayList();

        for (ResolvedMethodParameter methodParameter : methodParameters)
        {
            ResolvedType resolvedType = methodParameter.getParameterType();

            if (pageableType.equals(resolvedType))
            {
                ParameterContext parameterContext = new ParameterContext(methodParameter, new ParameterBuilder(), context.getDocumentationContext(), context.getGenericsNamingStrategy(), context);
                Function<ResolvedType, ? extends ModelReference> factory = createModelRefFactory(parameterContext);
                ModelReference intModel = factory.apply(resolver.resolve(Integer.TYPE));
                parameters.add(new ParameterBuilder().parameterType("query").name("page").modelRef(intModel).description("Results page you want to retrieve (0..N)").build());
                parameters.add(new ParameterBuilder().parameterType("query").name("size").modelRef(intModel).description("Number of records per page").build());
                context.operationBuilder().parameters(parameters);
            }
            else if (coordinatesType.equals(resolvedType))
            {
                ParameterContext parameterContext = new ParameterContext(methodParameter, new ParameterBuilder(), context.getDocumentationContext(), context.getGenericsNamingStrategy(), context);
                Function<ResolvedType, ? extends ModelReference> factory = createModelRefFactory(parameterContext);
                final ModelReference doubleModel = factory.apply(resolver.resolve(Double.TYPE));
                parameters.add(new ParameterBuilder().parameterType("query").name("lat").modelRef(doubleModel).description("Latitude").build());
                parameters.add(new ParameterBuilder().parameterType("query").name("lng").modelRef(doubleModel).description("Longitude").build());
                context.operationBuilder().parameters(parameters);
            }
        }
    }

    @Override
    public boolean supports(DocumentationType delimiter)
    {
        return true;
    }

    private Function<ResolvedType, ? extends ModelReference> createModelRefFactory(ParameterContext context)
    {
        ModelContext modelContext = inputParam("", context.resolvedMethodParameter().getParameterType().getErasedType(), context.getDocumentationType(), context.getAlternateTypeProvider(),
                context.getGenericNamingStrategy(), context.getIgnorableParameterTypes()
        );
        return ResolvedTypes.modelRefFactory(modelContext, nameExtractor);
    }
}
