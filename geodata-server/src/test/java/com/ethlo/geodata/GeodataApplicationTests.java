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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Geometry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.ethlo.geodata.dao.file.FileBoundaryDao;
import com.ethlo.geodata.dao.file.FileCountryDao;
import com.ethlo.geodata.dao.file.FileFeatureCodeDao;
import com.ethlo.geodata.dao.file.FileHierarchyDao;
import com.ethlo.geodata.dao.file.FileIpDao;
import com.ethlo.geodata.dao.file.FileLocationDao;
import com.ethlo.geodata.dao.file.FileTimeZoneDao;
import com.ethlo.geodata.dao.FileMetaDao;
import com.ethlo.geodata.importer.DataImporterService;
import com.ethlo.geodata.importer.FileGeonamesImporter;
import com.ethlo.geodata.importer.FileIpDataImporter;
import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.View;
import com.ethlo.geodata.util.ResourceUtil;

/**
 * Imports the small dataset in src/test/resources/fixture with the real importer, then queries the service loaded from the imported files.
 */
public class GeodataApplicationTests
{
    private static final int NORWAY = 3144096;
    private static final int OSLO_COUNTY = 3143242;
    private static final int OSLO = 3143244;
    private static final int INNLANDET = 9100002;
    private static final int EUROPE = 6255148;

    private static final String INCLUDED_FEATURES = "L.CONT,A.ADM1,A.ADM2,A.ADM3,A.ADM4,P.PPL,P.PPLA,P.PPLC,A.PCLI,T.ISL";

    @TempDir
    static Path dir;

    private static GeodataService geodataService;

    @BeforeAll
    static void importAndLoad() throws IOException
    {
        final Path data = dir.resolve("data");
        final Path input = dir.resolve("input");
        final Path boundaries = Files.createDirectories(input.resolve(DataType.BOUNDARIES));
        try (InputStream shapes = GeodataApplicationTests.class.getResourceAsStream("/fixture/shapes.tsv"))
        {
            Files.copy(shapes, boundaries.resolve("shapes.tsv"));
        }

        final Duration maxAge = Duration.ofDays(1);
        final ResourceUtil resourceUtil = new ResourceUtil(Files.createDirectories(dir.resolve("tmp")));
        final FileGeonamesImporter geonamesImporter = new FileGeonamesImporter(
                "classpath:fixture/alternateNames.txt", "classpath:fixture/countryInfo.txt", "classpath:fixture/allCountries.txt",
                INCLUDED_FEATURES, data, maxAge,
                new FileFeatureCodeDao(data), new FileTimeZoneDao(data), new FileHierarchyDao(data), new FileCountryDao(data), resourceUtil);
        final FileIpDataImporter ipImporter = new FileIpDataImporter("classpath:fixture/GeoIP2-City-Test.tar.gz", data, maxAge, resourceUtil);
        new DataImporterService(data, input, maxAge, ipImporter, geonamesImporter, GeodataFeatures.all()).update();

        final GeodataServiceImpl service = new GeodataServiceImpl(new FileLocationDao(data), new FileIpDao(data), new FileHierarchyDao(data),
                new FileFeatureCodeDao(data), new FileTimeZoneDao(data), new FileCountryDao(data), new FileBoundaryDao(data), new FileMetaDao(data),
                List.of("P.PPLC", "T.ISL", "P.PPL", "P.PPLA"), 200_000, GeodataFeatures.all());
        service.load();
        geodataService = service;
    }

    @Test
    public void testQueryForLocationByIp()
    {
        assertThat(geodataService.findByIp(inet("81.2.69.142")).getId()).isEqualTo(2643743);
        assertThat(geodataService.findByIp(inet("89.160.20.112")).getId()).isEqualTo(2694762);
        assertThat(geodataService.findByIp(inet("216.160.83.56")).getId()).isEqualTo(5803556);
    }

    @Test
    public void testFindLocationByIdOfOsloNorway()
    {
        final GeoLocation oslo = geodataService.findById(OSLO_COUNTY);
        assertThat(oslo.getName()).isEqualTo("Oslo");
        assertThat(oslo.getParentLocationId()).isEqualTo(NORWAY);
    }

    @Test
    public void testQueryForNearestLocationByPoint()
    {
        final Page<GeoLocationDistance> locations = geodataService.findNear(Coordinates.from(61, 10.5), 100, PageRequest.of(0, 10));
        assertThat(locations.getContent()).isNotEmpty();
        assertThat(locations.getContent().getFirst().getLocation().getId()).isEqualTo(INNLANDET);
    }

    @Test
    public void testQueryForPointInsideNorwayReturnsNorway()
    {
        final Optional<LookupMetadata> lookupMetadata = geodataService.findWithin(Coordinates.from(61, 10.7), 1_000);
        assertThat(lookupMetadata).isPresent();
        assertThat(lookupMetadata.get().getLocation().getId()).isEqualTo(NORWAY);
    }

    @Test
    public void testListCountriesOnContinentAfrica()
    {
        assertThat(geodataService.findCountriesOnContinent("AF", PageRequest.of(0, 100))).hasSize(58);
    }

    @Test
    public void testListCountriesOnContinentEurope()
    {
        assertThat(geodataService.findCountriesOnContinent("EU", PageRequest.of(0, 100))).hasSize(54);
    }

    @Test
    public void testListCountriesOnContinentEuropeWithLimit()
    {
        assertThat(geodataService.findCountriesOnContinent("EU", PageRequest.of(0, 10))).hasSize(10);
    }

    @Test
    public void testGetCountryByCode()
    {
        final Country country = geodataService.findCountryByCode("NO");
        assertThat(country).isNotNull();
        assertThat(country.getId()).isEqualTo(NORWAY);
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
        assertThat(geodataService.findChildren(NORWAY, true, PageRequest.of(0, 20))).hasSize(11);
        assertThat(geodataService.findChildren(NORWAY, false, PageRequest.of(0, 20))).hasSize(12);
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
        final Page<GeoLocation> locations = geodataService.findChildren(OSLO_COUNTY, false, PageRequest.of(0, 100));
        assertThat(locations.getContent()).extracting(GeoLocation::getId).containsExactly(OSLO);
    }

    @Test
    public void testPathOfOslo()
    {
        assertThat(geodataService.findPath(OSLO)).extracting(GeoLocation::getId).containsExactly(OSLO_COUNTY, NORWAY, EUROPE);
        assertThat(geodataService.isLocationInside(OSLO, NORWAY)).isTrue();
    }

    @Test
    public void testQueryForBoundariesOfNorway()
    {
        final Optional<Geometry> boundaries = geodataService.findBoundaries(NORWAY);
        assertThat(boundaries).isNotEmpty();
    }

    @Test
    public void testQueryForSimplifiedBoundariesOfNorway()
    {
        final Optional<Geometry> boundaries = geodataService.findBoundaries(NORWAY, new View(6, 18, 50, 62, 640, 480));
        assertThat(boundaries).isNotEmpty();
    }
}
