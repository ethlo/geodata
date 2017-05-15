package com.ethlo.geodata;

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.ethlo.geodata.importer.GeonamesImporter;
import com.ethlo.geodata.importer.IpLookupImporter;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GeodataApplicationTests
{
    @Autowired
    private IpLookupImporter ipLookupImporter;
    
    @Autowired
    private GeonamesImporter geonamesImporter;
    
    @Test
    public void contextLoads() throws IOException
    {
        geonamesImporter.importLocations();
        ipLookupImporter.importIpRanges();
    }
}
