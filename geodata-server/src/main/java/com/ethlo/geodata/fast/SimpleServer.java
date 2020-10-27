package com.ethlo.geodata.fast;

/*-
 * #%L
 * geodata-fast-server
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;

public class SimpleServer
{
    private static final Logger logger = LoggerFactory.getLogger(SimpleServer.class);

    private final Undertow.Builder undertowBuilder;

    private SimpleServer(Undertow.Builder undertow)
    {
        this.undertowBuilder = undertow;
    }

    public static SimpleServer simpleServer(HttpHandler handler, String host, int port)
    {
        Undertow.Builder undertow = Undertow.builder()
                //This setting is needed if you want to allow '=' as a value in a cookie.
                //If you base64 encode any cookie values you probably want it on.
                .setServerOption(UndertowOptions.ALLOW_EQUALS_IN_COOKIE_VALUE, true)
                // Needed to set request time in access logs
                .setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, true)
                .addHttpListener(port, host, handler);
        return new SimpleServer(undertow);
    }

    public Undertow start()
    {
        Undertow undertow = undertowBuilder.build();
        undertow.start();
        undertow.getListenerInfo().forEach(listenerInfo -> logger.info(listenerInfo.toString()));
        return undertow;
    }
}
