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
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.ethlo.geodata.model.Coordinates;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations="classpath:test-application.properties")
public class NoDataAssertGeodataApplicationTests
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
        geodataService.findByIp("77.88.103.250");
        geodataService.findByIp("103.199.40.241");
        geodataService.findByIp("136.1.107.78");
    }

    @Test
    public void testFindLocationById()
    {
        geodataService.findById(1581130);
    }
        
    @Test
    public void testQueryForNearestLocationByPoint()
    {
        geodataService.findNear(Coordinates.from(10, 64), 100, new PageRequest(0, 10));
    }
    
    @Test
    public void testQueryForPointInsideArea()
    {
        try
        {
            geodataService.findWithin(Coordinates.from(10, 62), 100);
        }
        catch (EmptyResultDataAccessException exc)
        {
            
        }
    }

    @Test
    public void testListCountriesOnContinentAfrica()
    {
        geodataService.findCountriesOnContinent("AF", new PageRequest(0, 100));
    }
    
    @Test
    public void testListCountriesOnContinentEurope()
    {
        geodataService.findCountriesOnContinent("EU", new PageRequest(0, 100));
    }
    
    @Test
    public void testListCountriesOnContinentEuropeWithLimit()
    {
        geodataService.findCountriesOnContinent("EU", new PageRequest(0, 10));
    }
    
    @Test
    public void testGetCountryByCode()
    {
        geodataService.findCountryByCode("NO");
    }
    
    @Test
    public void findPreviewBoundary()
    {
    	final long id = 6255151;
    	final byte[] boundaries = geodataService.findBoundaries(id);
    	final byte[] simplifiedBoundaries = geodataService.findPreviewBoundaries(id);
    	assertThat(boundaries.length).isGreaterThan(simplifiedBoundaries.length);  
    }
    
    @Test
    public void testFindCountryItaly()
    {
        geodataService.findByName("italy", new PageRequest(0,  10));
    }
    
    @Test
    public void testListContinents()
    {
        geodataService.findContinents();
    }
    
    @Test
    public void testListChildrenOfCountry()
    {
        geodataService.findChildren("nO", new PageRequest(0, 10));
    }
    
    @Test
    public void testQueryForBoundaries()
    {
        final byte[] boundaries = geodataService.findBoundaries(7626836);
        assertThat(boundaries).isNotNull();
    }
}
