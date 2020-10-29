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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.HierarchyDao;
import com.ethlo.geodata.io.IntIntMapSerializer;
import com.ethlo.geodata.util.CompressionUtil;

@Repository
public class FileHierarchyDao implements HierarchyDao
{
    public static final String HIERARCHY_DATA = "hierarchy.data";
    final IntIntMapSerializer serializer = new IntIntMapSerializer();
    private final Path basePath;

    public FileHierarchyDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.basePath = basePath;
    }

    @Override
    public void save(final Map<Integer, Integer> childToParent)
    {
        final Path tmpFile = basePath.resolve("hierarchy.tmp");
        final Path filePath = basePath.resolve(HIERARCHY_DATA);
        try (final OutputStream out = CompressionUtil.compress(new BufferedOutputStream(Files.newOutputStream(tmpFile))))
        {
            serializer.write(childToParent, out);
            Files.move(tmpFile, filePath, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        } finally
        {
            try
            {
                Files.deleteIfExists(tmpFile);
            }
            catch (IOException ignored)
            {

            }
        }
    }

    @Override
    public Map<Integer, Integer> load()
    {
        final Path filePath = basePath.resolve(HIERARCHY_DATA);
        try (final InputStream in = CompressionUtil.decompress(new BufferedInputStream(Files.newInputStream(filePath))))
        {
            return serializer.read(in);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
