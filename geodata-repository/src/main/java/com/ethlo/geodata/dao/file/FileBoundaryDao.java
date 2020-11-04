package com.ethlo.geodata.dao.file;

/*-
 * #%L
 * geodata-common
 * %%
 * Copyright (C) 2017 - 2020 Morten Haraldsen (ethlo)
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

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.BoundaryDao;
import com.ethlo.geodata.io.BinaryBoundaryEncoder;
import com.ethlo.geodata.model.BoundaryData;
import com.ethlo.geodata.model.BoundaryMetadata;
import com.ethlo.geodata.model.RTreePayload;
import com.ethlo.geodata.util.SerializationUtil;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;

@Repository
public class FileBoundaryDao extends BaseMmapDao implements BoundaryDao
{
    public FileBoundaryDao(@Value("${geodata.base-path}") final Path basePath)
    {
        super(basePath, "boundaries");
    }

    @Override
    public Iterator<RTreePayload> entries()
    {
        super.load();

        final DataInputStream in = super.getInputStream(0);

        return SerializationUtil.wrapClosable(Iterators.filter(new AbstractIterator<>()
        {
            @Override
            protected RTreePayload computeNext()
            {
                try
                {
                    final BoundaryMetadata boundaryMetadata = BinaryBoundaryEncoder.readBoundaryMetadata(in);
                    return new RTreePayload(boundaryMetadata.getId(), boundaryMetadata.getSubDivideIndex(), boundaryMetadata.getArea(), boundaryMetadata.getMbr());
                }
                catch (EOFException exc)
                {
                    return endOfData();
                }
                catch (IOException exc)
                {
                    throw new UncheckedIOException(exc);
                }
            }
        }, e -> e.getSubdivideIndex() > 0), in);
    }

    @Override
    public Optional<Geometry> findGeometryById(final int id)
    {
        return Optional.ofNullable(getOffset(id))
                .map(this::getInputStream)
                .map(BinaryBoundaryEncoder::readGeometry)
                .map(BoundaryData::getGeometry);
    }

    @Override
    public Optional<Geometry> findGeometryById(final int id, final int subdivideIndex)
    {
        // TODO: We need to load the one with the right sub index
        return Optional.ofNullable(getOffset(id))
                .map(this::getInputStream)
                .map(BinaryBoundaryEncoder::readGeometry)
                .map(BoundaryData::getGeometry);
    }
}
