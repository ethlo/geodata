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

import java.util.Map;
import java.util.Optional;

import org.springframework.restdocs.operation.Operation;
import org.springframework.restdocs.snippet.TemplatedSnippet;
import org.springframework.web.method.HandlerMethod;

import capital.scalable.restdocs.section.SectionSupport;

public abstract class AbstractAnnotationSnippet extends TemplatedSnippet implements SectionSupport
{
    protected AbstractAnnotationSnippet(String snippetName, Map<String, Object> attributes)
    {
        super(snippetName, attributes);
    }

    protected Optional<HandlerMethod> getHandlerMethod(Operation operation)
    {
        final String key = HandlerMethod.class.getName();
        final HandlerMethod method = (HandlerMethod) operation.getAttributes().get(key);
        return Optional.ofNullable(method);
    }
}
