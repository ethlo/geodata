package com.ethlo.geodata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.importer.jdbc.GeoMetaService;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.Location;
import com.vividsolutions.jts.geom.Geometry;

@RunWith(SpringRunner.class)
@SpringBootTest
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
        final Location location = geodataService.findNear(new Point(10, 64), 100);
        assertThat(location).isNotNull();
    }
    
    @Test
    public void testQueryForPointInsideArea()
    {
        final Location location = geodataService.findWithin(new Point(10, 62), 1_000);
        assertThat(location).isNotNull();
    }

    @Test
    public void testListCountriesOnContinentAfrica()
    {
        final Collection<Location> countriesInAfrica = geodataService.findCountriesOnContinent("AF");
        assertThat(countriesInAfrica).isNotNull();
        assertThat(countriesInAfrica).hasSize(58);
    }
    
    @Test
    public void testListCountriesOnContinentEurope()
    {
        final Collection<Location> countriesInEurope = geodataService.findCountriesOnContinent("EU");
        assertThat(countriesInEurope).isNotNull();
        assertThat(countriesInEurope).hasSize(54);
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
        final Collection<Location> continents = geodataService.getContinents();
        assertThat(continents).hasSize(7);
    }
    
    @Test
    public void testListChildrenOfCountry()
    {
        final Collection<Location> counties = geodataService.getChildren(new Country().setCode("NO"));
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
