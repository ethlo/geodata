package com.ethlo.geodata.dao.file;

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
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;

import com.ethlo.geodata.boundaries.WkbDataReader;
import com.ethlo.geodata.io.JsonIoReader;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.RTreePayload;
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
import com.google.common.primitives.Ints;

public class RtreeRepository
{
    public static final String BOUNDARIES_FILENAME = "boundaries.wkb";
    public static final String ENVELOPE_FILENAME = "envelopes.json";
    private static final Logger logger = LoggerFactory.getLogger(RtreeRepository.class);
    private RTree<RTreePayload, Geometry> tree;
    private WkbDataReader reader;
    private File envelopeFile;

    public RtreeRepository(File file)
    {
        this.envelopeFile = new File(file.getParentFile(), RtreeRepository.ENVELOPE_FILENAME);
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
                if (!entries.hasNext())
                {
                    return endOfData();
                }

                final RTreePayload e = entries.next();
                return entry(e);
            }
        }));
    }

    private static <T> Iterable<T> once(final Iterator<T> source)
    {
        return new Iterable<T>()
        {
            private final AtomicBoolean exhausted = new AtomicBoolean();

            @Override
            @Nonnull
            public Iterator<T> iterator()
            {
                Preconditions.checkState(!exhausted.getAndSet(true));
                return source;
            }
        };
    }

    private Entry<RTreePayload, Geometry> entry(RTreePayload payload)
    {
        final Envelope env = payload.getEnvelope();
        return EntryDefault.entry(payload, Geometries.rectangle(env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()));
    }

    public Long find(Coordinates coordinates)
    {
        // Point to find
        final Point point = Geometries.point(coordinates.getLng(), coordinates.getLat());
        final Iterator<Entry<RTreePayload, Geometry>> iter = tree.search(point).toBlocking().getIterator();
        final org.locationtech.jts.geom.Point target = new GeometryFactory().createPoint(new Coordinate(coordinates.getLng(), coordinates.getLat()));

        // The candidates are the ones that have a matching bounding-box, but it may still not be a real match
        final List<Entry<RTreePayload, Geometry>> candidates = Lists.newArrayList(iter);

        // Sort by area, as we would like to find the smallest one that match
        candidates.sort(Comparator.comparingDouble(o -> o.value().getArea()));

        // Loop through candidates from smallest to largest and check actual polygon
        final WKBReader r = new WKBReader();
        for (Entry<RTreePayload, Geometry> candidate : candidates)
        {
            final byte[] wkb = reader.read(candidate.value().getId());
            try
            {
                final org.locationtech.jts.geom.Geometry geometry = r.read(wkb);
                if (geometry.contains(target))
                {
                    return candidate.value().getId();
                }
            }
            catch (TopologyException exc)
            {
                logger.warn("Cannot process geometry for location {}: {}", candidate.value().getId(), exc.getMessage());
                logger.debug(exc.getMessage(), exc);
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
        final int max = Ints.saturatedCast(pageable.getOffset() + pageable.getPageSize());
        final Iterator<Entry<RTreePayload, Geometry>> e = tree.nearest(Geometries.point(point.getLng(), point.getLat()), maxDistanceInKilometers, max).toBlocking().getIterator();
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
        Iterators.advance(iter, Ints.saturatedCast(pageable.getOffset()));
        return iter;
    }
}