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

/*
@Component
public class FileGeonamesBoundaryImporter extends BaseCsvFileImporter<Geometry>
{
    private static final Logger logger = LoggerFactory.getLogger(FileGeonamesBoundaryImporter.class);
    private final WKTReader reader;

    @Value("${geodata.geonames.source.boundaries}")
    private String geoNamesBoundaryUrl;

    public FileGeonamesBoundaryImporter(final Path basePath, final String type, final String url, final List header, final boolean isTsv, final int skipLines)
    {
        super(basePath, type, url, header, isTsv, skipLines);

        this.reader = new WKTReader();
    }

    @Override
    public Object processLine(final Map<String, String> entry)
    {
        //////////////////////////////////////////////77
        final File envelopeFile = getEnvelopeFile();
        try (final WkbDataWriter out = new WkbDataWriter(getFile());
             final JsonIoWriter<Map> envOut = new JsonIoWriter<>(envelopeFile, Map.class))
        {

            final WKBWriter writer = new WKBWriter();
            final Entry<Date, File> boundaryFile = fetchResource(DataType.BOUNDARY, geoNamesBoundaryUrl);
            //logger.info("Counting lines of {}", boundaryFile.getValue());
            //final long total = 1; // TODO: IoUtils.lineCount(boundaryFile.getValue());
            //final ProgressListener prg = new ProgressListener(l->publish(new DataLoadedEvent(this, DataType.BOUNDARY, Operation.IMPORT, l, total)));
            final GeonamesBoundaryImporter importer = new GeonamesBoundaryImporter(boundaryFile.getValue());
            ////////////////////////////////////////////////7

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
            throw new UncheckedIOException(new IOException(exc));
        }

        return null;
    }

    private File getEnvelopeFile()
    {
        return new File(getFile().getParentFile(), RtreeRepository.ENVELOPE_FILENAME);
    }
}
*/