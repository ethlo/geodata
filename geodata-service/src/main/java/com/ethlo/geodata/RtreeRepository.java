package com.ethlo.geodata;

/*-
 * #%L
 * Geodata service
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;

import com.ethlo.geodata.boundaries.WkbDataReader;
import com.ethlo.geodata.importer.file.FileGeonamesBoundaryImporter;
import com.ethlo.geodata.importer.file.JsonIoReader;
import com.ethlo.geodata.model.Coordinates;
import com.github.davidmoten.guavamini.Lists;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Geometry;
import com.github.davidmoten.rtree.geometry.Point;
import com.github.davidmoten.rtree.internal.EntryDefault;
import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.TopologyException;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;

public class RtreeRepository
{
    private static final Logger logger = LoggerFactory.getLogger(RtreeRepository.class);
    
    private RTree<RTreePayload, Geometry> tree;
    private WkbDataReader reader;
    private File envelopeFile;
    
    public RtreeRepository(File file)
    {
        this.envelopeFile = new File(file.getParentFile(), FileGeonamesBoundaryImporter.ENVELOPE_FILENAME);
        this.reader = new WkbDataReader(file);
        this.tree = RTree.create();
    }
    
    public RtreeRepository(Iterator<RTreePayload> entries)
    {
        final RTree<RTreePayload, Geometry> b = RTree.create();
        this.tree = b.add(once(new AbstractIterator<Entry<RTreePayload, Geometry>>()
        {
            @Override
            protected Entry<RTreePayload, Geometry> computeNext()
            {
                if (! entries.hasNext())
                {
                    return endOfData();
                }
                
                final RTreePayload e = entries.next();
                return entry(e);
            }
        }));
    }

    private Entry<RTreePayload, Geometry> entry(RTreePayload payload)
    {
        final Envelope env = payload.getEnvelope();
        return EntryDefault.entry(payload, Geometries.rectangle(env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()));
    }
    
    private static <T> Iterable<T> once(final Iterator<T> source)
    {
        return new Iterable<T>()
        {
            private AtomicBoolean exhausted = new AtomicBoolean();
            @Override
            public Iterator<T> iterator()
            {
                Preconditions.checkState(!exhausted.getAndSet(true));
                return source;
            }
        };
    }

    public Long find(Coordinates coordinates)
    {
        final Point point = Geometries.point(coordinates.getLng(), coordinates.getLat());
        final Iterator<Entry<RTreePayload, Geometry>> iter = tree.search(point).toBlocking().getIterator();
        final com.vividsolutions.jts.geom.Point target = new GeometryFactory().createPoint(new Coordinate(coordinates.getLng(), coordinates.getLat()));
        final List<Entry<RTreePayload, Geometry>> candidates = Lists.newArrayList(iter);
        Collections.sort(candidates, (o1, o2)->Double.valueOf(o1.value().getArea()).compareTo(o2.value().getArea()));
        final WKBReader r = new WKBReader();
        for (Entry<RTreePayload, Geometry> candidate : candidates)
        {
            final byte[] wkb = reader.read(candidate.value().getId());
            try
            {
                final com.vividsolutions.jts.geom.Geometry geometry = r.read(wkb);
                if (geometry.contains(target))
                {
                    return candidate.value().getId();
                }
            }
            catch (TopologyException exc)
            {
                logger.warn("Cannot process geometry for location {}: {}", candidate.value().getId(), exc.getMessage());
                logger.debug("{}", exc);
            }
            catch (ParseException exc)
            {
                throw new DataAccessResourceFailureException(exc.getMessage(), exc);
            }
        }
        return null;
    }

    public RtreeRepository add(RTreePayload payload)
    {
        tree = tree.add(entry(payload));
        return this;
    }

    public int size()
    {
        return tree.size();
    }

    public WkbDataReader getReader()
    {
        return reader;
    }
    
    @SuppressWarnings("rawtypes")
    public JsonIoReader<Map> getEnvelopeReader()
    {
        return new JsonIoReader<>(envelopeFile, Map.class);
    }

    public Iterator<Long> findNear(Coordinates point, double maxDistanceInKilometers, Pageable pageable)
    {
        final Iterator<Entry<RTreePayload, Geometry>> e = tree.nearest(Geometries.point(point.getLng(), point.getLat()), maxDistanceInKilometers, pageable.getOffset() + pageable.getPageSize()).toBlocking().getIterator();
        final Iterator<Long> iter = new AbstractIterator<Long>()
        {
            @Override
            protected Long computeNext()
            {
                if (e.hasNext())
                {
                    return e.next().value().getId();
                }
                return endOfData();
            }
        };
        Iterators.advance(iter, pageable.getOffset());
        return iter;
    }
}
