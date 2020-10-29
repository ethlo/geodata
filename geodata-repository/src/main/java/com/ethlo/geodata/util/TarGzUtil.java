package com.ethlo.geodata.util;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public class TarGzUtil
{
    public static void extract(InputStream in, Consumer<Map.Entry<TarArchiveEntry, InputStream>> fileEntry) throws IOException
    {
        try (final GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(in); TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn))
        {
            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tarIn.getNextEntry()) != null)
            {
                if (!entry.isDirectory())
                {
                    fileEntry.accept(new AbstractMap.SimpleEntry<>(entry, tarIn));
                }
            }
        }
    }

    public static void main(String[] args) throws IOException
    {
        TarGzUtil.extract(Files.newInputStream(Paths.get("/home/morten/Downloads/GeoLite2-City_20201020.tar.gz")), (e) ->
        {
            if (e.getKey().getName().endsWith("GeoLite2-City.mmdb"))
            {
                try
                {
                    Files.copy(e.getValue(), Paths.get("/tmp/geodata/test.mmdb"));
                }
                catch (IOException exc)
                {
                    throw new UncheckedIOException(exc);
                }
            }
        });
    }
}
