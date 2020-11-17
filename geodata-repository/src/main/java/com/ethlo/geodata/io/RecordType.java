package com.ethlo.geodata.io;

public enum RecordType
{
    UNCOMPRESSED_PREFIXED_LENGTH(1), LZMA_PREFIXED_LENGTH(2);

    private final int id;

    RecordType(final int id)
    {
        this.id = id;
    }

    public int getId()
    {
        return id;
    }
}