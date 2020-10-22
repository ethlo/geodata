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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.iterators.FilterIterator;
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
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.RawLocation;
import com.ethlo.geodata.util.ResourceUtil;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

@Component
public class FileGeonamesImporter extends BaseCsvFileImporter<RawLocation>
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
    private Map<Integer, String> alternateNames;
    private Map<String, Country> countries;

    public FileGeonamesImporter(@Value("${geodata.geonames.source.alternatenames}") final String geoNamesAlternateNamesUrl,
                                @Value("${geodata.geonames.source.country}") final String geoNamesCountryInfoUrl,
                                @Value("${geodata.geonames.source.names}") final String geoNamesAllCountriesUrl,
                                @Value("${geodata.geonames.features.included}") final String inclusionsCsv,
                                @Value("${geodata.base-path}") final Path basePath,
                                final FeatureCodeDao featureCodeDao,
                                final TimeZoneDao timeZoneDao,
                                final HierarchyDao hierarchyDao,
                                final CountryDao countryDao)
    {
        super(basePath, DataType.LOCATIONS, geoNamesAllCountriesUrl, HEADERS, true, 0);

        this.geoNamesAlternateNamesUrl = geoNamesAlternateNamesUrl;
        this.geoNamesCountryInfoUrl = geoNamesCountryInfoUrl;
        this.inclusions = StringUtils.commaDelimitedListToSet(inclusionsCsv);
        this.featureCodeDao = featureCodeDao;
        this.timeZoneDao = timeZoneDao;
        this.hierarchyDao = hierarchyDao;
        this.countryDao = countryDao;

        logger.info("Included features: {}", StringUtils.collectionToCommaDelimitedString(inclusions));
    }

    @Override
    protected void prepare() throws IOException
    {
        // Load countries
        logger.info("Loading countries");
        final Map.Entry<Date, File> countriesFile = ResourceUtil.fetchResource("countries", geoNamesCountryInfoUrl);
        final List<String> countryColumns = Arrays.asList("iso", "iso3", "iso_numeric", "fips", "country", "capital", "area", "population", "continent", "tld", "currency_Code", "currency_name", "phone", "postal_code_format", "postal_code_regex", "languages", "geonameid");
        this.countries = CountryParser.build(rawIterator(countriesFile.getValue().toPath(), countryColumns));

        // Load proper names for language
        logger.info("Loading alternate names");
        final Map.Entry<Date, File> alternateFile = ResourceUtil.fetchResource("alternate_names", geoNamesAlternateNamesUrl);
        this.alternateNames = AlternateNamesFileReader.loadPreferredNames(alternateFile.getValue(), "EN");
    }

    @Override
    protected RawLocation processLine(final Map<String, String> line)
    {
        final String featureClass = line.get("feature_class");
        final String featureCode = line.get("feature_code");
        final String featureKey = featureClass + "." + featureCode;
        if (!inclusions.contains(featureKey))
        {
            return null;
        }

        final String timezone = line.get("timezone");
        timezones.computeIfAbsent(timezone, tz -> timezones.size() + 1);
        featureCodes.computeIfAbsent(featureClass + "." + featureCode, combined -> featureCodes.size() + 1);

        final int id = Integer.parseInt(line.get("geonameid"));
        final String name = line.get("name");
        final String adm1 = line.get("adm1");
        final String adm2 = line.get("adm2");
        final String adm3 = line.get("adm3");
        final String adm4 = line.get("adm4");
        final String countryCode = line.get("country_code");
        final double lat = Double.parseDouble(line.get("lat"));
        final double lng = Double.parseDouble(line.get("lng"));
        final long population = Long.parseLong(line.get("population"));

        if (adm1 != null || adm2 != null || adm3 != null || adm4 != null)
        {
            final String key = countryCode + "|" + adm1 + "|" + adm2 + "|" + adm3 + "|" + adm4 + "|" + featureKey;
            adminLevels.put(key, id);
        }

        final String preferredName = alternateNames.get(id);
        final String actualName = preferredName != null ? preferredName : name;
        return new RawLocation(id, actualName, countryCode, Coordinates.from(lat, lng), featureCodes.get(featureKey), population, timezones.get(timezone));
    }

    @Override
    protected void processing()
    {
        logger.info("Writing countries");
        countryDao.save(countries);

        logger.info("Writing timezones");
        timeZoneDao.save(timezones);

        logger.info("Writing feature codes");
        featureCodeDao.save(featureCodes);

        logger.info("Build hierarchy");
        try (final CloseableIterator<Map<String, String>> iterator = rawIterator(getSourcePath(), HEADERS))
        {
            final Iterator<Map<String, String>> filtered = new FilterIterator<>(iterator, e -> inclusions.contains(e.get("feature_class") + "." + e.get("feature_code")));
            final Map<Integer, Integer> childToParent = HierachyBuilder.build(filtered, adminLevels, countries);
            logger.info("child to parent nodes: {}", childToParent.size());
            hierarchyDao.save(childToParent);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }
}
