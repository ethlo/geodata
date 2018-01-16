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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

import com.ethlo.geodata.DataLoadedEvent;
import com.ethlo.geodata.IoUtils;
import com.ethlo.geodata.ProgressListener;
import com.ethlo.geodata.boundaries.WkbDataWriter;
import com.ethlo.geodata.importer.DataType;
import com.ethlo.geodata.importer.GeonamesBoundaryImporter;
import com.ethlo.geodata.importer.Operation;
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
    
    public FileGeonamesBoundaryImporter(ApplicationEventPublisher publisher)
    {
        super(publisher, BOUNDARIES_FILENAME);
    }
    
    @SuppressWarnings("rawtypes") 
    @Override
    public long importData() throws IOException
    {
        final File envelopeFile = getEnvelopeFile();
        try (final WkbDataWriter out = new WkbDataWriter(getFile()); 
             final JsonIoWriter<Map> envOut = new JsonIoWriter<>(envelopeFile, Map.class))
        {
            final WKTReader reader = new WKTReader();
            final WKBWriter writer = new WKBWriter();
            final Entry<Date, File> boundaryFile = fetchResource(DataType.BOUNDARY, geoNamesBoundaryUrl);
            final long total = IoUtils.lineCount(boundaryFile.getValue());
            final ProgressListener prg = new ProgressListener(l->publish(new DataLoadedEvent(this, DataType.BOUNDARY, Operation.IMPORT, l, total)));
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
                    
                    prg.update();
                }
                catch (ParseException exc)
                {
                    throw new DataAccessResourceFailureException(exc.getMessage(), exc);
                }
            });
            
            publish(new DataLoadedEvent(this, DataType.BOUNDARY, Operation.IMPORT, total, total));
            return total;
        }
    }

    private File getEnvelopeFile()
    {
        return new File(getFile().getParentFile(), ENVELOPE_FILENAME);
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return getLastModified(geoNamesBoundaryUrl);
    }

    @Override
    public void purge() throws IOException
    {
        super.delete();
    }

    @Override
    protected List<File> getFiles()
    {
        return Arrays.asList(getEnvelopeFile(), super.getFile()); 
    }
}
