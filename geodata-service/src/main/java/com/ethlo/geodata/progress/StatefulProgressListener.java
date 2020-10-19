package com.ethlo.geodata.progress;

/*-
 * #%L
 * Geodata service
 * %%
 * Copyright (C) 2017 - 2020 Morten Haraldsen (ethlo)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

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
        if (total != null)
        {
            step.setTotal(total);
        }
    }

    @Override
    public void end()
    {
        final Step step = steps.get(last);
        if (step != null)
        {
            if (step.getTotal() != null)
            {
                step.setProgress(step.getTotal());
            }
            step.end();
        }
        last = null;
    }

    public Map<String, Step> getSteps()
    {
        return steps;
    }
}
