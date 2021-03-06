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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;

import com.ethlo.geodata.dao.BoundaryDao;
import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.RTreePayload;
import com.ethlo.geodata.model.RawLocation;
import com.github.davidmoten.grumpy.core.Position;
import com.github.davidmoten.guavamini.Lists;
import com.github.davidmoten.rtree2.Entry;
import com.github.davidmoten.rtree2.RTree;
import com.github.davidmoten.rtree2.geometry.Geometries;
import com.github.davidmoten.rtree2.geometry.Geometry;
import com.github.davidmoten.rtree2.geometry.Point;
import com.github.davidmoten.rtree2.geometry.Rectangle;
import com.github.davidmoten.rtree2.geometry.internal.RectangleDouble;
import com.github.davidmoten.rtree2.internal.EntryDefault;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.primitives.Ints;

public class RtreeRepository
{
    private static final Logger logger = LoggerFactory.getLogger(RtreeRepository.class);

    private final BoundaryDao boundaryDao;
    private final RTree<RTreePayload, Geometry> boundaryRTree;
    private RTree<Integer, Point> proximity;

    public RtreeRepository(LocationDao locationDao, BoundaryDao boundaryDao, final Set<Integer> featureCodesIncluded)
    {
        this.boundaryDao = boundaryDao;

        // Load proximity tree
        logger.info("Loading proximity tree");
        this.proximity = RTree.star().create();
        final int batchSize = 20_000;
        final Iterator<RawLocation> iter = locationDao.stream().iterator();
        List<Entry<Integer, Point>> batch;
        do
        {
            final Iterator<RawLocation> filtered = Iterators.filter(iter, l -> featureCodesIncluded.contains(Objects.requireNonNull(l).getMapFeatureId()));
            batch = Lists.newArrayList(Iterators.limit(new ConvertingIterator(filtered), batchSize));
            proximity = proximity.add(batch);
        }
        while (batch.size() == batchSize);
        logger.info("Loaded {} location points", proximity.size());

        // Load boundaries
        boundaryRTree = getBoundaryRTree(locationDao, boundaryDao);
        logger.info("Loaded {} location bounding boxes", boundaryRTree.size());
    }

    private static Rectangle createBounds(final Position from, final double distanceKm)
    {
        // this calculates a pretty accurate bounding box. Depending on the
        // performance you require you wouldn't have to be this accurate because
        // accuracy is enforced later
        Position north = from.predict(distanceKm, 0);
        Position south = from.predict(distanceKm, 180);
        Position east = from.predict(distanceKm, 90);
        Position west = from.predict(distanceKm, 270);

        return Geometries.rectangle(west.getLon(), south.getLat(), east.getLon(), north.getLat());
    }

    private RTree<RTreePayload, Geometry> getBoundaryRTree(final LocationDao locationDao, final BoundaryDao boundaryDao)
    {
        return RTree.create(boundaryDao.stream()
                .filter(entry -> locationDao.get(entry.getId()).isPresent())
                .map(this::envelopeEntry)
                .collect(Collectors.toList()));
    }

    private Entry<RTreePayload, Geometry> envelopeEntry(RTreePayload payload)
    {
        final Envelope env = payload.getEnvelope();
        return EntryDefault.entry(payload, Geometries.rectangleGeographic(env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()));
    }

    public Entry<RTreePayload, Geometry> find(Coordinates coordinates)
    {
        // Point to find
        final Point target = Geometries.pointGeographic(coordinates.getLng(), coordinates.getLat());
        final Iterator<Entry<RTreePayload, Geometry>> iter = boundaryRTree.search(target).iterator();

        // The candidates are the ones that have a matching bounding-box, but it may still not be a real match
        final List<Entry<RTreePayload, Geometry>> candidates = Lists.newArrayList(iter);

        // Sort by area, as we would like to find the smallest one that match
        candidates.sort(Comparator.comparingDouble(o -> o.value().getArea()));

        // Loop through candidates from smallest to largest and check actual polygon
        for (Entry<RTreePayload, Geometry> candidate : candidates)
        {
            if (isReallyInside(coordinates, candidate.value().getId(), candidate.value().getSubdivideIndex()))
            {
                return candidate;
            }
        }

        return null;
    }

    private boolean isReallyInside(Coordinates coordinate, final int id, final int subdivideIndex)
    {
        final org.locationtech.jts.geom.Point point = new GeometryFactory().createPoint(new Coordinate(coordinate.getLng(), coordinate.getLat()));

        final org.locationtech.jts.geom.Geometry geom = boundaryDao
                .findGeometryById(id, subdivideIndex)
                .orElseGet(() -> boundaryDao.findGeometryById(id, 0).orElseThrow(() -> new IllegalArgumentException("" + id + " - " + subdivideIndex)));
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

    public int size()
    {
        return proximity.size();
    }

    public Map<Integer, Double> getNearest(final Coordinates point, final int maxDistanceInKilometers, final Pageable pageable)
    {
        final int max = Ints.saturatedCast((pageable.getOffset() + pageable.getPageSize()));
        final Position from = Position.create(point.getLat(), point.getLng());
        //final Rectangle bounds = createBounds(from, maxDistanceInKilometers);
        return StreamSupport
                .stream(proximity.nearest(RectangleDouble.create(point.getLng(), point.getLat(), point.getLng(), point.getLat()), maxDistanceInKilometers, max).spliterator(), false)
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .collect(Collectors.toMap(Entry::value,
                        entry -> from.getDistanceToKm(Position.create(entry.geometry().y(), entry.geometry().x())),
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    private static class ConvertingIterator extends AbstractIterator<Entry<Integer, Point>>
    {
        private final Iterator<RawLocation> locations;

        public ConvertingIterator(Iterator<RawLocation> locations)
        {
            this.locations = locations;
        }

        @Override
        protected Entry<Integer, Point> computeNext()
        {
            if (locations.hasNext())
            {
                final RawLocation location = locations.next();

                final double lat = location.getCoordinates().getLat();
                final double lng = location.getCoordinates().getLng();

                return new Entry<>()
                {
                    @Override
                    public Integer value()
                    {
                        return location.getId();
                    }

                    @Override
                    public Point geometry()
                    {
                        return Geometries.point(lng, lat);
                    }
                };
            }
            return endOfData();
        }
    }
}