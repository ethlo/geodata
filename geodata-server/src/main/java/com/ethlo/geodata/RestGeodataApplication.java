package com.ethlo.geodata;

/*-
 * #%L
 * geodata-server
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import com.ethlo.geodata.progress.StatefulProgressListener;

@SpringBootApplication
public class RestGeodataApplication
{
    private static final Logger logger = LoggerFactory.getLogger(RestGeodataApplication.class);

    public static void main(String[] args)
    {
        final ApplicationContext ctx = SpringApplication.run(RestGeodataApplication.class, args);

        dumpMemUsage("Initial");

        if (args.length == 1 && "update".equals(args[0]))
        {
            logger.info("Data refresh requested, this may take some time");
            try
            {
                ctx.getBean(GeoMetaService.class).update();
            }
            catch (IOException exc)
            {
                throw new UncheckedIOException(exc);
            }
        }

        final StatefulProgressListener listener = new StatefulProgressListener();
        listener.begin("init", 1);
        final StartupFilter filter = ctx.getBean(StartupFilter.class);
        filter.setProgress(listener);
        try
        {
            ctx.getBean(GeodataServiceImpl.class).load(listener);
        } finally
        {
            filter.setEnabled(false);
        }


        dumpMemUsage("Completed");
    }

    private static void dumpMemUsage(String description)
    {
        if (!logger.isInfoEnabled())
        {
            return;
        }

        final MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
        final MemoryUsage mem = mbean.getHeapMemoryUsage();
        logger.info("Memory status at stage '{}':\nUsed: {}\nCommitted: {}\nMax: {}",
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
