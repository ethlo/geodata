package com.nurkiewicz.progress;

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
        switch (uri)
        {
            case "/init.stream":
                final AsyncContext asyncContext = request.startAsync();
                streamProgress(asyncContext);
                break;
            case "/health":
            case "/info":
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                break;
                
            case "/img/bg.jpg":
                sendImage(response, "bg.jpg");
                break;
                
            default:
                sendHtml(response, "loading.html");
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
                log.info("complete");
                subscription.unsubscribe();
            }

            @Override
            public void onTimeout(AsyncEvent event) throws IOException
            {
                log.info("timeout");
                subscription.unsubscribe();
            }

            @Override
            public void onError(AsyncEvent event) throws IOException
            {
                log.info("error");
                subscription.unsubscribe();
            }

            @Override
            public void onStartAsync(AsyncEvent event) throws IOException
            {
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
        catch (IOException e)
        {
            log.warn("Failed to stream", e);
        }
    }

    private void sendHtml(Response response, String name) throws IOException
    {
        try (InputStream loadingHtml = getClass().getResourceAsStream("/" + name))
        {
            IOUtils.copy(loadingHtml, response.getOutputStream());
        }
    }
    
    private void sendImage(Response response, String name) throws IOException
    {
        try (InputStream loadingHtml = getClass().getResourceAsStream("/img/" + name))
        {
            IOUtils.copy(loadingHtml, response.getOutputStream());
        }
    }

}