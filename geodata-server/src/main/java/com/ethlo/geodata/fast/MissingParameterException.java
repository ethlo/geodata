package com.ethlo.geodata.fast;

public class MissingParameterException extends RuntimeException
{
    public MissingParameterException(final String parameterName)
    {
        super("Missing input parameter " + parameterName);
    }
}
