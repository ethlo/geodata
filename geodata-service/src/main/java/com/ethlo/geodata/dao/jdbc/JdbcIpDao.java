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
import java.sql.SQLException;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.InvalidIpException;
import com.ethlo.geodata.dao.IpDao;
import com.ethlo.geodata.importer.jdbc.MysqlCursorUtil;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;

@SuppressWarnings("UnstableApiUsage")
@Repository
public class JdbcIpDao implements IpDao
{
    private static final Logger logger = LoggerFactory.getLogger(JdbcIpDao.class);
    private final RangeMap<Long, Long> ipRangeMap = TreeRangeMap.create();
    @Autowired
    private DataSource dataSource;

    @Override
    public Optional<Long> findByIp(final String ip)
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

        /*
        return Optional.ofNullable(jdbcTemplate.query(ipLookupSql, Collections.singletonMap("ip", ipLong), rs ->
        {
            if (rs.next())
            {
                return rs.getLong("geoname_id") != 0 ? rs.getLong("geoname_id") : rs.getLong("geoname_country_id");
            }
            return null;
        }));
        */
    }

    public void load()
    {
        final AtomicInteger count = new AtomicInteger();
        try
        {
            new MysqlCursorUtil(dataSource).query("SELECT geoname_id, geoname_country_id, first, last from geoip", Collections.emptyMap(), rs ->
            {
                while (rs.next())
                {
                    ipRangeMap.put(Range.closed(rs.getLong("first"), rs.getLong("last")), rs.getLong("geoname_id"));
                    count.incrementAndGet();

                    if (count.get() % 100_000 == 0)
                    {
                        logger.info("Loaded IP ranges: {}", count.get());
                    }
                }
            });
        }
        catch (SQLException exc)
        {
            throw new RuntimeException(exc);
        }
    }
}
