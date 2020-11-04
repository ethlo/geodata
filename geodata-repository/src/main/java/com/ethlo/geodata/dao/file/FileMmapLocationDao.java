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
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.model.RawLocation;
import com.ethlo.geodata.util.SerializationUtil;
import com.google.common.collect.AbstractIterator;

@Repository
public class FileMmapLocationDao extends BaseMmapDao implements LocationDao
{
    public FileMmapLocationDao(@Value("${geodata.base-path}") final Path basePath)
    {
        super(basePath, "locations");
    }

    @Override
    public int load()
    {
        return super.load();
    }

    @Override
    public CloseableIterator<RawLocation> iterator()
    {
        final DataInputStream in = super.getInputStream(0);

        return SerializationUtil.wrapClosable(new AbstractIterator<>()
        {
            @Override
            protected RawLocation computeNext()
            {
                try
                {
                    final RawLocation l = new RawLocation();
                    l.read(in);
                    return l;
                }
                catch (EOFException ignored)
                {
                    return endOfData();
                }
                catch (IOException exc)
                {
                    throw new UncheckedIOException(exc);
                }
            }
        }, in);
    }

    @Override
    public Optional<RawLocation> get(final int id)
    {
        return Optional.ofNullable(super.getOffset(id)).map(this::readDataAtOffset);
    }

    @Override
    public int size()
    {
        return super.size();
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