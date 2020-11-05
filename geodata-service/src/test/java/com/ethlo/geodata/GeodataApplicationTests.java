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

import static com.ethlo.geodata.util.InetUtil.inet;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.View;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test-application.properties")
public class GeodataApplicationTests
{
    @Autowired
    private GeodataService geodataService;

    @Test
    public void testQueryForLocationByIp()
    {
        assertThat(geodataService.findByIp(inet("77.88.103.250"))).isNotNull();
        assertThat(geodataService.findByIp(inet("103.199.40.241"))).isNotNull();
        assertThat(geodataService.findByIp(inet("110.1.107.78"))).isNotNull();
    }

    @Test
    public void testFindLocationByIdOfOsloNorway()
    {
        assertThat(geodataService.findById(3143242)).isNotNull();
    }

    @Test
    public void testQueryForNearestLocationByPoint()
    {
        final Page<GeoLocationDistance> location = geodataService.findNear(Coordinates.from(61, 10.5), 100, PageRequest.of(0, 10));
        assertThat(location).isNotNull();
    }

    @Test
    public void testQueryForPointInsideNorwayReturnsNorway()
    {
        final GeoLocation location = geodataService.findWithin(Coordinates.from(61, 10.7), 1_000);
        assertThat(location).isNotNull();
        assertThat(location.getId()).isEqualTo(3144096);
    }

    @Test
    public void testListCountriesOnContinentAfrica()
    {
        final Page<Country> countriesInAfrica = geodataService.findCountriesOnContinent("AF", PageRequest.of(0, 100));
        assertThat(countriesInAfrica).isNotNull();
        assertThat(countriesInAfrica).hasSize(58);
    }

    @Test
    public void testListCountriesOnContinentEurope()
    {
        final Page<Country> countriesInEurope = geodataService.findCountriesOnContinent("EU", PageRequest.of(0, 100));
        assertThat(countriesInEurope).isNotNull();
        assertThat(countriesInEurope).hasSize(54);
    }

    @Test
    public void testListCountriesOnContinentEuropeWithLimit()
    {
        final Page<Country> countriesInEurope = geodataService.findCountriesOnContinent("EU", PageRequest.of(0, 10));
        assertThat(countriesInEurope).isNotNull();
        assertThat(countriesInEurope).hasSize(10);
    }

    @Test
    public void testGetCountryByCode()
    {
        final Country country = geodataService.findCountryByCode("NO");
        assertThat(country).isNotNull();
    }

    @Test
    public void testListContinents()
    {
        final Page<Continent> continents = geodataService.findContinents();
        assertThat(continents).hasSize(7);
    }

    @Test
    public void testListChildrenOfCountry()
    {
        final Page<GeoLocation> counties = geodataService.findChildren("No", PageRequest.of(0, 20));
        assertThat(counties).hasSize(11);
    }

    @Test
    public void testListChildrenOfNorway()
    {
        final Page<GeoLocation> children = geodataService.findChildren(3144096, PageRequest.of(0, 20));
        assertThat(children).hasSize(13);
    }

    @Test
    public void testListStatesOfUsa()
    {
        final Page<GeoLocation> states = geodataService.findChildren("US", PageRequest.of(0, 100));
        assertThat(states).hasSize(51);
    }

    @Test
    public void testLocationsInOsloCounty()
    {
        final Page<GeoLocation> locations = geodataService.findChildren(3143242, PageRequest.of(0, 100));
        assertThat(locations).isNotEmpty();
    }

    @Test
    public void testQueryForBoundariesOfNorway()
    {
        final Optional<Geometry> boundaries = geodataService.findBoundaries(3144096);
        assertThat(boundaries).isNotEmpty();
    }

    @Test
    public void testQueryForSimplifiedBoundariesOfNorway()
    {
        final Optional<Geometry> boundaries = geodataService.findBoundaries(3144096, new View(6, 18, 50, 62, 640, 480));
        assertThat(boundaries).isNotEmpty();
    }
}
