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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ethlo.geodata.dao.BoundaryDao;
import com.ethlo.geodata.dao.CountryDao;
import com.ethlo.geodata.dao.FeatureCodeDao;
import com.ethlo.geodata.dao.HierarchyDao;
import com.ethlo.geodata.dao.IpDao;
import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.dao.MetaDao;
import com.ethlo.geodata.dao.TimeZoneDao;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.MapFeature;
import com.ethlo.geodata.model.RawLocation;

class GeodataServicePathTest
{
    private static final int EUROPE = GeoConstants.CONTINENTS.get("EU");
    private static final int NORWAY = 100;
    private static final int OSLO = 101;
    private static final int LOOP_A = 200;
    private static final int LOOP_B = 201;
    private static final int LOOP_CHILD = 202;

    private static GeodataServiceImpl service(final Map<Integer, Integer> childToParent)
    {
        final Map<Integer, RawLocation> locations = new HashMap<>();
        GeoConstants.CONTINENTS.values().forEach(id -> locations.put(id, location(id)));
        for (final int id : List.of(NORWAY, OSLO, LOOP_A, LOOP_B, LOOP_CHILD))
        {
            locations.put(id, location(id));
        }

        final LocationDao locationDao = mock(LocationDao.class);
        when(locationDao.load()).thenReturn(locations.size());
        when(locationDao.stream()).thenAnswer(inv -> locations.values().stream());
        when(locationDao.get(anyInt())).thenAnswer(inv -> Optional.ofNullable(locations.get(inv.<Integer>getArgument(0))));
        final FeatureCodeDao featureCodeDao = mock(FeatureCodeDao.class);
        when(featureCodeDao.load()).thenReturn(new HashMap<>(Map.of(1, new MapFeature("A", "ADM1"))));
        final TimeZoneDao timeZoneDao = mock(TimeZoneDao.class);
        when(timeZoneDao.load()).thenReturn(Map.of("UTC", 1));
        final CountryDao countryDao = mock(CountryDao.class);
        when(countryDao.load()).thenReturn(new ArrayList<>());
        final HierarchyDao hierarchyDao = mock(HierarchyDao.class);
        when(hierarchyDao.load()).thenReturn(childToParent);
        final MetaDao metaDao = mock(MetaDao.class);
        final SourceDataInfoSet manifest = new SourceDataInfoSet();
        manifest.add(new SourceDataInfo(DataType.LOCATIONS, locations.size(), OffsetDateTime.now()));
        when(metaDao.load()).thenReturn(manifest);

        final GeodataServiceImpl service = new GeodataServiceImpl(locationDao, mock(IpDao.class), hierarchyDao, featureCodeDao, timeZoneDao, countryDao,
                mock(BoundaryDao.class), metaDao, Collections.emptyList(), 200_000, GeodataFeatures.of());
        service.load();
        return service;
    }

    private static RawLocation location(final int id)
    {
        return new RawLocation(id, "Location " + id, null, Coordinates.from(0, 0), 1, 0, 1, 0);
    }

    @Test
    void pathExcludesLocationAndRoot()
    {
        final GeodataServiceImpl service = service(Map.of(OSLO, NORWAY, NORWAY, EUROPE, EUROPE, GeoConstants.EARTH_ID));
        assertThat(service.findPath(OSLO)).extracting(GeoLocation::getId).containsExactly(NORWAY, EUROPE);
        assertThat(service.isLocationInside(OSLO, EUROPE)).isTrue();
        assertThat(service.isLocationInside(OSLO, GeoConstants.EARTH_ID)).isFalse();
    }

    @Test
    void circularHierarchyEndsThePathWithoutDroppingAnAncestor()
    {
        final GeodataServiceImpl service = service(Map.of(LOOP_CHILD, LOOP_A, LOOP_A, LOOP_B, LOOP_B, LOOP_A));
        assertThat(service.findPath(LOOP_CHILD)).extracting(GeoLocation::getId).containsExactly(LOOP_A, LOOP_B);
        assertThat(service.isLocationInside(LOOP_CHILD, LOOP_B)).isTrue();
    }
}
