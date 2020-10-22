package com.ethlo.geodata.importer.jdbc;

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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.net.util.SubnetUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ethlo.geodata.importer.BaseFileImporter;
import com.ethlo.geodata.importer.GeonamesSource;
import com.ethlo.geodata.ip.IpData;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;

@SuppressWarnings("UnstableApiUsage")
@Component
public class FileIpDataImporter extends BaseFileImporter<IpData>
{
    public FileIpDataImporter(@Value("${geodata.base-path}") Path basePath, @Value("${geodata.geolite2.source.csv}") final String csvUrl)
    {
        super(basePath, GeonamesSource.IP, csvUrl,
                Arrays.asList("network","geoname_id","registered_country_geoname_id",
                        "represented_country_geoname_id","is_anonymous_proxy","is_satellite_provider",
                        "postal_code","latitude","longitude","accuracy_radius"), false, 1);
    }

    @Override
    protected IpData processLine(final Map<String, String> entry)
    {
        final String strGeoNameId = findMapValue(entry, "geoname_id", "represented_country_geoname_id", "registered_country_geoname_id");
        final String strGeoNameCountryId = findMapValue(entry, "represented_country_geoname_id", "registered_country_geoname_id");
        final Integer countryId = strGeoNameCountryId != null ? Integer.parseInt(strGeoNameCountryId) : null;
        final Integer locationId = strGeoNameId != null ? Integer.valueOf(Integer.parseInt(strGeoNameId)) : countryId;
        if (locationId != null)
        {
            final SubnetUtils u = new SubnetUtils(entry.get("network"));
            final long lower = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(u.getInfo().getLowAddress()))).longValue();
            final long upper = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(u.getInfo().getHighAddress()))).longValue();
            return new IpData(locationId, lower, upper);
        }
        return null;
    }
}
