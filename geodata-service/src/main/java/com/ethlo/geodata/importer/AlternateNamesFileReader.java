package com.ethlo.geodata.importer;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import org.springframework.util.StringUtils;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class AlternateNamesFileReader
{
    public static Map<Integer, String> loadPreferredNames(File alternateNamesFile, String preferredLanguage) throws IOException
    {
        final Map<Integer, String> preferredNames = new Int2ObjectOpenHashMap<>(1_000_000);
        try (final BufferedReader alternateReader = new BufferedReader(new FileReader(alternateNamesFile)))
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
        try (final BufferedReader alternateReader = new BufferedReader(new FileReader(alternateNamesFile)))
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
