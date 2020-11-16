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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.Test;

import com.ethlo.geodata.importer.DataImporterService;

public class DataImporterServiceTest
{
    final Path basePath = Path.of("test");
    final Path inputPath = basePath.resolve("input");
    private final DataImporterService dataImporterService = new DataImporterService(Files.createDirectory(basePath), inputPath, Duration.ofDays(7), null, null);

    public DataImporterServiceTest() throws IOException
    {
    }

    @Test
    public void metadataTest()
    {
        final OffsetDateTime expected = OffsetDateTime.parse("2010-12-24T22:46:11Z");
        dataImporterService.setStatus("IP", expected, 2244);
        assertThat(dataImporterService.getLastModified(DataType.IP).get()).isAtSameInstantAs(expected);
    }
}
