package com.ethlo.geodata.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface DataSerializer<T>
{
    void write(T obj, OutputStream target) throws IOException;

    T read(InputStream source) throws IOException;
}
