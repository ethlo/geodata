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

import java.time.Duration;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import com.ethlo.geodata.progress.StatefulProgressListener;
import com.ethlo.geodata.util.MemoryUsageUtil;

@SpringBootApplication
public class RestGeodataApplication
{
    private static final Logger logger = LoggerFactory.getLogger(RestGeodataApplication.class);

    public static void main(String[] args)
    {
        final ApplicationContext ctx = SpringApplication.run(RestGeodataApplication.class, args);

        MemoryUsageUtil.dumpMemUsage("Initial");

        final StatefulProgressListener listener = new StatefulProgressListener();
        listener.begin("init", 1);
        final StartupFilter filter = ctx.getBean(StartupFilter.class);
        filter.setProgress(listener);
        try
        {
            ctx.getBean(GeodataServiceImpl.class).load(listener);
        }
        catch (Exception exc)
        {
            logger.error("Load failed", exc);
            System.exit(1);
        } finally
        {
            filter.setEnabled(false);
        }

        logger.info("Startup completed in {}", Duration.between(MemoryUsageUtil.getJvmStartTime(), OffsetDateTime.now()));

        logger.info("Triggering GC");
        // Attempt to force GC
        for (int i = 0; i < 3; i++)
        {
            System.gc();
        }

        MemoryUsageUtil.dumpMemUsage("Ready");
    }
}
