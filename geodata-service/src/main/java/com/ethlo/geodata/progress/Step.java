package com.ethlo.geodata.progress;

import java.time.Duration;
import java.time.OffsetDateTime;

public class Step
{
    private final String name;
    private Integer total;
    private int progress;
    private final OffsetDateTime started = OffsetDateTime.now();
    private OffsetDateTime ended;

    public Step(final String name, final Integer total)
    {
        this.name = name;
        this.total = total;
    }

    public String getName()
    {
        return name;
    }

    public int getProgress()
    {
        return progress;
    }

    public Step setProgress(final int progress)
    {
        this.progress = progress;
        return this;
    }

    public Float getProgressPercentage()
    {
        final int progress = getProgress();
        final Integer total = getTotal();
        if (total == null)
        {
            return null;
        }
        return (progress / (float) total) * 100;
    }

    public Integer getTotal()
    {
        return total;
    }

    public void setTotal(int total)
    {
        this.total = total;
    }

    public OffsetDateTime getStart()
    {
        return started;
    }

    public OffsetDateTime getEnd()
    {
        return ended;
    }

    public Duration getElapsed()
    {
        return Duration.between(started, ended != null ? ended : OffsetDateTime.now());
    }

    public void end()
    {
        this.ended = OffsetDateTime.now();
    }
}
