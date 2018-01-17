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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.transaction.annotation.Transactional;

import com.ethlo.geodata.importer.DataType;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.View;
import com.ethlo.geodata.util.GeometryUtil;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.geojson.GeoJsonWriter;

@RunWith(SpringRunner.class)
@SpringBootApplication(exclude=TransactionAutoConfiguration.class)
@SpringBootTest
@TestPropertySource(locations="classpath:test-application.properties")
@ActiveProfiles("test")
@TestExecutionListeners(inheritListeners=false, listeners={DependencyInjectionTestExecutionListener.class})
public class NoDataAssertGeodataApplicationTests
{
    private static final Logger logger = LoggerFactory.getLogger(NoDataAssertGeodataApplicationTests.class);
    
    private static final long SOMALIA_ID = 51537;
    
    private GeodataService geodataService;
    
    @Autowired
    private GeoMetaService geoMetaService;
    
    @Autowired
    private ApplicationContext appCtx;
    
    @Value("${data.directory}")
    private File dataDirectory;
    
    private static boolean initialized = false;
    private static boolean loaded = false;
    
    @Before
    public void contextLoads() throws IOException, SQLException
    {
        if (! initialized)
        {
            //delete(dataDirectory);
            //dataDirectory.mkdir();
            geoMetaService.update();
            initialized = true;
        }

        final GeodataServiceImpl impl = appCtx.getBean(GeodataServiceImpl.class);
        if (! loaded)
        {
            impl.load();
            loaded = true;
        }
        
        geodataService = impl;
    }
    
    private void delete(File dir) throws IOException
    {
        if (dir.exists() && dir.isDirectory())
        {
            Path rootPath = Paths.get(dir.getAbsolutePath());
            Files.walk(rootPath)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .peek(e->logger.info("Deleting {}", e))
                .forEach(File::delete);
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
        geoMetaService.setSourceDataInfo(DataType.COUNTRY, expected, 122);
        assertThat(geoMetaService.getLastModified(DataType.COUNTRY)).isEqualTo(expected.getTime());
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
    	final byte[] boundaries = geodataService.findBoundaries(SOMALIA_ID);
    	final byte[] simplifiedBoundaries = geodataService.findBoundaries(SOMALIA_ID, new View(5, 10, 55, 75, 1920, 1080));
    	assertThat(boundaries.length).isGreaterThan(simplifiedBoundaries.length);
    }
    
    @Test
    public void testClipAtBoundaryPartiallyInside() throws IOException, ParseException
    {
    	final Geometry boundaries = new WKBReader().read(geodataService.findBoundaries(SOMALIA_ID));
    	final double minLng=109.02832043750004;
    	final double maxLng=128.03466809375004;
    	final double minLat=-25.105497099694126;
    	final double maxLat=18.999802543661826;
    	final Geometry geo = GeometryUtil.clip(new Envelope(minLng, maxLng, minLat, maxLat), boundaries);
    	final GeoJsonWriter w = new GeoJsonWriter();
    }
    
    @Test
    public void testClipAtBoundaryTotallyInside() throws IOException, ParseException
    {
    	final Geometry boundaries = new WKBReader().read(geodataService.findBoundaries(SOMALIA_ID));
    	final double minLng=124.71679700000004;
    	final double maxLng=143.72314465625004;
    	final double minLat=-29.973969979050846;
    	final double maxLat=-24.106646903690297;
    	final Geometry geo = GeometryUtil.clip(new Envelope(minLng, maxLng, minLat, maxLat), boundaries);
    	final GeoJsonWriter w = new GeoJsonWriter();
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
