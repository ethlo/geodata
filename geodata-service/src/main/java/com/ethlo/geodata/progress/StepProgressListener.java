package com.ethlo.geodata.progress;

@FunctionalInterface
public interface StepProgressListener
{
    void progress(int progress, Integer total);
}
