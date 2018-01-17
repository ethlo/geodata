package com.ethlo.geodata;


import com.ethlo.geodata.model.Coordinates;
import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.google.common.base.Function;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.schema.ResolvedTypes;
import springfox.documentation.schema.TypeNameExtractor;
import springfox.documentation.schema.ModelReference;
import springfox.documentation.service.Parameter;
import springfox.documentation.service.ResolvedMethodParameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.schema.contexts.ModelContext;
import springfox.documentation.spi.service.OperationBuilderPlugin;
import springfox.documentation.spi.service.contexts.OperationContext;
import springfox.documentation.spi.service.contexts.ParameterContext;

import static com.google.common.collect.Lists.newArrayList;
import static springfox.documentation.spi.schema.contexts.ModelContext.inputParam;

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
                        context.getGenericNamingStrategy(), context.getIgnorableParameterTypes());
        return ResolvedTypes.modelRefFactory(modelContext, nameExtractor);
    }
}