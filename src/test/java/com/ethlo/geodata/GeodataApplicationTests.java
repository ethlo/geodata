package com.ethlo.geodata;

/*-
 * #%L
 * geodata
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.geo.Point;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.ethlo.geodata.importer.jdbc.GeoMetaService;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.GeoLocation;
import com.vividsolutions.jts.geom.Geometry;

@Ignore
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations="classpath:test-application.properties")
public class GeodataApplicationTests
{
    private static boolean initialized = false;
    
    @Autowired
    private GeodataService geodataService;
    
    @Autowired
    private GeoMetaService geoMetaService;
    
    @Before
    public void contextLoads() throws IOException, SQLException
    {
        if (! initialized)
        {
            geoMetaService.update();
            initialized = true;
        }
    }
    
    @Test
    @Transactional
    public void metadataTest() throws IOException
    {
        final Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2010);
        cal.set(Calendar.MONTH, 10);
        cal.set(Calendar.DAY_OF_MONTH, 31);
        cal.set(Calendar.HOUR_OF_DAY, 14);
        cal.set(Calendar.MINUTE, 56);
        cal.set(Calendar.SECOND, 45);
        cal.set(Calendar.MILLISECOND, 0);
        final Date expected = cal.getTime();
        geoMetaService.setLastModified("foo", expected);
        assertThat(geoMetaService.getLastModified("foo").getTime()).isEqualTo(expected.getTime());
    }
    
    @Test
    public void testQueryForLocationByIp()
    {
        assertThat(geodataService.findByIp("77.88.103.250")).isNotNull();
        assertThat(geodataService.findByIp("103.199.40.241")).isNotNull();
        assertThat(geodataService.findByIp("136.1.107.78")).isNotNull();
    }

    @Test
    public void testFindLocationById()
    {
        assertThat(geodataService.findById(1581130)).isNotNull();
    }
        
    @Test
    public void testQueryForNearestLocationByPoint()
    {
        final GeoLocation location = geodataService.findNear(new Point(10, 64), 100);
        assertThat(location).isNotNull();
    }
    
    @Test
    public void testQueryForPointInsideArea()
    {
        final GeoLocation location = geodataService.findWithin(new Point(10, 62), 1_000);
        assertThat(location).isNotNull();
    }

    @Test
    public void testListCountriesOnContinentAfrica()
    {
        final Page<GeoLocation> countriesInAfrica = geodataService.findCountriesOnContinent("AF", new PageRequest(0, 100));
        assertThat(countriesInAfrica).isNotNull();
        assertThat(countriesInAfrica).hasSize(58);
    }
    
    @Test
    public void testListCountriesOnContinentEurope()
    {
        final Page<GeoLocation> countriesInEurope = geodataService.findCountriesOnContinent("EU", new PageRequest(0, 100));
        assertThat(countriesInEurope).isNotNull();
        assertThat(countriesInEurope).hasSize(54);
    }
    
    @Test
    public void testListCountriesOnContinentEuropeWithLimit()
    {
        final Page<GeoLocation> countriesInEurope = geodataService.findCountriesOnContinent("EU", new PageRequest(0, 10));
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
        final Collection<GeoLocation> continents = geodataService.getContinents();
        assertThat(continents).hasSize(7);
    }
    
    @Test
    public void testListChildrenOfCountry()
    {
        final Collection<GeoLocation> counties = geodataService.getChildren(new Country().setCode("NO"));
        assertThat(counties).hasSize(19);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testQueryForBoundaries()
    {
        final Geometry boundaries = geodataService.findBoundaries(7626836);
        assertThat(boundaries).isNotNull();
    }
}
