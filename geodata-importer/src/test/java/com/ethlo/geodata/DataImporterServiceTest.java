package com.ethlo.geodata;

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