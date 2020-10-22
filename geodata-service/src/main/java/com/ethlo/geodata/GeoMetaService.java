package com.ethlo.geodata;

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
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.importer.GeoFabrikBoundaryLoader;
import com.ethlo.geodata.importer.GeonamesSource;
import com.ethlo.geodata.importer.jdbc.FileGeonamesImporter;
import com.ethlo.geodata.importer.jdbc.FileIpDataImporter;
import com.ethlo.geodata.util.JsonUtil;

@Service
public class GeoMetaService
{
    public static final String META_INFO_FILE = "meta.json";

    private final GeoFabrikBoundaryLoader geoFabrikBoundaryLoader = new GeoFabrikBoundaryLoader();

    private final Duration maxDataAge;
    private final FileIpDataImporter ipLookupImporter;
    private final FileGeonamesImporter geonamesImporter;
    private final Path basePath;

    public GeoMetaService(@Value("${geodata.base-path}") final Path basePath,
                          @Value("${geodata.max-data-age}") final Duration maxDataAge,
                          FileIpDataImporter ipLookupImporter,
                          FileGeonamesImporter geonamesImporter)
    {
        this.basePath = basePath;
        this.maxDataAge = maxDataAge;
        this.ipLookupImporter = ipLookupImporter;
        this.geonamesImporter = geonamesImporter;
    }


    public Optional<Date> getLastModified(GeonamesSource alias)
    {
        return Optional.ofNullable(getSourceDataInfo().get(alias)).map(SourceDataInfo::getLastModified);
    }

    public void setStatus(GeonamesSource type, Date lastModified, final int count)
    {
        final Path file = basePath.resolve(META_INFO_FILE);
        final SourceDataInfoSet data = getSourceDataInfo();
        data.add(new SourceDataInfo(type, count, lastModified));
        JsonUtil.write(file, data);
    }

    public void update() throws IOException
    {
        ifExpired(GeonamesSource.LOCATION, geonamesImporter.lastRemoteModified(), () ->
        {
            geonamesImporter.purgeData();
            return geonamesImporter.importData();
        });

        ifExpired(GeonamesSource.IP, ipLookupImporter.lastRemoteModified(), () ->
        {
            ipLookupImporter.purgeData();
            return ipLookupImporter.importData();
        });

        /*final Date boundariesTimestamp = boundaryImporter.lastRemoteModified();
        if (boundariesTimestamp.getTime() > getLastModified("geoboundaries") + maxDataAgeMillis)
        {
            boundaryImporter.purge();
            boundaryImporter.importData();
            setLastModified("geoboundaries", boundariesTimestamp);
        }
        */
    }

    private void ifExpired(final GeonamesSource type, final Date sourceTimestamp, final Supplier<Integer> updater)
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
        final Path file = basePath.resolve(META_INFO_FILE);
        if (Files.exists(file))
        {
            return JsonUtil.read(file, SourceDataInfoSet.class);
        }
        return new SourceDataInfoSet();
    }
}
