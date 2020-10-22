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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemoryUsageUtil
{
    private static final Logger logger = LoggerFactory.getLogger(MemoryUsageUtil.class);

    public static void dumpMemUsage(String description)
    {
        if (!logger.isInfoEnabled())
        {
            return;
        }

        final MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
        final MemoryUsage mem = mbean.getHeapMemoryUsage();
        logger.info("{}: used={} committed={} max={}",
                description,
                humanReadableByteCount(mem.getUsed(), false),
                humanReadableByteCount(mem.getCommitted(), false),
                humanReadableByteCount(mem.getMax(), false)
        );
    }

    public static String humanReadableByteCount(long bytes, boolean si)
    {
        final int unit = si ? 1000 : 1024;
        if (bytes < unit)
        {
            return bytes + " B";
        }
        final int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
