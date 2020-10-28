package com.ethlo.geodata.importer;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.DataType;
import com.ethlo.geodata.SourceDataInfo;
import com.ethlo.geodata.SourceDataInfoSet;
import com.ethlo.geodata.dao.FileMetaDao;
import com.ethlo.geodata.util.JsonUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class DataImporterService
{
    private static final Logger logger = LoggerFactory.getLogger(DataImporterService.class);
    private final GeoFabrikBoundaryLoader geoFabrikBoundaryLoader = new GeoFabrikBoundaryLoader();
    private final Duration maxDataAge;
    private final FileIpDataImporter ipLookupImporter;
    private final FileGeonamesImporter geonamesImporter;
    private final Path basePath;

    public DataImporterService(@Value("${geodata.base-path}") final Path basePath,
                               @Value("${geodata.max-data-age}") final Duration maxDataAge,
                               FileIpDataImporter ipLookupImporter,
                               FileGeonamesImporter geonamesImporter)
    {
        this.basePath = basePath;
        this.maxDataAge = maxDataAge;
        this.ipLookupImporter = ipLookupImporter;
        this.geonamesImporter = geonamesImporter;
    }

    public Optional<Date> getLastModified(String alias)
    {
        return Optional.ofNullable(getSourceDataInfo().get(alias)).map(SourceDataInfo::getLastModified);
    }

    public void setStatus(String type, Date lastModified, final int count)
    {
        final Path file = basePath.resolve(FileMetaDao.FILE);
        final SourceDataInfoSet data = getSourceDataInfo();
        data.add(new SourceDataInfo(type, count, lastModified));
        JsonUtil.write(file, data);
    }

    @PostConstruct
    public void update() throws IOException
    {
        Files.createDirectories(basePath);

        final AtomicBoolean updated = new AtomicBoolean();
        ifExpired(DataType.LOCATIONS, geonamesImporter.lastRemoteModified(), () ->
        {
            geonamesImporter.purgeData();
            updated.set(true);
            return geonamesImporter.importData();
        });

        ifExpired(DataType.IP, ipLookupImporter.lastRemoteModified(), () ->
        {
            ipLookupImporter.purgeData();
            updated.set(true);
            return ipLookupImporter.importData();
        });

        if (!updated.get())
        {
            logger.info("No data to update. Max data age {}", maxDataAge);
        }

        /*final Date boundariesTimestamp = geoFabrikBoundaryLoader.lastRemoteModified();
        if (boundariesTimestamp.getTime() > getLastModified("geoboundaries") + maxDataAgeMillis)
        {
            boundaryImporter.purge();
            boundaryImporter.importData();
            setLastModified("geoboundaries", boundariesTimestamp);
        }
        */
    }

    private void ifExpired(final String type, final Date sourceTimestamp, final Supplier<Integer> updater)
    {
        final Optional<Date> localDataModifiedAt = getLastModified(type);
        if (localDataModifiedAt.isEmpty() || sourceTimestamp.getTime() > localDataModifiedAt.get().getTime() + maxDataAge.toMillis())
        {
            final int count = updater.get();
            setStatus(type, sourceTimestamp, count);
        }
    }

    public SourceDataInfoSet getSourceDataInfo()
    {
        final Path file = basePath.resolve(FileMetaDao.FILE);
        if (Files.exists(file))
        {
            return JsonUtil.read(file, SourceDataInfoSet.class);
        }
        return new SourceDataInfoSet();
    }
}
