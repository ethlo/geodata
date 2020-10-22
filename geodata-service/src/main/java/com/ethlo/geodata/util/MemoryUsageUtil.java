package com.ethlo.geodata.util;

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
