package com.ethlo.geodata;

public class InvalidDataException extends RuntimeException
{
    private final String input;

    public InvalidDataException(final String message)
    {
        this(null, message);
    }

    public InvalidDataException(final String input, final String message)
    {
        super(message);
        this.input = input;
    }

    public String getInput()
    {
        return input;
    }
}