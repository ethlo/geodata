package com.ethlo.geodata.importer.file;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.net.util.SubnetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.DataLoadedEvent;
import com.ethlo.geodata.IoUtils;
import com.ethlo.geodata.ProgressListener;
import com.ethlo.geodata.importer.DataType;
import com.ethlo.geodata.importer.IpLookupImporter;
import com.ethlo.geodata.importer.Operation;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;

@Component
public class FileIpLookupImporter extends FilePersistentImporter
{
    public static final String FILENAME = "geoip.json";
    private static final Logger logger = LoggerFactory.getLogger(FileIpLookupImporter.class);
    @Value("${geodata.geolite2.source}")
    private String url;

    public FileIpLookupImporter(ApplicationEventPublisher publisher)
    {
        super(publisher, FILENAME);
    }

    @Override
    public long importData() throws IOException
    {
        final Map.Entry<Date, File> ipDataFile = super.fetchResource(DataType.IP, url);
        final AtomicInteger count = new AtomicInteger(0);

        final File csvFile = ipDataFile.getValue();
        final long total = IoUtils.lineCount(csvFile);
        final ProgressListener prg = new ProgressListener(l -> publish(new DataLoadedEvent(this, DataType.IP, Operation.IMPORT, l, total)));

        final IpLookupImporter ipLookupImporter = new IpLookupImporter(csvFile);

        final JsonFactory f = new JsonFactory();
        f.enable(JsonGenerator.Feature.ESCAPE_NON_ASCII);
        f.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        final ObjectMapper mapper = new ObjectMapper(f);

        final byte newLine = (byte) "\n".charAt(0);

        logger.info("Writing IP data to file {}", getFile().getAbsolutePath());
        try (final OutputStream out = new BufferedOutputStream(new FileOutputStream(getFile())))
        {
            ipLookupImporter.processFile(entry ->
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
                    final Map<String, Object> paramMap = new HashMap<>(5);
                    paramMap.put("geoname_id", geonameId);
                    paramMap.put("geoname_country_id", geonameCountryId);
                    paramMap.put("first", lower);
                    paramMap.put("last", upper);

                    try
                    {
                        mapper.writeValue(out, paramMap);
                        out.write(newLine);
                    }
                    catch (IOException exc)
                    {
                        throw new DataAccessResourceFailureException(exc.getMessage(), exc);
                    }
                }

                if (count.get() % 100_000 == 0)
                {
                    logger.info("Processed {}", count.get());
                }

                count.getAndIncrement();

                prg.update();
            });
        }

        return total;
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
        super.delete();
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return getLastModified(url);
    }

    @Override
    protected List<File> getFiles()
    {
        return Arrays.asList(getFile());
    }
}
