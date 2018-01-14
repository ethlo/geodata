package com.nurkiewicz.progress;

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

import org.apache.catalina.Container;
import org.apache.catalina.Pipeline;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscription;
import rx.schedulers.Schedulers;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

public class ProgressValve extends ValveBase
{
    private static final Logger log = LoggerFactory.getLogger(ProgressValve.class);

    public ProgressValve()
    {
        super(true);
        ProgressBeanPostProcessor.observe().subscribe(beanName -> log.trace("Bean found: {}", beanName), t -> log.error("Failed", t), this::removeMyself);
    }

    private void removeMyself()
    {
        log.debug("Application started, de-registering");
        final Container container = getContainer();
        if (container != null)
        {
            final Pipeline pipeline = container.getPipeline();
            pipeline.removeValve(this);
        }
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException
    {
        final String uri = request.getRequestURI();

        if ("/init.stream".equals(uri))
        {
            final AsyncContext asyncContext = request.startAsync();
            streamProgress(asyncContext);
        }
        else if ("/".equals(uri))
        {
            sendHtml(response, "/loading.html");
        }
        else if (uri.startsWith("/css"))
        {
            sendHtml(response, "/public/" + uri);
        }
        else
        {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }

    private void streamProgress(AsyncContext asyncContext) throws IOException
    {
        final ServletResponse resp = asyncContext.getResponse();
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.flushBuffer();
        final Subscription subscription = ProgressBeanPostProcessor.observe().map(beanName -> "data: " + beanName).subscribeOn(Schedulers.io())
                        .subscribe(event -> stream(event, resp), e -> log.error("Error in observe()", e), () -> complete(asyncContext));
        unsubscribeOnDisconnect(asyncContext, subscription);
    }

    private void complete(AsyncContext asyncContext)
    {
        stream("event: complete\ndata:", asyncContext.getResponse());
        asyncContext.complete();
    }

    private void unsubscribeOnDisconnect(AsyncContext asyncContext, final Subscription subscription)
    {
        asyncContext.addListener(new AsyncListener()
        {
            @Override
            public void onComplete(AsyncEvent event) throws IOException
            {
                log.debug("complete");
                subscription.unsubscribe();
            }

            @Override
            public void onTimeout(AsyncEvent event) throws IOException
            {
                log.debug("timeout");
                subscription.unsubscribe();
            }

            @Override
            public void onError(AsyncEvent event) throws IOException
            {
                log.debug("error");
                subscription.unsubscribe();
            }

            @Override
            public void onStartAsync(AsyncEvent event) throws IOException
            {
                log.debug("start");
            }
        });
    }

    private void stream(String event, ServletResponse response)
    {
        try
        {
            final PrintWriter writer = response.getWriter();
            writer.println(event);
            writer.println();
            writer.flush();
        }
        catch (Exception e)
        {
            log.debug("Failed to stream", e);
        }
    }

    private void sendHtml(Response response, String name) throws IOException
    {
        try (InputStream loadingHtml = getClass().getResourceAsStream(name))
        {
            IOUtils.copy(loadingHtml, response.getOutputStream());
        }
    }
}
