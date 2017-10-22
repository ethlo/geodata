package com.ethlo.geodata;

/*-
 * #%L
 * Geodata service
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import com.ethlo.geodata.model.Coordinates;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

public class RtreeRepositoryTest
{
    @Test
    public void testSimpleBuild() throws ParseException
    {
        final String polyStr = "POLYGON((44.65047 8.79517,44.01055 9.00722,43.72778 9.26278,43.62805 9.35389,43.62722 9.35444,43.58305 9.33611,43.56 9.34611,43.55055 9.35083,43.45528 9.40833,43.44221 9.41667,43.43833 9.41944,43.43083 9.42611,43.42777 9.42972,43.42388 9.43944,43.42333 9.45194,43.42694 9.46972,43.42833 9.4825,43.42722 9.48805,43.38667 9.55694,43.33527 9.61583,43.32166 9.62333,43.30527 9.62833,43.28333 9.63472,43.76773 10.25588,43.90165 10.17244,44.20832 10.50778,44.25278 10.46833,44.27833 10.44778,44.29749 10.43861,44.30805 10.43472,44.38472 10.41194,44.39805 10.41111,44.54722 10.41028,44.56 10.41055,44.57194 10.41194,44.59444 10.41639,44.61444 10.42389,44.63305 10.43194,44.65111 10.44139,44.66111 10.445,44.6825 10.45083,44.71638 10.4575,44.72833 10.45917,44.74027 10.45972,44.75361 10.45861,44.80028 10.45,44.83333 10.44028,44.85333 10.43167,44.88583 10.4225,44.89833 10.42056,44.93555 10.42361,44.95972 10.42667,44.97805 10.42972,44.98861 10.43278,44.99722 10.43722,45.15944 10.53417,45.24638 10.58917,45.25361 10.59639,45.265 10.61305,45.27833 10.62694,45.32388 10.66472,45.33305 10.66889,45.34583 10.66833,45.35694 10.665,45.36722 10.66055,45.37971 10.65833,45.39222 10.65778,45.44167 10.66166,45.45277 10.66389,45.46083 10.66694,45.51277 10.69666,45.62833 10.77417,45.70055 10.82444,45.75139 10.87028,45.75999 10.87528,45.77277 10.87611,45.79472 10.87694,45.80499 10.87306,45.84333 10.84139,45.86138 10.83833,45.86945 10.84416,45.94214 10.11824,44.95914 10.0802,44.95498 9.89399,44.64006 9.86069,44.65047 8.79517))";
        final Geometry poly = new WKTReader().read(polyStr);
        final List<RTreePayload> boundaries = new LinkedList<>();
        boundaries.add(new RTreePayload(123, poly.getArea(), poly.getEnvelopeInternal()));
        final RtreeRepository rtreeRepository = new RtreeRepository(boundaries.iterator());

        assertThat(rtreeRepository.find(Coordinates.from(44, 9))).isNotNull();
        assertThat(rtreeRepository.find(Coordinates.from(46, 9))).isNull();
    }
}
