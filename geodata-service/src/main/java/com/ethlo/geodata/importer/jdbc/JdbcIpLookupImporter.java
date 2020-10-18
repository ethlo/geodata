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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.net.util.SubnetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.importer.DataType;
import com.ethlo.geodata.importer.IpLookupImporter;
import com.ethlo.geodata.util.ResourceUtil;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;

@Component
public class JdbcIpLookupImporter implements PersistentImporter
{
    private static final Logger logger = LoggerFactory.getLogger(JdbcIpLookupImporter.class);
    private static final String insertSql = "INSERT INTO geoip(geoname_id, geoname_country_id, first, last) VALUES (:geoname_id, :geoname_country_id, :first, :last)";

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate txnTemplate;

    @Value("${geodata.geolite2.source}")
    private String url;

    @Override
    public long importData()
    {
        try
        {
            return doUpdate();
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    private long doUpdate() throws IOException
    {
        final Map.Entry<Date, File> ipDataFile = ResourceUtil.fetchResource(DataType.IP, url);

        final AtomicInteger count = new AtomicInteger(0);

        final IpLookupImporter ipLookupImporter = new IpLookupImporter(ipDataFile.getValue());

        final int bufferSize = 20_000;
        final List<Map<String, Object>> buffer = new ArrayList<>(bufferSize);

        ipLookupImporter.processFile(entry ->
        {
            final Optional<Map<String, Object>> paramsOpt = processLine(entry);
            paramsOpt.ifPresent(params ->
            {
                buffer.add(params);

                if (buffer.size() == bufferSize)
                {
                    flush(buffer);
                }

                if (count.get() % 100_000 == 0)
                {
                    logger.info("Processed {}", count.get());
                }

                count.incrementAndGet();
            });
        });

        flush(buffer);
        return count.get();
    }

    private Optional<Map<String, Object>> processLine(final Map<String, String> entry)
    {
        final String strGeoNameId = findMapValue(entry, "geoname_id", "represented_country_geoname_id", "registered_country_geoname_id");
        final String strGeoNameCountryId = findMapValue(entry, "represented_country_geoname_id", "registered_country_geoname_id");
        final Long geonameId = strGeoNameId != null ? Long.parseLong(strGeoNameId) : null;
        if (geonameId != null)
        {
            final Long geonameCountryId = strGeoNameCountryId != null ? Long.parseLong(strGeoNameCountryId) : null;
            final SubnetUtils u = new SubnetUtils(entry.get("network"));
            final long lower = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(u.getInfo().getLowAddress()))).longValue();
            final long upper = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(u.getInfo().getHighAddress()))).longValue();
            final Double lat = parseDouble(entry.get("lat"));
            final Double lng = parseDouble(entry.get("lng"));
            final Map<String, Object> paramMap = new HashMap<>(5);
            paramMap.put("geoname_id", geonameId);
            paramMap.put("geoname_country_id", geonameCountryId);
            paramMap.put("first", lower);
            paramMap.put("last", upper);
            return Optional.of(paramMap);
        }

        return Optional.empty();
    }

    private void flush(final List<Map<String, Object>> buffer)
    {
        Map<String, ?>[] params = buffer.toArray(JdbcGeonamesImporter::newArray);

        txnTemplate.execute((transactionStatus) ->
        {
            jdbcTemplate.batchUpdate(insertSql, params);
            buffer.clear();
            return null;
        });
    }

    private Double parseDouble(String str)
    {
        if (str == null)
        {
            return null;
        }

        if (StringUtils.hasLength(str))
        {
            return Double.parseDouble(str);
        }

        return null;
    }

    private String findMapValue(Map<String, String> map, String... needles)
    {
        return Arrays.stream(needles).map(map::get).filter(StringUtils::hasLength).findFirst().orElse(null);
    }

    @Override
    public void purge()
    {
        jdbcTemplate.update("DELETE FROM geoip", Collections.emptyMap());
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return ResourceUtil.getLastModified(url);
    }
}
