package com.ethlo.geodata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.test.context.junit4.SpringRunner;

import com.ethlo.geodata.importer.jdbc.JdbcGeonamesBoundaryImporter;
import com.ethlo.geodata.importer.jdbc.JdbcGeonamesImporter;
import com.ethlo.geodata.importer.jdbc.JdbcIpLookupImporter;
import com.ethlo.geodata.model.Location;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GeodataApplicationTests
{
    private static boolean initialized = false;
    
    @Autowired
    private JdbcIpLookupImporter ipLookupImporter;
    
    @Autowired
    private JdbcGeonamesImporter geonamesImporter;
    
    @Autowired
    private JdbcGeonamesBoundaryImporter boundaryImporter;
    
    @Autowired
    private GeodataService geodataService;
    
    @Before
    public void contextLoads() throws IOException, SQLException
    {
        if (! initialized)
        {
            geonamesImporter.importLocations();
            ipLookupImporter.importIpRanges();
            boundaryImporter.importBoundaries();
            initialized = true;
        }
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
        
    @Ignore
    @Test
    public void testQueryForNearestLocationByPoint()
    {
        final Location location = geodataService.findByCoordinates(new Point(62,10));
        assertThat(location).isNotNull();
    }

    @Ignore
    @Test
    public void testQueryForBoundaries()
    {
        final List<Point> boundaries = geodataService.findBoundaries(7626836);
        assertThat(boundaries).isNotNull();
    }
}
