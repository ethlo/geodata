package com.ethlo.geodata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface CacheSerializer<T>
{
    void write(T obj, OutputStream target) throws IOException;

    T read(InputStream source) throws IOException;
}
