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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.transaction.annotation.Transactional;

import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;

@Ignore
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations="classpath:test-application.properties")
@ActiveProfiles("test")
@TestExecutionListeners(inheritListeners=false, listeners={DependencyInjectionTestExecutionListener.class})
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
        assertThat(geoMetaService.getLastModified("foo")).isEqualTo(expected.getTime());
    }
    
    @Test
    public void testQueryForLocationByIp()
    {
        assertThat(geodataService.findByIp("1.0.0.1")).isNotNull();
    }

    @Test
    public void testFindLocationById()
    {
        assertThat(geodataService.findById(1581130)).isNotNull();
    }
        
    @Test
    public void testQueryForNearestLocationByPoint()
    {
        final Page<GeoLocationDistance> location = geodataService.findNear(Coordinates.from(10, 64), 100, new PageRequest(0, 10));
        assertThat(location).isNotNull();
    }
    
    @Test
    public void testQueryForPointInsideArea()
    {
        final GeoLocation location = geodataService.findWithin(Coordinates.from(10, 64), 1_000);
        assertThat(location).isNotNull();
    }

    @Test
    public void testListCountriesOnContinentAfrica()
    {
        final Page<Country> countriesInAfrica = geodataService.findCountriesOnContinent("AF", new PageRequest(0, 100));
        assertThat(countriesInAfrica).isNotNull();
        assertThat(countriesInAfrica).hasSize(58);
    }
    
    @Test
    public void testListCountriesOnContinentEurope()
    {
        final Page<Country> countriesInEurope = geodataService.findCountriesOnContinent("EU", new PageRequest(0, 100));
        assertThat(countriesInEurope).isNotNull();
        assertThat(countriesInEurope).hasSize(54);
    }
    
    @Test
    public void testListCountriesOnContinentEuropeWithLimit()
    {
        final Page<Country> countriesInEurope = geodataService.findCountriesOnContinent("EU", new PageRequest(0, 10));
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
        final Page<GeoLocation> counties = geodataService.findChildren("No", new PageRequest(0, 20));
        assertThat(counties).hasSize(19);
    }
    
    @Test
    public void testQueryForBoundaries()
    {
        final byte[] boundaries = geodataService.findBoundaries(7626836);
        assertThat(boundaries).isNotNull();
    }
}
