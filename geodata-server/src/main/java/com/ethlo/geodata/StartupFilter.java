package com.ethlo.geodata;

/*-
 * #%L
 * geodata-server
 * %%
 * Copyright (C) 2017 - 2020 Morten Haraldsen (ethlo)
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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;

import javax.annotation.Nonnull;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ethlo.geodata.progress.StatefulProgressListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.io.ByteStreams;

@Component
public class StartupFilter extends OncePerRequestFilter
{
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private StatefulProgressListener progress = new StatefulProgressListener();
    private boolean enabled = true;

    public StartupFilter()
    {
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void setProgress(final StatefulProgressListener progress)
    {
        this.progress = progress;
    }

    public void setEnabled(final boolean enabled)
    {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(@Nonnull final HttpServletRequest request, @Nonnull final HttpServletResponse response, @Nonnull final FilterChain filterChain) throws ServletException, IOException
    {
        final String path = request.getRequestURI();
        final boolean isApi = path.matches("/v[0-9]/.*");
        final boolean isStatusEndpoint = "/status.json".equals(path);
        final boolean isStatusPage = "/status".equals(path);

        if (isApi && enabled)
        {
            response.setStatus(503);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), new ApiError(HttpStatus.SERVICE_UNAVAILABLE, "Service is starting up", Collections.emptyList()));
        }
        else if (isStatusPage)
        {
            response.setStatus(200);
            response.setContentType(MediaType.TEXT_HTML_VALUE);
            ByteStreams.copy(new ClassPathResource("loading.html").getInputStream(), response.getOutputStream());
        }
        else if (isStatusEndpoint)
        {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            writeStatus(response.getOutputStream());
        }
        else
        {
            filterChain.doFilter(request, response);
        }
    }

    private void writeStatus(final OutputStream outputStream) throws IOException
    {
        objectMapper.writeValue(outputStream, progress);
        outputStream.close();
    }
}
