package com.ethlo.geodata.progress;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ethlo.geodata.LoadProgressListener;

public class StatefulProgressListener implements LoadProgressListener
{
    private final Map<String, Step> steps = new LinkedHashMap<>();
    private String last;

    @Override
    public void begin(final String name)
    {
        this.begin(name, null);
    }

    @Override
    public void begin(final String name, final Integer total)
    {
        if (last != null)
        {
            end();
        }
        steps.put(name, new Step(name, total));
        last = name;
    }

    @Override
    public void progress(final int progress)
    {
        final Step step = steps.get(last);
        step.setProgress(progress);
    }

    @Override
    public void progress(final int progress, final Integer total)
    {
        final Step step = steps.get(last);
        step.setProgress(progress);
        step.setProgress(total);
    }

    @Override
    public void end()
    {
        final Step step = steps.get(last);
        step.setProgress(step.getTotal());
        last = null;
    }

    public Map<String, Step> getSteps()
    {
        return steps;
    }
}
