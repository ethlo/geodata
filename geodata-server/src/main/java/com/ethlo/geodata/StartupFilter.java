package com.ethlo.geodata;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ethlo.geodata.progress.StatefulProgressListener;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class StartupFilter extends OncePerRequestFilter
{
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private StatefulProgressListener progress = new StatefulProgressListener();
    private boolean enabled = true;

    public void setProgress(final StatefulProgressListener progress)
    {
        this.progress = progress;
    }

    public void setEnabled(final boolean enabled)
    {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain) throws ServletException, IOException
    {
        if (enabled)
        {
            response.setStatus(503);
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
