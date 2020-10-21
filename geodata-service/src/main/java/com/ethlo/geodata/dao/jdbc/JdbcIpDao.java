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

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Repository;

import com.ethlo.geodata.InvalidIpException;
import com.ethlo.geodata.dao.IpDao;
import com.ethlo.geodata.importer.GeonamesSource;
import com.ethlo.geodata.ip.IpData;
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
    private final Path basePath = Paths.get("/tmp");
    private final RangeMap<Long, Integer> ipRangeMap = TreeRangeMap.create();

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
        try
        {
            doLoad(listener);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private void doLoad(StepProgressListener listener) throws IOException
    {
        final AtomicInteger count = new AtomicInteger();
        final Path filePath = basePath.resolve(GeonamesSource.IP.name().toLowerCase() + ".data");
        try (final ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(filePath))))
        {
            Object obj;
            try
            {
                while ((obj = in.readObject()) != null)
                {
                    final IpData data = (IpData) obj;
                    ipRangeMap.put(Range.closed(data.getLower(), data.getUpper()), data.getGeonameId());
                    listener.progress(count.incrementAndGet(), null);
                }
            }
            catch (EOFException ignored)
            {

            }
            catch (ClassNotFoundException exc)
            {
                throw new RuntimeException(exc);
            }
        }
    }
}
