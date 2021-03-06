package com.ethlo.geodata.dao;

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

import java.util.Map;

import org.springframework.data.domain.Pageable;

import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.RawLocation;

public interface ReverseGeocodingDao
{
    Map<Integer, Double> findNearest(Coordinates point, int maxDistance, Pageable pageable);

    RawLocation findContaining(Coordinates point, int range);
}
