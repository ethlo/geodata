package com.ethlo.geodata.importer.jdbc;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.net.util.SubnetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.importer.IpLookupImporter;
import com.ethlo.geodata.util.ResourceUtil;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;

@Component
public class JdbcIpLookupImporter implements PersistentImporter
{
    private static final Logger logger = LoggerFactory.getLogger(JdbcIpLookupImporter.class);

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    
    @Value("${geodata.geolite2.source}")
    private String url;

    @Override
    public void importData() throws IOException
    {
        final String sql = "INSERT INTO geoip(geoname_id, geoname_country_id, first, last) VALUES (:geoname_id, :geoname_country_id, :first, :last)";
        final Map.Entry<Date, File> ipDataFile = ResourceUtil.fetchResource("ipData", url);
        
        final AtomicInteger count = new AtomicInteger(0);

        final IpLookupImporter ipLookupImporter = new IpLookupImporter(ipDataFile.getValue());
        ipLookupImporter.processFile(entry->
        {
            final String strGeoNameId = findMapValue(entry, "geoname_id", "represented_country_geoname_id", "registered_country_geoname_id");
            final String strGeoNameCountryId = findMapValue(entry, "represented_country_geoname_id", "registered_country_geoname_id");
            final Long geonameId = strGeoNameId != null ? Long.parseLong(strGeoNameId) : null;
            final Long geonameCountryId = strGeoNameCountryId != null ? Long.parseLong(strGeoNameCountryId) : null;
            if (geonameId != null)
            {
                final SubnetUtils u = new SubnetUtils(entry.get("network"));
                final long lower = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(u.getInfo().getLowAddress()))).longValue();
                final long upper = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(u.getInfo().getHighAddress()))).longValue();
                final Double lat = parseDouble(entry.get("lat"));
                final Double lng = parseDouble(entry.get("lng"));
                final Map<String, Object> paramMap = new HashMap<>(5);
                paramMap.put("geoname_id", geonameId);
                paramMap.put("geoname_country_id", geonameCountryId);
                paramMap.put("first", lower);
                paramMap.put("last", upper);
                paramMap.put("lat", lat);
                paramMap.put("lng", lng);
                
                try
                {
                    final int affected = jdbcTemplate.update(sql, paramMap);
                    Assert.isTrue(affected == 1, "Should insert 1 row");
                }
                catch (DataIntegrityViolationException exc)
                {
                    logger.warn("{} - {}", paramMap, exc.getMessage());
                }

                if (count.get() % 100_000 == 0)
                {
                    logger.info("Processed {}", count.get());
                }
                
                count.getAndIncrement();
            }
        });
    }

    private Double parseDouble(String str)
    {
        if (str == null)
        {
            return null;
        }
        
        if (StringUtils.hasLength(str))
        {
            return Double.parseDouble(str);
        }
        
        return null;
    }
    
    private String findMapValue(Map<String, String> map, String... needles)
    {
        final Iterator<String> it = Arrays.asList(needles).iterator();
        
        while (it.hasNext())
        {
            final String key = it.next();
            final String val = map.get(key);
            if (StringUtils.hasLength(val))
            {
                return val;
            } 
        }
        return null;
    }

    @Override
    public void purge() throws IOException
    {
        jdbcTemplate.update("DELETE FROM geoip", Collections.emptyMap());
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return ResourceUtil.getLastModified(url);
    }
}
