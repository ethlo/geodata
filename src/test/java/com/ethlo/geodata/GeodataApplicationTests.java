package com.ethlo.geodata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.test.context.junit4.SpringRunner;

import com.ethlo.geodata.importer.jdbc.GeoMetaService;
import com.ethlo.geodata.model.Location;
import com.vividsolutions.jts.geom.Geometry;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GeodataApplicationTests
{
    private static final Logger logger = LoggerFactory.getLogger(GeodataApplicationTests.class);
    
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
    public void testBuildHierarchy()
    {
        
    }
    
    @Test
    public void testQueryForLocationByIp()
    {
        for (int i = 0; i < 5; i++)
        {
            assertThat(geodataService.findByIp("77.88.103.250")).isNotNull();
            assertThat(geodataService.findByIp("103.199.40.241")).isNotNull();
            assertThat(geodataService.findByIp("136.1.107.78")).isNotNull();
            logger.info("Pass {}", i+1);
        }
    }

    @Test
    public void testFindLocationById()
    {
        assertThat(geodataService.findById(1581130)).isNotNull();
    }
        
    @Test
    public void testQueryForNearestLocationByPoint()
    {
        final Location location = geodataService.findNear(new Point(10, 62), 1000);
        System.out.println(location);
        assertThat(location).isNotNull();
    }
    
    @Test
    public void testQueryForPointInsideArea()
    {
        final Location location = geodataService.findWithin(new Point(10, 62), 90);
        System.out.println(location);
        assertThat(location).isNotNull();
    }

    @Test
    public void testQueryForBoundaries()
    {
        final Geometry boundaries = geodataService.findBoundaries(7626836);
        assertThat(boundaries).isNotNull();
    }
}
