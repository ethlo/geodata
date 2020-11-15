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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

import com.ethlo.geodata.DataType;
import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.model.RawLocation;
import com.google.common.collect.Iterators;

public class FileLocationDao extends BaseMmapDao implements LocationDao
{
    public FileLocationDao(final Path basePath)
    {
        super(basePath, false, DataType.LOCATIONS);
    }

    @Override
    public int load()
    {
        return super.load();
    }

    @Override
    public Iterator<RawLocation> iterator()
    {
        return Iterators.transform(super.rawIterator(), e ->
        {
            try (final DataInputStream in = e.getValue())
            {
                final RawLocation l = new RawLocation();
                l.read(in);
                return l;
            }
            catch (IOException exc)
            {
                throw new UncheckedIOException(exc);
            }
        });
    }

    @Override
    public Optional<RawLocation> get(final int id)
    {
        return Optional.ofNullable(super.getOffset(id)).map(this::readDataAtOffset);
    }

    private RawLocation readDataAtOffset(final Integer offset)
    {
        final DataInputStream in = getInputStream(offset);
        final RawLocation l = new RawLocation();
        try
        {
            l.read(in);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        return l;
    }
}