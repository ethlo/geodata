package com.ethlo.geodata;

/*-
 * #%L
 * Geodata service
 * %%
 * Copyright (C) 2017 - 2018 Morten Haraldsen (ethlo)
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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class SourceDataInfoSet
{
    private Set<SourceDataInfo> data = new HashSet<>();

    public SourceDataInfo get(String type)
    {
        final Optional<SourceDataInfo> entry = data.stream().filter(e -> e.getType().equals(type)).findFirst();
        return entry.orElse(null);
    }

    public void add(final SourceDataInfo sourceDataInfo)
    {
        data.add(sourceDataInfo);
    }

    public Set<SourceDataInfo> getData()
    {
        return data;
    }

    public SourceDataInfoSet setData(final Set<SourceDataInfo> data)
    {
        this.data = data;
        return this;
    }
}