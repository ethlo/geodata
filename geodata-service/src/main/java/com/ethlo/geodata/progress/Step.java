package com.ethlo.geodata.progress;

public class Step
{
    private final String name;
    private final Integer total;
    private int progress;

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
        return progress / (float) total;
    }

    public Integer getTotal()
    {
        return total;
    }
}
