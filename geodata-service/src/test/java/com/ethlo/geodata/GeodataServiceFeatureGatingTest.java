package com.ethlo.geodata;

/*-
 * #%L
 * geodata-service
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import com.ethlo.geodata.GeodataFeatures.Feature;
import com.ethlo.geodata.dao.BoundaryDao;
import com.ethlo.geodata.dao.CountryDao;
import com.ethlo.geodata.dao.FeatureCodeDao;
import com.ethlo.geodata.dao.HierarchyDao;
import com.ethlo.geodata.dao.IpDao;
import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.dao.MetaDao;
import com.ethlo.geodata.dao.TimeZoneDao;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.MapFeature;
import com.ethlo.geodata.model.RawLocation;

class GeodataServiceFeatureGatingTest
{
    private static final int OSLO = 3143244;
    private static final Coordinates NEAR_OSLO = Coordinates.from(59.9, 10.7);

    private static SourceDataInfoSet manifest(final String... types)
    {
        final SourceDataInfoSet manifest = new SourceDataInfoSet();
        manifest.add(new SourceDataInfo(DataType.LOCATIONS, 8, OffsetDateTime.now()));
        for (final String type : types)
        {
            manifest.add(new SourceDataInfo(type, 1, OffsetDateTime.now()));
        }
        return manifest;
    }

    private static BoundaryDao boundaryDao()
    {
        final BoundaryDao boundaryDao = mock(BoundaryDao.class);
        when(boundaryDao.stream()).thenAnswer(inv -> Stream.empty());
        when(boundaryDao.hasGeometry(anyInt())).thenReturn(true);
        return boundaryDao;
    }

    private static GeodataServiceImpl loadedService(final GeodataFeatures features, final SourceDataInfoSet manifest)
    {
        return loadedService(features, manifest, boundaryDao());
    }

    private static GeodataServiceImpl loadedService(final GeodataFeatures features, final SourceDataInfoSet manifest, final BoundaryDao boundaryDao)
    {
        final GeodataServiceImpl service = service(features, manifest, boundaryDao);
        service.load();
        return service;
    }

    private static GeodataServiceImpl service(final GeodataFeatures features, final SourceDataInfoSet manifest, final BoundaryDao boundaryDao)
    {
        final Map<Integer, RawLocation> locations = new HashMap<>();
        GeoConstants.CONTINENTS.values().forEach(id -> locations.put(id, new RawLocation(id, "Continent " + id, null, Coordinates.from(0, 0), 1, 0, 1, 0)));
        locations.put(OSLO, new RawLocation(OSLO, "Oslo", null, Coordinates.from(59.91, 10.75), 1, 0, 1, 0));

        final LocationDao locationDao = mock(LocationDao.class);
        when(locationDao.load()).thenReturn(locations.size());
        when(locationDao.stream()).thenAnswer(inv -> locations.values().stream());
        when(locationDao.get(anyInt())).thenAnswer(inv -> Optional.ofNullable(locations.get(inv.<Integer>getArgument(0))));

        final FeatureCodeDao featureCodeDao = mock(FeatureCodeDao.class);
        when(featureCodeDao.load()).thenReturn(new HashMap<>(Map.of(1, new MapFeature("L", "CONT"))));
        final TimeZoneDao timeZoneDao = mock(TimeZoneDao.class);
        when(timeZoneDao.load()).thenReturn(Map.of("UTC", 1));
        final CountryDao countryDao = mock(CountryDao.class);
        when(countryDao.load()).thenReturn(new ArrayList<>());
        final HierarchyDao hierarchyDao = mock(HierarchyDao.class);
        when(hierarchyDao.load()).thenReturn(new HashMap<>());
        final IpDao ipDao = mock(IpDao.class);
        when(ipDao.findByIp(any())).thenReturn(Optional.of(OSLO));
        final MetaDao metaDao = mock(MetaDao.class);
        when(metaDao.load()).thenReturn(manifest);

        return new GeodataServiceImpl(locationDao, ipDao, hierarchyDao, featureCodeDao, timeZoneDao, countryDao, boundaryDao, metaDao, Collections.emptyList(), 200_000, features);
    }

    @Test
    void boundariesAreLoadedWithoutCoordinates()
    {
        final BoundaryDao boundaryDao = boundaryDao();
        final GeodataServiceImpl service = loadedService(GeodataFeatures.of(Feature.BOUNDARIES), manifest(DataType.BOUNDARIES), boundaryDao);

        verify(boundaryDao).load();
        verify(boundaryDao, never()).stream();
        assertThat(service.hasBoundary(OSLO)).isTrue();
    }

    @Test
    void boundariesAreNotLoadedWhenDisabled()
    {
        final BoundaryDao boundaryDao = boundaryDao();
        loadedService(GeodataFeatures.of(Feature.COORDINATES), manifest(DataType.BOUNDARIES), boundaryDao);

        verify(boundaryDao, never()).load();
        verify(boundaryDao, never()).stream();
    }

    @Test
    void nothingIsAvailableBeforeLoad()
    {
        final GeodataServiceImpl service = service(GeodataFeatures.all(), manifest(DataType.IP, DataType.BOUNDARIES), boundaryDao());
        assertThat(service.getFeatures().getEnabled()).isEmpty();
        assertThatThrownBy(() -> service.findByIp(InetAddress.getLoopbackAddress())).isInstanceOf(FeatureDisabledException.class);
    }

    @Test
    void enabledFeaturesWithData()
    {
        final GeodataServiceImpl service = loadedService(GeodataFeatures.all(), manifest(DataType.IP, DataType.BOUNDARIES));

        assertThat(service.getFeatures().getEnabled()).containsExactlyInAnyOrder(Feature.values());
        assertThat(service.findByIp(InetAddress.getLoopbackAddress()).getId()).isEqualTo(OSLO);
        assertThat(service.hasBoundary(OSLO)).isTrue();
        assertThat(service.findByCoordinate(NEAR_OSLO, Integer.MAX_VALUE)).isEmpty();
        assertThat(service.findWithin(NEAR_OSLO, Integer.MAX_VALUE)).isEmpty();
    }

    @Test
    void enabledFeaturesWithoutImportedDataAreDisabled()
    {
        final GeodataServiceImpl service = loadedService(GeodataFeatures.all(), manifest());

        assertThat(service.getFeatures().getEnabled()).containsExactlyInAnyOrder(Feature.COORDINATES, Feature.PHONE);
        assertThatThrownBy(() -> service.findByIp(InetAddress.getLoopbackAddress()))
                .isInstanceOf(FeatureDisabledException.class)
                .extracting(e -> ((FeatureDisabledException) e).getFeature()).isEqualTo(Feature.IP);
        assertThatThrownBy(() -> service.findBoundaries(OSLO)).isInstanceOf(FeatureDisabledException.class);
        assertThatThrownBy(() -> service.findWithin(NEAR_OSLO, Integer.MAX_VALUE)).isInstanceOf(FeatureDisabledException.class);
        assertThat(service.hasBoundary(OSLO)).isFalse();

        // Without boundaries there is no containing boundary, only the nearest location
        assertThat(service.findByCoordinate(NEAR_OSLO, Integer.MAX_VALUE)).isEmpty();
        assertThat(service.findNear(NEAR_OSLO, Integer.MAX_VALUE, PageRequest.of(0, 1)).getContent().get(0).getLocation().getId()).isEqualTo(OSLO);
    }

    @Test
    void disabledFeaturesAreUnavailableEvenWithData()
    {
        final GeodataServiceImpl service = loadedService(GeodataFeatures.of(Feature.IP), manifest(DataType.IP, DataType.BOUNDARIES));

        assertThat(service.getFeatures().getEnabled()).containsExactly(Feature.IP);
        assertThatThrownBy(() -> service.findNear(NEAR_OSLO, 10, PageRequest.of(0, 1))).isInstanceOf(FeatureDisabledException.class);
        assertThatThrownBy(() -> service.findByCoordinate(NEAR_OSLO, 10)).isInstanceOf(FeatureDisabledException.class);
        assertThatThrownBy(() -> service.findByPhoneNumber("+4722000000")).isInstanceOf(FeatureDisabledException.class);
        assertThatThrownBy(() -> service.findBoundaries(OSLO)).isInstanceOf(FeatureDisabledException.class);
    }
}
