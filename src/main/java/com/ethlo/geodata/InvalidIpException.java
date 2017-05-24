package com.ethlo.geodata;

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
