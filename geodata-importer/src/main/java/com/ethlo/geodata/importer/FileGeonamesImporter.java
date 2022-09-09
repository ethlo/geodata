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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.DataType;
import com.ethlo.geodata.dao.CountryDao;
import com.ethlo.geodata.dao.FeatureCodeDao;
import com.ethlo.geodata.dao.HierarchyDao;
import com.ethlo.geodata.dao.TimeZoneDao;
import com.ethlo.geodata.dao.file.FileIpDao;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.RawLocation;
import com.ethlo.geodata.util.ResourceUtil;
import com.google.common.collect.Iterators;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

@Component
public class FileGeonamesImporter implements DataImporter
{
    public static final List<String> HEADERS = Arrays.asList("geonameid", "name", "asciiname", "alternate_names", "lat", "lng", "feature_class",
            "feature_code", "country_code", "cc2", "adm1", "adm2", "adm3", "adm4",
            "population", "elevation", "dem", "timezone", "modified"
    );

    private static final Logger logger = LoggerFactory.getLogger(FileGeonamesImporter.class);
    private final Set<String> inclusions;
    private final Map<String, Integer> timezones = new HashMap<>();
    private final Map<String, Integer> featureCodes = new HashMap<>();
    private final Map<String, Integer> adminLevels = new Object2IntOpenHashMap<>();
    private final String geoNamesAlternateNamesUrl;
    private final String geoNamesCountryInfoUrl;
    private final FeatureCodeDao featureCodeDao;
    private final TimeZoneDao timeZoneDao;
    private final HierarchyDao hierarchyDao;
    private final CountryDao countryDao;

    private final String url;
    private final BinaryIndexedFileWriter<RawLocation> locationWriter;
    private final Duration maxAge;
    private final ResourceUtil resourceUtil;
    private final Path basePath;

    private Map<Integer, String> alternateNames;
    private Map<String, Country> countries;
    private Set<Integer> geoIpIds;

    public FileGeonamesImporter(@Value("${geodata.geonames.source.alternatenames}") final String geoNamesAlternateNamesUrl,
                                @Value("${geodata.geonames.source.country}") final String geoNamesCountryInfoUrl,
                                @Value("${geodata.geonames.source.names}") final String geoNamesAllCountriesUrl,
                                @Value("${geodata.geonames.features.included}") final String inclusionsCsv,
                                @Value("${geodata.base-path}") final Path basePath,
                                @Value("${geodata.max-data-age}") final Duration maxAge,
                                final FeatureCodeDao featureCodeDao,
                                final TimeZoneDao timeZoneDao,
                                final HierarchyDao hierarchyDao,
                                final CountryDao countryDao,
                                final ResourceUtil resourceUtil)
    {
        this.locationWriter = new BinaryIndexedFileWriter<>(basePath, "locations", false)
        {
            @Override
            protected void write(final RawLocation data, final DataOutputStream out) throws IOException
            {
                data.write(out);
            }
        };

        this.basePath = basePath;
        this.url = geoNamesAllCountriesUrl;
        this.maxAge = maxAge;
        this.geoNamesAlternateNamesUrl = geoNamesAlternateNamesUrl;
        this.geoNamesCountryInfoUrl = geoNamesCountryInfoUrl;
        this.inclusions = StringUtils.commaDelimitedListToSet(inclusionsCsv);
        this.featureCodeDao = featureCodeDao;
        this.timeZoneDao = timeZoneDao;
        this.hierarchyDao = hierarchyDao;
        this.countryDao = countryDao;
        this.resourceUtil = resourceUtil;
        logger.info("Included features: {}", StringUtils.collectionToCommaDelimitedString(inclusions));
    }

    @Override
    public void purgeData()
    {
        //throw new UnsupportedOperationException()
    }

