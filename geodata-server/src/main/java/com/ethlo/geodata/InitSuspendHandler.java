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
