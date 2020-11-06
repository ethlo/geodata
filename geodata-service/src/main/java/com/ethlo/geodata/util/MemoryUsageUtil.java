package com.ethlo.geodata.util;

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

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class MemoryUsageUtil
{
    private static final Logger logger = LoggerFactory.getLogger(MemoryUsageUtil.class);
    private static final ObjectWriter jsonWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();

    public static OffsetDateTime getJvmStartTime()
    {
        RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        final long startTime = bean.getStartTime();
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneOffset.systemDefault());
    }

    public static void dumpMemUsage(String description)
    {
        if (!logger.isInfoEnabled())
        {
            return;
        }

        try
        {
            logger.info(description + "\n{}", jsonWriter.writeValueAsString(getInfoMap()));
        }
        catch (JsonProcessingException ignored)
        {

        }
    }

    public static String humanReadableByteCount(long bytes)
    {
        final int unit = 1024;
        if (bytes < unit)
        {
            return bytes + " B";
        }
        final int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = ("KMGTPE").charAt(exp - 1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static Map<String, Object> getInfoMap()
    {
        final Map<String, Object> info = new LinkedHashMap<>();

        final MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
        final MemoryUsage mem = mbean.getHeapMemoryUsage();
        final Map<String, Object> heap = new LinkedHashMap<>();
        heap.put("used", humanReadableByteCount(mem.getUsed()));
        heap.put("committed", humanReadableByteCount(mem.getCommitted()));
        heap.put("max", humanReadableByteCount(mem.getMax()));
        info.put("heap", heap);

        final List<Map<String, Object>> bufferPools = new LinkedList<>();
        final List<BufferPoolMXBean> pools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
        for (BufferPoolMXBean pool : pools)
        {
            final Map<String, Object> poolInfo = new LinkedHashMap<>();
            poolInfo.put("name", pool.getName());
            poolInfo.put("count", pool.getCount());
            poolInfo.put("used", humanReadableByteCount(pool.getMemoryUsed()));
            poolInfo.put("capacity", humanReadableByteCount(pool.getTotalCapacity()));
            bufferPools.add(poolInfo);
        }
        info.put("bufferPools", bufferPools);

        return info;
    }
}
