package com.ethlo.geodata.importer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

public class IpLookupImporter implements DataImporter
{
    private final File csvFile;
    
    public IpLookupImporter(File csvFile)
    {
        this.csvFile = csvFile;
    }
    
    @Override
    public void processFile(Consumer<Map<String, String>> sink) throws IOException
    {
        final CsvMapper csvMapper = new CsvMapper();
        final CsvSchema schema = CsvSchema.emptySchema().withHeader(); // use first row as header; otherwise defaults are fine
        try (final BufferedReader reader = new BufferedReader(new FileReader(csvFile)))
        {
            final MappingIterator<Map<String,String>> it = csvMapper.readerFor(Map.class)
               .with(schema)
               .readValues(reader);
            while (it.hasNext())
            {
                sink.accept(it.next());
            }
        }
    }
}
