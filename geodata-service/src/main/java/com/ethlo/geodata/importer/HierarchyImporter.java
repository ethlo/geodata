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

import static org.apache.commons.lang3.StringUtils.stripToNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.springframework.util.StringUtils;

public class HierarchyImporter implements DataImporter
{
    private final File hierarchyFile;

    public HierarchyImporter(File hierarchyFile)
    {
        this.hierarchyFile = hierarchyFile;
    }

    @Override
    public long processFile(Consumer<Map<String, String>> sink)
    {
        final AtomicInteger count = new AtomicInteger();
        try (final BufferedReader reader = new BufferedReader(new FileReader(hierarchyFile)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                final String[] entry = StringUtils.delimitedListToStringArray(line, "\t");
                final Map<String, String> paramMap = new TreeMap<>();
                paramMap.put("parent_id", stripToNull(entry[0]));
                paramMap.put("child_id", stripToNull(entry[1]));
                sink.accept(paramMap);
                count.incrementAndGet();
            }
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        return count.get();
    }
}
