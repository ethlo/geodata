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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.GeoConstants;
import com.ethlo.geodata.model.Country;
import com.google.common.util.concurrent.RateLimiter;

public class HierachyBuilder
{
    private static final Logger logger = LoggerFactory.getLogger(HierachyBuilder.class);

    private static String nullAfterOffset(final String[] strings, int offset)
    {
        final String[] copy = Arrays.copyOf(strings, strings.length);
        for (int i = offset + 1; i < copy.length; i++)
        {
            copy[i] = "";
        }
        return StringUtils.arrayToDelimitedString(copy, "|");
    }

    public static Map<Integer, Integer> build(Iterator<Map<String, String>> locations, final Map<String, Integer> adminLevels, final Map<String, Country> countries)
    {
        final Map<Integer, Integer> childToParent = new HashMap<>(100_000)
        {
            @Override
            public Integer put(final Integer key, final Integer value)
            {
                if (Objects.equals(key, value))
                {
                    logger.error("Integrity error: Location {} points to self", key);
                    return null;
                }
                else
                {
                    return super.put(key, value);
                }
            }
        };

        final RateLimiter rateLimiter = RateLimiter.create(0.2);
        int count = 0;
        while (locations.hasNext())
        {
            handleSingleLocation(locations.next(), adminLevels, countries, childToParent);
            if (rateLimiter.tryAcquire())
            {
                logger.info("Processing locations: {}", count);
            }
            count++;
        }

        // Link continents to earth
        GeoConstants.CONTINENTS.values().forEach(c -> childToParent.put(c, GeoConstants.EARTH_ID));

        logger.info("Processed hierarchy for a total of {} nodes", childToParent.size());
        return childToParent;
    }

    private static void handleSingleLocation(final Map<String, String> rs, final Map<String, Integer> adminLevels, final Map<String, Country> countries, final Map<Integer, Integer> childToParent)
    {
        final int id = Integer.parseInt(rs.get("geonameid"));
        final String featureCode = rs.get("feature_class") + "." + rs.get("feature_code");
        final String countryCode = rs.get("country_code");
        final String[] adminCodeArray = getAdminCodeArray(rs);

        final Integer parentId = getParentId(id, featureCode, countries, adminLevels, countryCode, adminCodeArray).orElse(null);
        if (parentId != null)
        {
            childToParent.put(id, parentId);
        }
        else if (GeoConstants.COUNTRY_LEVEL_FEATURES.contains(featureCode))
        {
            final int continentId = getContinentId(countries, countryCode);
            childToParent.put(id, continentId);
        }
        else if (StringUtils.hasLength(countryCode))
        {
            final int countryId = getCountryId(countries, countryCode);
            if (countryId != id)
            {
                childToParent.put(id, countryId);
            }
            else
            {
                final int continentId = getContinentId(countries, countryCode);
                childToParent.put(id, continentId);
            }
        }
        else
        {
            if (id != GeoConstants.EARTH_ID && GeoConstants.CONTINENTS.values().stream().noneMatch(e -> e == id))
            {
                final String name = rs.get("name");
                logger.info("No parent: {} - {} - {}", id, name, featureCode);
            }
        }
    }

    private static String[] getAdminCodeArray(final Map<String, String> rs)
    {
        final String[] result = new String[4];
        result[0] = rs.get("adm1");
        result[1] = rs.get("adm2");
        result[2] = rs.get("adm3");
        result[3] = rs.get("adm4");
        return result;
    }

    private static Integer getContinentId(final Map<String, Country> countries, final String countryCode)
    {
        final Country country = Optional.ofNullable(countries.get(countryCode.toUpperCase())).orElseThrow();
        return GeoConstants.CONTINENTS.entrySet().stream().filter(e -> e.getKey().equals(country.getContinentCode())).findFirst().map(Map.Entry::getValue).orElseThrow();
    }

    private static Optional<Integer> getParentId(final int id, final String featureCode, final Map<String, Country> countryToId, final Map<String, Integer> cache, final String countryCode, final String[] adminCodeArray)
    {
        final int index = GeoConstants.ADMINISTRATIVE_LEVEL_FEATURES.indexOf(featureCode);

        if (index == 0)
        {
            // Country level, this is ADM1
            return Optional.of(getCountryId(countryToId, countryCode));
        }

        final boolean isAdminLevelLocation = index > -1;
        final Optional<Integer> lastNonEmpty = lastOfNotEmpty(id, adminCodeArray);
        return lastNonEmpty.flatMap(lastNonEmptyIndex ->
        {
            final int startIndex = isAdminLevelLocation ? index - 1 : lastNonEmptyIndex;

            for (int i = startIndex; i >= 0; i--)
            {
                final String key = getKey(countryCode, adminCodeArray, i);
                final Integer parentId = cache.get(key);
                if (parentId != null)
                {
                    return Optional.of(parentId);
                }
            }

            return Optional.empty();
        });
    }

    private static int getCountryId(final Map<String, Country> countryToId, final String countryCode)
    {
        final Country country = countryToId.get(countryCode);
        if (country != null)
        {
            return country.getId();
        }
        throw new IllegalArgumentException("Unknown country code: " + countryCode);
    }

    private static String getKey(final String countryCode, final String[] adminCodeArray, final int adminLevelIndexForParent)
    {
        final String adminLevelCode = GeoConstants.ADMINISTRATIVE_LEVEL_FEATURES.get(adminLevelIndexForParent);
        final String adminCodes = nullAfterOffset(adminCodeArray, adminLevelIndexForParent);
        return countryCode + "|" + adminCodes + "|" + adminLevelCode;
    }

    public static Optional<Integer> lastOfNotEmpty(final int selfId, final String[] codes)
    {
        for (int i = codes.length - 1; i >= 0; i--)
        {
            if (!"".equals(codes[i]) && !("00".equals(codes[i]) && i == 0) && !(Integer.valueOf(selfId).toString().equals(codes[i])))
            {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }
}
