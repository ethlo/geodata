package com.ethlo.geodata.dao.jdbc;

/*-
 * #%L
 * Geodata service
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

import java.net.InetAddress;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.InvalidIpException;
import com.ethlo.geodata.dao.IpDao;
import com.ethlo.geodata.importer.jdbc.MysqlCursorUtil;
import com.ethlo.geodata.progress.StepProgressListener;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;

@SuppressWarnings("UnstableApiUsage")
@Repository
public class JdbcIpDao implements IpDao
{
    private final RangeMap<Long, Integer> ipRangeMap = TreeRangeMap.create();

    @Autowired
    private DataSource dataSource;

    @Override
    public Optional<Integer> findByIp(final String ip)
    {
        final boolean isValid = InetAddresses.isInetAddress(ip);
        final InetAddress address = InetAddresses.forString(ip);
        final boolean isLocalAddress = address.isLoopbackAddress() || address.isAnyLocalAddress();
        if (!isValid || isLocalAddress)
        {
            return Optional.empty();
        }

        long ipLong;
        try
        {
            ipLong = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(ip))).longValue();
        }
        catch (IllegalArgumentException exc)
        {
            throw new InvalidIpException(ip, exc.getMessage(), exc);
        }

        return Optional.ofNullable(ipRangeMap.get(ipLong));
    }

    public void load(StepProgressListener listener)
    {
        final AtomicInteger count = new AtomicInteger();
        new MysqlCursorUtil(dataSource).query("SELECT geoname_id, geoname_country_id, first, last from geoip", Collections.emptyMap(), rs ->
        {
            while (rs.next())
            {
                ipRangeMap.put(Range.closed(rs.getLong("first"), rs.getLong("last")), rs.getInt("geoname_id"));
                count.incrementAndGet();

                if (count.get() % 10_000 == 0)
                {
                    listener.progress(count.get(), null);
                }
            }
        });
    }
}
