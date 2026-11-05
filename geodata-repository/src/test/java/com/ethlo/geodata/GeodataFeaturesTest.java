package com.ethlo.geodata;

/*-
 * #%L
 * geodata-common
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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.ethlo.geodata.GeodataFeatures.Feature;

class GeodataFeaturesTest
{
    @Test
    void enabledFromFlags()
    {
        final GeodataFeatures features = new GeodataFeatures(false, true, true, false);
        assertThat(features.getEnabled()).containsExactlyInAnyOrder(Feature.BOUNDARIES, Feature.COORDINATES);
    }

    @Test
    void manifestDisablesFeaturesWithoutImportedData()
    {
        final SourceDataInfoSet manifest = new SourceDataInfoSet();
        manifest.add(new SourceDataInfo(DataType.LOCATIONS, 100, OffsetDateTime.now()));
        manifest.add(new SourceDataInfo(DataType.BOUNDARIES, 0, OffsetDateTime.now()));

        final GeodataFeatures available = GeodataFeatures.all().availableIn(manifest);

        assertThat(available.getEnabled()).containsExactlyInAnyOrder(Feature.COORDINATES, Feature.PHONE);
    }

    @Test
    void manifestNeverEnablesDisabledFeatures()
    {
        final SourceDataInfoSet manifest = new SourceDataInfoSet();
        manifest.add(new SourceDataInfo(DataType.IP, 10, OffsetDateTime.now()));

        final GeodataFeatures available = GeodataFeatures.of(Feature.PHONE).availableIn(manifest);

        assertThat(available.getEnabled()).containsExactly(Feature.PHONE);
    }
}
