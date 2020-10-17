package com.ethlo.geodata;

public interface LoadProgressListener
{
    void begin(String name);

    void begin(String name, Integer total);

    void progress(int progress);

    void progress(int progress, final Integer total);

    void end();
}
