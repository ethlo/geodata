package com.ethlo.geodata.importer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.GeoConstants;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.progress.StepProgressListener;

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

    public static Map<Integer, Integer> build(Iterator<Map<String, String>> locations, final Map<String, Integer> adminLevels, final Map<String, Country> countries, StepProgressListener progressListener)
    {
        final Map<Integer, Integer> childToParent = new HashMap<>(100_000)
        {
            @Override
            public Integer put(final Integer key, final Integer value)
            {
                if (Objects.equals(key, value))
                {
                    throw new IllegalArgumentException("Key and value are the same: " + key);
                }
                return super.put(key, value);
            }
        };

        while (locations.hasNext())
        {
            final Map<String, String> rs = locations.next();
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
            else if (!StringUtils.isEmpty(countryCode))
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
                logger.info("No parent: {}", id);
            }
        }

        // Link continents to earth
        GeoConstants.CONTINENTS.values().forEach(c -> childToParent.put(c, GeoConstants.EARTH_ID));

        logger.info("Processed hierarchy for a total of {} nodes", childToParent.size());
        return childToParent;
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
        // TODO: FIXME!!!
        return GeoConstants.CONTINENTS.values().iterator().next();
        //return countries.get(countryCode).getContinentCode();
    }

    private static Optional<Integer> getParentId(final long id, final String featureCode, final Map<String, Country> countryToId, final Map<String, Integer> cache, final String countryCode, final String[] adminCodeArray)
    {
        final int index = GeoConstants.ADMINISTRATIVE_LEVEL_FEATURES.indexOf(featureCode);

        if (index == 0)
        {
            // Country level
            return Optional.of(getCountryId(countryToId, countryCode));
        }

        final boolean isAdminLevelLocation = index > -1;

        if (isAdminLevelLocation)
        {
            final String key = getKey(countryCode, adminCodeArray, index - 1);
            return Optional.ofNullable(cache.get(key));
        }
        else
        {
            final int lastNonEmptyIndex = lastOfNotEmpty(adminCodeArray);
            for (int i = lastNonEmptyIndex; i >= 0; i--)
            {
                final String key = getKey(countryCode, adminCodeArray, i);
                final Integer parentId = cache.get(key);
                if (parentId != null)
                {
                    return Optional.of(parentId);
                }
            }
        }

        logger.debug("Inconsistent data! No match for parent of {} - {} - {}", id, featureCode, countryCode);
        return Optional.empty();
    }

    private static int getCountryId(final Map<String, Country> countryToId, final String countryCode)
    {
        final Country country = countryToId.get(countryCode);
        if (country != null)
        {
            return country.getId();
        }
        throw new EmptyResultDataAccessException("Unknown country code: " + countryCode, 1);
    }

    private static String getKey(final String countryCode, final String[] adminCodeArray, final int index)
    {
        final String adminLevelCode = GeoConstants.ADMINISTRATIVE_LEVEL_FEATURES.get(index);
        final String adminCodes = nullAfterOffset(adminCodeArray, index);
        return countryCode + "|" + adminCodes + "|" + adminLevelCode;
    }

    private static int lastOfNotEmpty(final String[] codes)
    {
        for (int i = codes.length - 1; i >= 0; i--)
        {
            if (!"".equals(codes[i]))
            {
                return i;
            }
        }
        return -1;
    }
}
