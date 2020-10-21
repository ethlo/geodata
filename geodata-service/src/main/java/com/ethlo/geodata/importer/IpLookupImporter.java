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
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.commons.net.util.SubnetUtils;
import org.springframework.data.util.CloseableIterator;
import org.springframework.util.StringUtils;

import ch.qos.logback.core.util.CloseUtil;
import com.ethlo.geodata.ip.IpData;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.collect.AbstractIterator;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;

@SuppressWarnings("UnstableApiUsage")
public class IpLookupImporter implements DataImporter
{
    private final File csvFile;

    public IpLookupImporter(File csvFile)
    {
        this.csvFile = csvFile;
    }

    @Override
    public long processFile(Consumer<Map<String, String>> sink)
    {
        final AtomicInteger count = new AtomicInteger();
        final CsvMapper csvMapper = new CsvMapper();
        final CsvSchema schema = CsvSchema.emptySchema().withHeader(); // use first row as header; otherwise defaults are fine
        try (final BufferedReader reader = new BufferedReader(new FileReader(csvFile)))
        {
            final MappingIterator<Map<String, String>> it = csvMapper.readerFor(Map.class)
                    .with(schema)
                    .readValues(reader);
            while (it.hasNext())
            {
                sink.accept(it.next());
                count.incrementAndGet();
            }
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        return count.get();
    }

    public Iterator<IpData> iterator() throws IOException
    {
        final CsvMapper csvMapper = new CsvMapper();
        final CsvSchema schema = CsvSchema.emptySchema().withHeader(); // use first row as header; otherwise defaults are fine
        final BufferedReader reader = new BufferedReader(new FileReader(csvFile));
        final MappingIterator<Map<String, String>> it = csvMapper.readerFor(Map.class)
                .with(schema)
                .readValues(reader);
        return wrapClosable(new AbstractIterator<IpData>()
        {
            @Override
            protected IpData computeNext()
            {
                IpData data = null;
                while (it.hasNext() && (data = processLine(it.next()).orElse(null)) == null) ;
                if (data != null)
                {
                    return data;
                }
                return endOfData();
            }
        }, it);
    }

    private <T> CloseableIterator<T> wrapClosable(final Iterator<T> iter, final Closeable closeable)
    {
        return new CloseableIterator<T>()
        {
            @Override
            public void close()
            {
                CloseUtil.closeQuietly(closeable);
            }

            @Override
            public boolean hasNext()
            {
                return iter.hasNext();
            }

            @Override
            public T next()
            {
                return iter.next();
            }
        };
    }

    private Optional<IpData> processLine(final Map<String, String> entry)
    {
        final String strGeoNameId = findMapValue(entry, "geoname_id", "represented_country_geoname_id", "registered_country_geoname_id");
        final String strGeoNameCountryId = findMapValue(entry, "represented_country_geoname_id", "registered_country_geoname_id");
        final Integer geonameCountryId = strGeoNameCountryId != null ? Integer.parseInt(strGeoNameCountryId) : null;
        final Integer geonameId = strGeoNameId != null ? Integer.valueOf(Integer.parseInt(strGeoNameId)) : geonameCountryId;

        if (geonameId != null)
        {
            final SubnetUtils u = new SubnetUtils(entry.get("network"));
            final long lower = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(u.getInfo().getLowAddress()))).longValue();
            final long upper = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(u.getInfo().getHighAddress()))).longValue();
            return Optional.of(new IpData(geonameId, lower, upper));
        }

        return Optional.empty();
    }

    private String findMapValue(Map<String, String> map, String... needles)
    {
        return Arrays.stream(needles).map(map::get).filter(StringUtils::hasLength).findFirst().orElse(null);
    }
}
