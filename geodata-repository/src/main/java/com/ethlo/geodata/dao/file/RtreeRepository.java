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

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;

import com.ethlo.geodata.dao.BoundaryDao;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.RTreePayload;
import com.github.davidmoten.guavamini.Lists;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Geometry;
import com.github.davidmoten.rtree.geometry.Point;
import com.github.davidmoten.rtree.internal.EntryDefault;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.primitives.Ints;

public class RtreeRepository
{
    private static final Logger logger = LoggerFactory.getLogger(RtreeRepository.class);
    private final BoundaryDao boundaryDao;
    private RTree<RTreePayload, Geometry> tree;

    public RtreeRepository(BoundaryDao boundaryDao)
    {
        this.boundaryDao = boundaryDao;

        this.tree = RTree.create();
        final Iterator<RTreePayload> entries = boundaryDao.entries();
        while (entries.hasNext())
        {
            final RTreePayload entry = entries.next();
            tree = tree.add(entry(entry));
        }
    }

    private Entry<RTreePayload, Geometry> entry(RTreePayload payload)
    {
        final Envelope env = payload.getEnvelope();
        return EntryDefault.entry(payload, Geometries.rectangle(env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()));
    }

    public Integer find(Coordinates coordinates)
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
        for (Entry<RTreePayload, Geometry> candidate : candidates)
        {
            if (isReallyInside(coordinates.getLat(), coordinates.getLng(), candidate.value().getId()))
            {
                return candidate.value().getId();
            }
        }

        return null;
    }

    private boolean isReallyInside(double lat, double lng, final int id)
    {
        final org.locationtech.jts.geom.Point point = new GeometryFactory().createPoint(new Coordinate(lng, lat));

        final org.locationtech.jts.geom.Geometry geom = boundaryDao.findGeometryById(id).orElseThrow();
        if (geom instanceof GeometryCollection)
        {
            final GeometryCollection coll = (GeometryCollection) geom;
            for (int num = 0; num < coll.getNumGeometries(); num++)
            {
                final org.locationtech.jts.geom.Geometry geomElem = coll.getGeometryN(num);
                final boolean actualInside = geomElem.contains(point);
                if (actualInside)
                {
                    return true;
                }
            }
            return false;
        }
        else
        {
            return geom.contains(point);
        }
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

    public Iterator<Integer> findNear(Coordinates point, double maxDistanceInKilometers, Pageable pageable)
    {
        final int max = Ints.saturatedCast(pageable.getOffset() + pageable.getPageSize());
        final Iterator<Entry<RTreePayload, Geometry>> e = tree.nearest(Geometries.point(point.getLng(), point.getLat()), maxDistanceInKilometers, max).toBlocking().getIterator();
        final Iterator<Integer> iter = new AbstractIterator<>()
        {
            @Override
            protected Integer computeNext()
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

    public Map<Integer, Double> getNearest(final Coordinates point, final int maxDistanceInKilometers, final Pageable pageable)
    {
        final int max = Ints.saturatedCast(pageable.getOffset() + pageable.getPageSize());
        final Point target = Geometries.point(point.getLng(), point.getLat());
        final Iterator<Entry<RTreePayload, Geometry>> entryIterator = tree.nearest(target, maxDistanceInKilometers, max).toBlocking().getIterator();

        final Map<Integer, Double> idAndDistance = new LinkedHashMap<>();
        while (entryIterator.hasNext())
        {
            final Entry<RTreePayload, Geometry> entry = entryIterator.next();
            final double distance = entry.geometry().distance(target);
            idAndDistance.put(entry.value().getId(), distance);
        }
        return idAndDistance;
    }
}