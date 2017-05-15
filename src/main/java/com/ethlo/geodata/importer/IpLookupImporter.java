package com.ethlo.geodata.importer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.apache.commons.net.util.SubnetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.http.ResourceUtil;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.base.Throwables;
import com.google.common.net.InetAddresses;

@Service
public class IpLookupImporter
{
    private static final Logger logger = LoggerFactory.getLogger(IpLookupImporter.class);

    private NamedParameterJdbcTemplate jdbcTemplate;
    
    @Value("${geodata.geolite2.source}")
    private String url;

    @Autowired
    public void setDataSource(DataSource dataSource)
    {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    public void importIpRanges() throws IOException
    {
        final String sql = "INSERT INTO geo_ip(geoname_id, geoname_country_id, first, last, lat, lng) VALUES (:geoname_id, :geoname_country_id, :first, :last, :lat, :lng)";
        final AtomicInteger count = new AtomicInteger(0);
        processFile(ResourceUtil.fetchZipEntry("ipData", url, "GeoLite2-City-Blocks-IPv4.csv"), entry->
        {
            final String strGeoNameId = findMapValue(entry, "geoname_id", "represented_country_geoname_id", "registered_country_geoname_id");
            final String strGeoNameCountryId = findMapValue(entry, "represented_country_geoname_id", "registered_country_geoname_id");
            final Long geonameId = strGeoNameId != null ? Long.parseLong(strGeoNameId) : null;
            final Long geonameCountryId = strGeoNameCountryId != null ? Long.parseLong(strGeoNameCountryId) : null;
            if (geonameId != null)
            {
                final SubnetUtils u = new SubnetUtils(entry.get("network"));
                final int lower = InetAddresses.coerceToInteger(InetAddresses.forString(u.getInfo().getLowAddress()));
                final int upper = InetAddresses.coerceToInteger(InetAddresses.forString(u.getInfo().getHighAddress()));
                final Double lat = parseDouble(entry.get("lat"));
                final Double lng = parseDouble(entry.get("lng"));
                final Map<String, Object> paramMap = new HashMap<>(5);
                paramMap.put("geoname_id", geonameId);
                paramMap.put("geoname_country_id", geonameCountryId);
                paramMap.put("first", lower);
                paramMap.put("last", upper);
                paramMap.put("lat", lat);
                paramMap.put("lng", lng);
                
                final int affected = jdbcTemplate.update(sql, paramMap);
                Assert.isTrue(affected == 1, "Should insert 1 row");

                if (count.getAndIncrement() % 1000 == 0)
                {
                    logger.info("Processed {}", count.get());
                }
            }
        });
        
        final int rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM geo_ip", Collections.emptyMap(), Integer.class);
        logger.info("rowCount: {}", rowCount);
        
        dumpInsertStatements();
    }

    private void dumpInsertStatements() throws IOException
    {
        final String fileName = File.createTempFile("geoip_", ".sql").getAbsolutePath();
        logger.info("Dumping to {}", fileName);
        final String dumpSqlStatement = "SCRIPT TABLE GEO_IP";
        
        try (final Writer out = new BufferedWriter(new FileWriter(fileName)))
        {
            jdbcTemplate.execute(dumpSqlStatement, new PreparedStatementCallback<Void>()
            {
                @Override
                public Void doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException
                {
                    final ResultSet rs = ps.executeQuery();
                    while (rs.next())
                    {
                        final String stmt = rs.getString(1);
                        if (stmt.startsWith("INSERT"))
                        {
                            try
                            {
                                out.write(stmt);
                                out.write("\n");
                            }
                            catch (IOException exc)
                            {
                                throw Throwables.propagate(exc);
                            }
                        }
                    }
                    return null;
                }
            });
        };
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

    private void processFile(File csvFile, Consumer<Map<String, String>> sink) throws IOException
    {
        final CsvMapper csvMapper = new CsvMapper();
        final CsvSchema schema = CsvSchema.emptySchema().withHeader(); // use first row as header; otherwise defaults are fine
        try (final BufferedReader reader = new BufferedReader(new FileReader(csvFile)))
        {
            final MappingIterator<Map<String,String>> it = csvMapper.readerFor(Map.class)
               .with(schema)
               .readValues(reader);
            while (it.hasNext())
            {
                sink.accept(it.next());
            }
        }
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
}
