package com.ethlo.geodata;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.geo.Point;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.ethlo.geodata.importer.jdbc.GeoMetaService;
import com.ethlo.geodata.model.Country;
import com.vividsolutions.jts.geom.Geometry;

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
        geodataService.findNear(new Point(10, 64), 100);
    }
    
    @Test
    public void testQueryForPointInsideArea()
    {
        geodataService.findWithin(new Point(10, 62), 1_000);
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
        final Country country = geodataService.findCountryByCode("NO");
        assertThat(country).isNotNull();
    }
    
    @Test
    public void testListContinents()
    {
        geodataService.getContinents();
    }
    
    @Test
    public void testListChildrenOfCountry()
    {
        geodataService.getChildren(new Country().setCode("NO"));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testQueryForBoundaries()
    {
        final Geometry boundaries = geodataService.findBoundaries(7626836);
        assertThat(boundaries).isNotNull();
    }
}
