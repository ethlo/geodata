package com.ethlo.geodata;

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

import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ApiError
{
    private final String message;
    private final List<String> details;
    private final Date timestamp;
    private final int status;

    public ApiError(int status, String message, List<String> details)
    {
        this.status = status;
        this.message = message;
        this.details = details;
        this.timestamp = new Date();
    }

    public ApiError(int status, String message, String error)
    {
        this(status, message, Collections.singletonList(error));
    }

    public ApiError(int status, String message)
    {
        this(status, message, (List<String>) null);
    }

    public String getMessage()
    {
        return message;
    }

    public List<String> getDetails()
    {
        return details;
    }

    public Date getTimestamp()
    {
        return timestamp;
    }

    @JsonIgnore
    public int getStatus()
    {
        return status;
    }

}
