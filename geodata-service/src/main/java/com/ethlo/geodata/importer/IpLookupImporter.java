package com.ethlo.geodata.importer;

/*-
 * #%L
 * geodata
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

import com.ethlo.geodata.IoUtils;
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
    public long processFile(Consumer<Map<String, String>> sink) throws IOException
    {
        final CsvMapper csvMapper = new CsvMapper();
        final CsvSchema schema = CsvSchema.emptySchema().withHeader(); // use first row as header; otherwise defaults are fine
        int count = 0;
        try (final BufferedReader reader = IoUtils.getBufferedReader(csvFile))
        {
            final MappingIterator<Map<String,String>> it = csvMapper.readerFor(Map.class)
               .with(schema)
               .readValues(reader);
            while (it.hasNext())
            {
                sink.accept(it.next());
                count++;
            }
        }
        return count;
    }
}
