package com.ethlo.geodata;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class InitSuspendHandler implements HttpHandler
{
    private final HttpHandler delegate;
    private boolean ready = false;

    public InitSuspendHandler(final HttpHandler delegate)
    {
        this.delegate = delegate;
    }

    public InitSuspendHandler setReady(final boolean ready)
    {
        this.ready = ready;
        return this;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception
    {
        if (ready)
        {
            delegate.handleRequest(exchange);
        }
        else
        {
            BaseServerHandler.json(exchange, new ApiError(503, "Server is initializing, please wait..."));
        }
    }
}
