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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.iterators.LazyIteratorChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.DataType;
import com.ethlo.geodata.GeoConstants;
import com.ethlo.geodata.SourceDataInfo;
import com.ethlo.geodata.SourceDataInfoSet;
import com.ethlo.geodata.dao.FeatureCodeDao;
import com.ethlo.geodata.dao.FileMetaDao;
import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.dao.file.FileFeatureCodeDao;
import com.ethlo.geodata.dao.file.FileMmapLocationDao;
import com.ethlo.geodata.importer.boundary.GeoNamesBoundaryImporter;
import com.ethlo.geodata.model.BoundaryData;
import com.ethlo.geodata.model.MapFeature;
import com.ethlo.geodata.util.IoUtil;
import com.ethlo.geodata.util.JsonUtil;
import com.ethlo.geodata.util.SerializationUtil;

@Service
public class DataImporterService
{
    public static final int MAX_TILE_SIZE = 2_000;
    private static final Logger logger = LoggerFactory.getLogger(DataImporterService.class);
    private static final Set<String> supportedExtensions = new HashSet<>(Arrays.asList("tsv", "geojson", "wkb", "wkt", "kml"));
    private final Duration maxDataAge;
    private final FileIpDataImporter ipLookupImporter;
    private final FileGeonamesImporter geonamesImporter;
    private final Path basePath;
    private GeoNamesBoundaryImporter geoNamesBoundaryImporter;

    public DataImporterService(@Value("${geodata.base-path}") @NotNull final Path basePath,
                               @Value("${geodata.max-data-age}") @NotNull final Duration maxDataAge,
                               FileIpDataImporter ipLookupImporter,
                               FileGeonamesImporter geonamesImporter)
    {
        this.basePath = Objects.requireNonNull(basePath, "GEODATA_BASEPATH must be set");
        this.maxDataAge = Objects.requireNonNull(maxDataAge, "GEODATA_MAXDATAAGE must be set");
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

        ifExpired(DataType.BOUNDARIES, new Date(), () ->
        {
            final FeatureCodeDao featureCodeDao = new FileFeatureCodeDao(basePath);
            final LocationDao locationDao = new FileMmapLocationDao(basePath);
            locationDao.load();

            final Map<Integer, MapFeature> featureCodes = featureCodeDao.load();
            this.geoNamesBoundaryImporter = new GeoNamesBoundaryImporter(locationDao, basePath, MAX_TILE_SIZE, l ->
            {
                final MapFeature mapFeature = featureCodes.get(l.getMapFeatureId());
                final String key = mapFeature.getKey();
                return GeoConstants.ADMINISTRATIVE_OR_ABOVE.contains(key) && !key.equals("A.ADM3") && !key.equals("A.ADM4");
            });

            updated.set(true);

            final Path boundaryImportFolder = basePath.resolve("input").resolve("boundaries");
            logger.info("Checking folder {} for boundary data", boundaryImportFolder);
            if (Files.exists(boundaryImportFolder))
            {
                return importFromDirectory(boundaryImportFolder);
            }
            else
            {
                logger.info("Folder {} for boundary data does not exist. Skipping.", boundaryImportFolder);
                return 0;
            }
        });

        if (!updated.get())
        {
            logger.info("No data to update. Max data age {}", maxDataAge);
        }
    }

    private int importFromDirectory(final Path boundaryImportFolder)
    {
        final Collection<Integer> overrides = getSingleFileIds(boundaryImportFolder);

        try (final Stream<Path> stream = Files.list(boundaryImportFolder))
        {
            final Iterator<Path> inputFileIterator = stream
                    .filter(supportedBoundaryFile())
                    .sorted((a, b) ->
                    {
                        final String extA = IoUtil.getExtension(a);
                        final String extB = IoUtil.getExtension(b);
                        final boolean isMultiA = isMulti(extA);
                        final boolean isMultiB = isMulti(extB);
                        if (isMultiA && !isMultiB)
                        {
                            return -1;
                        }
                        else if (!isMultiA && isMultiB)
                        {
                            return 1;
                        }
                        return a.compareTo(b);
                    }).iterator();


            final LazyIteratorChain<BoundaryData> wrapper = new LazyIteratorChain<>()
            {
                @Override
                protected Iterator<BoundaryData> nextIterator(final int count)
                {
                    if (inputFileIterator.hasNext())
                    {
                        return processBoundaryFile(overrides, inputFileIterator.next());
                    }
                    return null;
                }
            };

            return geoNamesBoundaryImporter.write(wrapper);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private Collection<Integer> getSingleFileIds(final Path boundaryImportFolder)
    {
        try (final Stream<Path> stream = Files.list(boundaryImportFolder))
        {
            return stream
                    .filter(supportedBoundaryFile())
                    .filter(p -> !isMulti(IoUtil.getExtension(p)))
                    .map(GeoNamesBoundaryImporter::getIdFromFilename)
                    .collect(Collectors.toSet());
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isMulti(final String ext)
    {
        return ext.equals("tsv");
    }

    private CloseableIterator<BoundaryData> processBoundaryFile(final Collection<Integer> overrides, final Path path)
    {
        logger.info("Processing boundary file: {}", path.toAbsolutePath());

        switch (IoUtil.getExtension(path))
        {
            case "tsv":
                return geoNamesBoundaryImporter.processTsv(overrides, path, progress -> logger.info("{} progress {}", path.getFileName(), progress));

            case "kml":
                return SerializationUtil.wrapClosable(geoNamesBoundaryImporter.processKml(path), null);

            case "geojson":
                return SerializationUtil.wrapClosable(geoNamesBoundaryImporter.processGeoJson(path), null);

            default:
                throw new UnsupportedOperationException("Unhandled file type: " + path);
        }
    }

    private Predicate<Path> supportedBoundaryFile()
    {
        return p -> supportedExtensions.contains(IoUtil.getExtension(p));
    }

    private void ifExpired(final String type, final Date sourceTimestamp, final Supplier<Integer> updater)
    {
        logger.info("Checking data type: {}, max age: {}", type, maxDataAge);
        final Optional<Date> localDataModifiedAt = getLastModified(type);
        logger.info("local last modified: {}", localDataModifiedAt.orElse(null));
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
