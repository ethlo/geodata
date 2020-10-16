package com.ethlo.geodata;

/*-
 * #%L
 * geodata
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
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

import org.springframework.core.NestedRuntimeException;

public class InvalidIpException extends NestedRuntimeException
{
    private static final long serialVersionUID = -2470273771695446820L;
    
    private String ipAddress;

    public InvalidIpException(String ip, String msg, Throwable cause)
    {
        super(msg, cause);
        this.ipAddress = ip;
    }

    public String getIpAddress()
    {
        return ipAddress;
    }
}