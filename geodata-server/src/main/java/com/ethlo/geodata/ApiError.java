package com.ethlo.geodata;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ApiError
{
    private final String message;
    private final List<String> details;
    private final Date timestamp;
    private final HttpStatus status;

    public ApiError(HttpStatus status, String message, List<String> details)
    {
        this.status = status;
        this.message = message;
        this.details = details;
        this.timestamp = new Date();
    }

    public ApiError(HttpStatus status, String message, String error)
    {
        this(status, message, Arrays.asList(error));
    }
    
    public ApiError(HttpStatus status, String message)
    {
        this(status, message, Collections.emptyList());
    }

    public int getCode()
    {
        return status.value();
    }
    
    public String getReasonPhrase()
    {
        return status.getReasonPhrase();
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
    public HttpStatus getStatus()
    {
        return status;
    }
}