    @Override
    public int importData()
    {
        try
        {
            prepare();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }

        Map.Entry<OffsetDateTime, Path> fileData;
        try
        {
            fileData = resourceUtil.fetchResource(DataType.LOCATIONS, maxAge, url);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException("Unable to download resource: " + url, exc);
        }

        try (final CloseableIterator<RawLocation> iter = new CsvFileIterator<>(fileData.getValue(), HEADERS, true, 0, this::processLine))
        {
            final int result = locationWriter.writeData(iter);

            logger.info("Writing countries");
            countryDao.save(countries.values());

            logger.info("Writing timezones");
            timeZoneDao.save(timezones);

            logger.info("Writing feature codes");
            featureCodeDao.save(featureCodes);

            logger.info("Build hierarchy");
            try (final CloseableIterator<Map<String, String>> iterator = new CsvFileIterator<>(fileData.getValue(), HEADERS, true, 0, i -> i))
            {
                final Iterator<Map<String, String>> filtered = Iterators.filter(iterator, e ->
                {
                    final String featureKey = e.get("feature_class") + "." + e.get("feature_code");
                    final int id = Integer.parseInt(e.get("geonameid"));
                    return inclusions.contains(featureKey) || geoIpIds.contains(id);
                });
                final Map<Integer, Integer> childToParent = HierachyBuilder.build(filtered, adminLevels, countries);
                logger.info("Hierarchy nodes: {}", childToParent.size());
                hierarchyDao.save(childToParent);
            }

            return result;
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    @Override
    public OffsetDateTime lastRemoteModified() throws IOException
    {
        return resourceUtil.getLastModified(url);
    }

    protected void prepare() throws IOException
    {
        // Load countries
        logger.info("Loading countries");
        final Map.Entry<OffsetDateTime, Path> countriesFile = resourceUtil.fetchResource("countries", maxAge, geoNamesCountryInfoUrl);
        final List<String> countryColumns = Arrays.asList("iso", "iso3", "iso_numeric", "fips", "country", "capital", "area", "population", "continent", "tld", "currency_Code", "currency_name", "phone", "postal_code_format", "postal_code_regex", "languages", "geonameid");
        countries = new HashMap<>();
        try (final CloseableIterator<Country> iter = new CsvFileIterator<>(countriesFile.getValue(), countryColumns, true, 0, c ->
        {
            final int id = Integer.parseInt(c.get("geonameid"));
            final String countryCode = c.get("iso");
            final String name = c.get("country");
            final String phone = c.get("phone");
            final String continentCode = c.get("continent");
            final List<String> languages = new ArrayList<>(StringUtils.commaDelimitedListToSet(c.get("languages")));
            return new Country(id, name, countryCode, continentCode, languages, phone);
        }))
        {
            iter.forEachRemaining(c -> countries.put(c.getCountryCode(), c));
        }

        // Load proper names for language
        logger.info("Loading alternate names");
        final Map.Entry<OffsetDateTime, Path> alternateFile = resourceUtil.fetchResource("alternate_names", maxAge, geoNamesAlternateNamesUrl);
        this.alternateNames = AlternateNamesFileReader.loadPreferredNames(alternateFile.getValue(), "EN");

        logger.info("Create list of location ids used by ip-lookup database");
        this.geoIpIds = new HashSet<>();
        new FileIpDao(basePath).iterate(id -> geoIpIds.add(id));
    }

    private RawLocation processLine(final Map<String, String> line)
    {
        final int id = Integer.parseInt(line.get("geonameid"));
        final String featureClass = line.get("feature_class");
        final String featureCode = line.get("feature_code");
        final String featureKey = featureClass + "." + featureCode;
        if (!inclusions.contains(featureKey) && !geoIpIds.contains(id))
        {
            return null;
        }

        final String timezone = line.get("timezone");
        timezones.computeIfAbsent(timezone, tz -> timezones.size() + 1);
        featureCodes.computeIfAbsent(featureClass + "." + featureCode, combined -> featureCodes.size() + 1);

        final String name = line.get("name");
        final String adm1 = line.get("adm1");
        final String adm2 = line.get("adm2");
        final String adm3 = line.get("adm3");
        final String adm4 = line.get("adm4");
        final String countryCode = line.get("country_code");
        final double lat = Double.parseDouble(line.get("lat"));
        final double lng = Double.parseDouble(line.get("lng"));
        final long population = Long.parseLong(line.get("population"));
        final String strElevation = line.get("elevation");
        final int elevation = !StringUtils.hasLength(strElevation) ? Integer.parseInt(strElevation) : Integer.MIN_VALUE;

        if (adm1 != null || adm2 != null || adm3 != null || adm4 != null)
        {
            final String key = countryCode + "|" + adm1 + "|" + adm2 + "|" + adm3 + "|" + adm4 + "|" + featureKey;
            adminLevels.put(key, id);
        }

        final String preferredName = alternateNames.get(id);
        final String actualName = preferredName != null ? preferredName : name;
        return new RawLocation(id, actualName, countryCode, Coordinates.from(lat, lng), featureCodes.get(featureKey), population, timezones.get(timezone), elevation);
    }
}
