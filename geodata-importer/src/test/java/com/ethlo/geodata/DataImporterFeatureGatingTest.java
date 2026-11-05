package com.ethlo.geodata;

/*-
 * #%L
 * geodata-importer
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ethlo.geodata.GeodataFeatures.Feature;
import com.ethlo.geodata.importer.DataImporterService;
import com.ethlo.geodata.importer.FileGeonamesImporter;
import com.ethlo.geodata.importer.FileIpDataImporter;

class DataImporterFeatureGatingTest
{
    @TempDir
    Path basePath;

    @Test
    void disabledSourcesAreNotImported() throws IOException
    {
        final FileIpDataImporter ipImporter = mock(FileIpDataImporter.class);
        final FileGeonamesImporter geonamesImporter = mock(FileGeonamesImporter.class);
        when(geonamesImporter.lastRemoteModified()).thenReturn(OffsetDateTime.now());
        when(geonamesImporter.importData()).thenReturn(42);

        final DataImporterService service = new DataImporterService(basePath, basePath.resolve("input"), Duration.ofDays(7), ipImporter, geonamesImporter, GeodataFeatures.of(Feature.COORDINATES));
        service.update();

        verifyNoInteractions(ipImporter);
        verify(geonamesImporter).importData();
        assertThat(service.getSourceDataInfo().get(DataType.LOCATIONS).getCount()).isEqualTo(42);
        assertThat(service.getSourceDataInfo().get(DataType.IP)).isNull();
        assertThat(service.getSourceDataInfo().get(DataType.BOUNDARIES)).isNull();
    }
}
