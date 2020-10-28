package com.ethlo.geodata.fast;

/*-
 * #%L
 * geodata-fast-server
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.dao.EmptyResultDataAccessException;

import com.ethlo.geodata.ApiError;
import com.ethlo.geodata.GeodataServiceImpl;
import com.ethlo.geodata.dao.BoundaryDao;
import com.ethlo.geodata.dao.CountryDao;
import com.ethlo.geodata.dao.FeatureCodeDao;
import com.ethlo.geodata.dao.FileMetaDao;
import com.ethlo.geodata.dao.HierarchyDao;
import com.ethlo.geodata.dao.IpDao;
import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.dao.MetaDao;
import com.ethlo.geodata.dao.TimeZoneDao;
import com.ethlo.geodata.dao.file.FileBoundaryDao;
import com.ethlo.geodata.dao.file.FileCountryDao;
import com.ethlo.geodata.dao.file.FileFeatureCodeDao;
import com.ethlo.geodata.dao.file.FileHierarchyDao;
import com.ethlo.geodata.dao.file.FileIpDao;
import com.ethlo.geodata.dao.file.FileMmapLocationDao;
import com.ethlo.geodata.dao.file.FileTimeZoneDao;
import com.ethlo.geodata.progress.StatefulProgressListener;
import com.ethlo.geodata.util.MemoryUsageUtil;
import io.undertow.server.HttpHandler;

@SpringBootApplication
public class UndertowServer
{
    private static final Logger logger = LoggerFactory.getLogger(UndertowServer.class);

    public UndertowServer()
    {
        final Path basePath = Paths.get("/tmp/geodata");
        final MetaDao metaDao = new FileMetaDao(basePath);
        final LocationDao locationDao = new FileMmapLocationDao(basePath);
        final IpDao ipDao = new FileIpDao(basePath);
        final HierarchyDao hierarchyDao = new FileHierarchyDao(basePath);
        final FeatureCodeDao featureCodeDao = new FileFeatureCodeDao(basePath);
        final TimeZoneDao timeZoneDao = new FileTimeZoneDao(basePath);
        final CountryDao countryDao = new FileCountryDao(basePath);
        final BoundaryDao boundaryDao = new FileBoundaryDao(basePath);
        final int boundaryQualityConstant = 200_000;
        final GeodataServiceImpl geodataService = new GeodataServiceImpl(locationDao, ipDao, hierarchyDao, featureCodeDao, timeZoneDao, countryDao, boundaryDao, Collections.emptyList(), boundaryQualityConstant);
        final StatefulProgressListener progressListener = new StatefulProgressListener();

        geodataService.load(progressListener);

        final Map<Class<? extends Throwable>, Function<Throwable, ApiError>> exceptionHandlers = new LinkedHashMap<>();
        exceptionHandlers.put(EmptyResultDataAccessException.class, exc -> new ApiError(404, exc.getMessage()));
        exceptionHandlers.put(MissingParameterException.class, exc -> new ApiError(400, exc.getMessage()));
        exceptionHandlers.put(Exception.class, exc -> new ApiError(500, "An internal error occurred"));
        final HttpHandler routes = new ServerHandler(geodataService, metaDao).handler(exceptionHandlers);

        final SimpleServer server = SimpleServer.simpleServer(routes, "0.0.0.0", 6565);
        server.start();

        logger.info("Startup completed in {}", DurationFormatUtils.formatDuration(Duration.between(MemoryUsageUtil.getJvmStartTime(), OffsetDateTime.now()).toMillis(), "ss.SSS 'seconds'"));

        logger.info("Triggering GC");
        // Attempt to force GC
        for (int i = 0; i < 3; i++)
        {
            System.gc();
        }

        MemoryUsageUtil.dumpMemUsage("Ready");
    }

    public static void main(String[] args)
    {
        SpringApplication.run(UndertowServer.class, args);
    }
}
