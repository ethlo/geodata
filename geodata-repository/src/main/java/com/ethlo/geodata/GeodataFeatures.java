package com.ethlo.geodata;

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

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The optional data sources and lookups, each turned on or off with <code>geodata.features.&lt;name&gt;.enabled</code>.
 * Locations, countries, continents and the hierarchy are always available.
 */
@Component
public class GeodataFeatures
{
    private final Set<Feature> enabled;

    @Autowired
    public GeodataFeatures(@Value("${geodata.features.ip.enabled:true}") final boolean ip,
                           @Value("${geodata.features.boundaries.enabled:true}") final boolean boundaries,
                           @Value("${geodata.features.coordinates.enabled:true}") final boolean coordinates,
                           @Value("${geodata.features.phone.enabled:true}") final boolean phone)
    {
        final Set<Feature> set = EnumSet.noneOf(Feature.class);
        if (ip)
        {
            set.add(Feature.IP);
        }
        if (boundaries)
        {
            set.add(Feature.BOUNDARIES);
        }
        if (coordinates)
        {
            set.add(Feature.COORDINATES);
        }
        if (phone)
        {
            set.add(Feature.PHONE);
        }
        this.enabled = Collections.unmodifiableSet(set);
    }

    private GeodataFeatures(final Set<Feature> enabled)
    {
        this.enabled = enabled.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(enabled));
    }

    public static GeodataFeatures of(final Feature... features)
    {
        return new GeodataFeatures(Set.copyOf(Arrays.asList(features)));
    }

    public static GeodataFeatures all()
    {
        return new GeodataFeatures(EnumSet.allOf(Feature.class));
    }

    public boolean isEnabled(final Feature feature)
    {
        return enabled.contains(feature);
    }

    public Set<Feature> getEnabled()
    {
        return enabled;
    }

    /**
     * Keep only the enabled features whose data was actually imported, according to the import manifest.
     */
    public GeodataFeatures availableIn(final SourceDataInfoSet manifest)
    {
        final Set<Feature> available = EnumSet.noneOf(Feature.class);
        for (final Feature feature : enabled)
        {
            if (feature.getDataType() == null || hasData(manifest, feature.getDataType()))
            {
                available.add(feature);
            }
        }
        return new GeodataFeatures(available);
    }

    private static boolean hasData(final SourceDataInfoSet manifest, final String dataType)
    {
        final SourceDataInfo info = manifest.get(dataType);
        return info != null && info.getCount() > 0;
    }

    @Override
    public String toString()
    {
        return enabled.toString();
    }

    public enum Feature
    {
        /**
         * Location lookup by IP address. Needs the GeoLite2 import.
         */
        IP(DataType.IP),

        /**
         * Boundary geometries and point-in-boundary lookup. Needs the boundaries import.
         */
        BOUNDARIES(DataType.BOUNDARIES),

        /**
         * Lat/lng to location (nearest location, and the containing boundary when boundaries are enabled).
         */
        COORDINATES(null),

        /**
         * Country lookup by phone number.
         */
        PHONE(null);

        private final String dataType;

        Feature(final String dataType)
        {
            this.dataType = dataType;
        }

        public String getDataType()
        {
            return dataType;
        }

        public String getKey()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
