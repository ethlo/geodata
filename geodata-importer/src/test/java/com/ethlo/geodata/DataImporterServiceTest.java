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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;

import org.junit.Test;

import com.ethlo.geodata.importer.DataImporterService;

public class DataImporterServiceTest
{
    private final DataImporterService dataImporterService = new DataImporterService(Files.createDirectory(Path.of("test")), Duration.ofDays(7), null, null);

    public DataImporterServiceTest() throws IOException
    {
    }

    @Test
    public void metadataTest()
    {
        final Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2010);
        cal.set(Calendar.MONTH, 10);
        cal.set(Calendar.DAY_OF_MONTH, 31);
        cal.set(Calendar.HOUR_OF_DAY, 14);
        cal.set(Calendar.MINUTE, 56);
        cal.set(Calendar.SECOND, 45);
        cal.set(Calendar.MILLISECOND, 0);
        final Date expected = cal.getTime();
        dataImporterService.setStatus("IP", expected, 2244);
        //assertThat(dataImporterService.getLastModified(DataType.IP).get().getTime()).isEqualTo(expected.getTime());
        //assertThat(geoMetaService.getSourceDataInfo().get(DataType.IP).getCount()).isEqualTo(2244);
    }
}
