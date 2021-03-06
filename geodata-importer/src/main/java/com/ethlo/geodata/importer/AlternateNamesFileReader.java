package com.ethlo.geodata.importer;

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

import static org.apache.commons.lang3.StringUtils.stripToNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.util.StringUtils;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class AlternateNamesFileReader
{
    public static Map<Integer, String> loadPreferredNames(Path alternateNamesFile, String preferredLanguage) throws IOException
    {
        final Map<Integer, String> preferredNames = new Int2ObjectOpenHashMap<>(1_000_000);
        try (final BufferedReader alternateReader = Files.newBufferedReader(alternateNamesFile))
        {
            String line;
            while ((line = alternateReader.readLine()) != null)
            {
                final String[] entry = StringUtils.delimitedListToStringArray(line, "\t");

                if (entry.length == 8)
                {
                    final String languageCode = stripToNull(entry[2]);
                    final String preferredName = stripToNull(entry[3]);
                    final boolean isShort = "1".equals(stripToNull(entry[5]));
                    if (preferredLanguage.equalsIgnoreCase(languageCode) && isShort)
                    {
                        final int geonameId = Integer.parseInt(stripToNull(entry[1]));
                        preferredNames.put(geonameId, preferredName);
                    }
                }
            }
        }

        // Run through again, but use the "preferred" if not "short" name was found in the previous round
        try (final BufferedReader alternateReader = Files.newBufferedReader(alternateNamesFile))
        {
            String line;
            while ((line = alternateReader.readLine()) != null)
            {
                final String[] entry = StringUtils.delimitedListToStringArray(line, "\t");

                if (entry.length == 8)
                {
                    final int geonameId = Integer.parseInt(stripToNull(entry[1]));
                    final String languageCode = stripToNull(entry[2]);
                    final String preferredName = stripToNull(entry[3]);
                    final boolean isPreferred = "1".equals(stripToNull(entry[4]));
                    if (!preferredNames.containsKey(geonameId) && preferredLanguage.equalsIgnoreCase(languageCode) && isPreferred)
                    {
                        preferredNames.put(geonameId, preferredName);
                    }
                }
            }
        }
        return preferredNames;
    }
}
