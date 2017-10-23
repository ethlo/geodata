package com.ethlo.geodata.importer.file;

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

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

import com.ethlo.geodata.boundaries.WkbDataWriter;
import com.ethlo.geodata.importer.GeonamesBoundaryImporter;
import com.ethlo.geodata.util.ResourceUtil;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBWriter;
import com.vividsolutions.jts.io.WKTReader;

@Component
public class FileGeonamesBoundaryImporter extends FilePersistentImporter
{
    public static final String BOUNDARIES_FILENAME = "boundaries.wkb";
    public static final String ENVELOPE_FILENAME = "envelopes.json";
    
    @Value("${geodata.geonames.source.boundaries}")
    private String geoNamesBoundaryUrl;
    
    public FileGeonamesBoundaryImporter()
    {
        super(BOUNDARIES_FILENAME);
    }
    
    @Override
    public void importData() throws IOException
    {
        final File envelopeFile = new File(getFile().getParentFile(), ENVELOPE_FILENAME);
        try (final WkbDataWriter out = new WkbDataWriter(getFile()); @SuppressWarnings("rawtypes") final JsonIoWriter<Map> envOut = new JsonIoWriter<>(envelopeFile, Map.class))
        {
            final WKTReader reader = new WKTReader();
            final WKBWriter writer = new WKBWriter();
            final Entry<Date, File> boundaryFile = ResourceUtil.fetchResource("geonames_boundary", geoNamesBoundaryUrl);
            final GeonamesBoundaryImporter importer = new GeonamesBoundaryImporter(boundaryFile.getValue());
            importer.processFile(entry->
            {
                try
                {
                    final Geometry geometry = reader.read(entry.get("poly"));
                    
                    // Write the MBR
                    final Envelope env = geometry.getEnvelopeInternal();
                    final Map<String, Object> map = new TreeMap<>();
                    map.put("id", entry.get("id"));
                    map.put("minX", env.getMinX());
                    map.put("minY", env.getMinY());
                    map.put("maxX", env.getMaxX());
                    map.put("maxY", env.getMaxY());
                    envOut.write(map);
                    
                    // Write full geometry in WKB format
                    out.write(Long.parseLong(entry.get("id")), writer.write(geometry));
                }
                catch (ParseException exc)
                {
                    throw new DataAccessResourceFailureException(exc.getMessage(), exc);
                }
            });
        }
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return ResourceUtil.getLastModified(geoNamesBoundaryUrl);
    }

    @Override
    public void purge() throws IOException
    {
        super.delete();
    }
}
