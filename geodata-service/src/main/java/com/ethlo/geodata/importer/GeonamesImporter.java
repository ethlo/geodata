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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class GeonamesImporter implements DataImporter
{
    private static final Logger logger = LoggerFactory.getLogger(GeonamesImporter.class);

    private final Set<String> exclusions;

    private final boolean onlyHierarchical;

    private final File allCountriesFile;

    private final File hierarchyFile;
    
    private final File alternateNamesFile;

    @Override
    public void processFile(Consumer<Map<String, String>> sink) throws IOException
    {
        final Map<Long, Long> childToParentMap = new HashMap<>();
        final Set<Long> inHierarchy = new TreeSet<>();
        new HierarchyImporter(hierarchyFile).processFile(h->
        {
            childToParentMap.put(Long.parseLong(h.get("child_id")), Long.parseLong(h.get("parent_id")));
            inHierarchy.add(Long.parseLong(h.get("child_id")));
            inHierarchy.add(Long.parseLong(h.get("parent_id")));
        });
        
        // Load alternate names
        final Map<Long, String> preferredNames = loadPreferredNames("EN");
        
        int count = 0;
        try (final BufferedReader reader = new BufferedReader(new FileReader(allCountriesFile)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                final String[] entry = StringUtils.delimitedListToStringArray(line, "\t");

                if (entry.length == 19)
                {
                    final Map<String, String> paramMap = new TreeMap<>();

                    final String lat = stripToNull(entry[4]);
                    final String lng = stripToNull(entry[5]);
                    final String featureCode = stripToNull(entry[7]);

                    final long id = Long.parseLong(stripToNull(entry[0]));
                    final Long parent = childToParentMap.get(id);
                    
                    paramMap.put("id", stripToNull(entry[0]));
                    paramMap.put("parent_id", parent != null ? parent.toString() : null);
                    
                    final String preferredName = preferredNames.get(id);
                    
                    paramMap.put("name", preferredName != null ? preferredName : stripToNull(entry[1]));
                    paramMap.put("lat", lat);
                    paramMap.put("lng", lng);
                    paramMap.put("feature_class", stripToNull(entry[6]));
                    paramMap.put("feature_code", featureCode);
                    paramMap.put("country_code", stripToNull(entry[8]));
                    paramMap.put("population", stripToNull(entry[14]));
                    paramMap.put("elevation_meters", stripToNull(entry[15]));
                    paramMap.put("timezone", stripToNull(entry[17]));
                    paramMap.put("last_modified", null);

                    if (isIncluded(featureCode) && (!onlyHierarchical || inHierarchy.contains(id)))
                    {
                        sink.accept(paramMap);
                    }
                }
                else
                {
                    logger.warn("Cannot process line: {}", line);
                }

                if (count % 100_000 == 0)
                {
                    logger.info("Progress: {}", count);
                }

                count++;
            }
        }
    }

    private Map<Long, String> loadPreferredNames(String preferredLanguage) throws IOException
    {
        final Map<Long, String> preferredNames = new HashMap<>();  
        try (final BufferedReader alternateReader = new BufferedReader(new FileReader(alternateNamesFile)))
        {
        	String line;
            while ((line = alternateReader.readLine()) != null)
            {
                final String[] entry = StringUtils.delimitedListToStringArray(line, "\t");

                if (entry.length == 8)
                {
                	final long geonameId = Long.parseLong(stripToNull(entry[1]));
                	final String languageCode = stripToNull(entry[2]);
                	final String preferredName = stripToNull(entry[3]);
                	final boolean isShort = "1".equals(stripToNull(entry[5]));
                	if (preferredLanguage.equalsIgnoreCase(languageCode) && isShort)
                	{
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
                	final long geonameId = Long.parseLong(stripToNull(entry[1]));
                	final String languageCode = stripToNull(entry[2]);
                	final String preferredName = stripToNull(entry[3]);
                	final boolean isPreferred = "1".equals(stripToNull(entry[4]));
                	if (! preferredNames.containsKey(geonameId) && preferredLanguage.equalsIgnoreCase(languageCode) && isPreferred)
                	{
                		preferredNames.put(geonameId, preferredName);
                	}
                }
            }
        }
        return preferredNames;
	}

	protected boolean isIncluded(String featureCode)
    {
        return exclusions == null || !exclusions.contains(featureCode);
    }

    public static class Builder
    {
        private Set<String> exclusions;
        private boolean onlyHierarchical;
        private File allCountriesFile;
        private File hierarchyFile;
		public File alternateNamesFile;

        public Builder exclusions(Set<String> exclusions)
        {
            this.exclusions = exclusions;
            return this;
        }

        public Builder onlyHierarchical(boolean onlyHierarchical)
        {
            this.onlyHierarchical = onlyHierarchical;
            return this;
        }

        public Builder allCountriesFile(File allCountriesFile)
        {
            this.allCountriesFile = allCountriesFile;
            return this;
        }
        
        public Builder alternateNamesFile(File alternateNamesFile)
        {
            this.alternateNamesFile = alternateNamesFile;
            return this;
        }

        public Builder hierarchyFile(File hierarchyFile)
        {
            this.hierarchyFile = hierarchyFile;
            return this;
        }

        public GeonamesImporter build()
        {
            return new GeonamesImporter(this);
        }
    }

    private GeonamesImporter(Builder builder)
    {
        this.exclusions = builder.exclusions;
        this.onlyHierarchical = builder.onlyHierarchical;
        this.allCountriesFile = builder.allCountriesFile;
        this.hierarchyFile = builder.hierarchyFile;
        this.alternateNamesFile = builder.alternateNamesFile;
    }
}
