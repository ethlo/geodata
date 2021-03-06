package com.ethlo.geodata.importer;

/*-
 * #%L
 * geodata-importer
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

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;

import org.springframework.util.FastByteArrayOutputStream;

import com.ethlo.geodata.io.RecordType;
import com.ethlo.geodata.model.IntIdentifiable;
import com.ethlo.geodata.util.CompressionUtil;

public abstract class BinaryIndexedFileWriter<T extends IntIdentifiable>
{
    private final Path directory;
    private final String alias;
    private final boolean compress;

    protected BinaryIndexedFileWriter(final Path directory, final String alias, final boolean compress)
    {
        this.directory = directory;
        this.alias = alias;
        this.compress = compress;
    }

    public int writeData(Iterator<T> data) throws IOException
    {
        int count = 0;

        final Path indexPath = directory.resolve(alias + ".index");
        final Path dataPath = directory.resolve(alias + ".data");

        try (final OutputStream uncompressedIndex = Files.newOutputStream(indexPath))
        {
            // Write placeholder
            uncompressedIndex.write(new byte[4]);
        }

        try (final DataOutputStream indexOut = new DataOutputStream(CompressionUtil.compress(new BufferedOutputStream(Files.newOutputStream(indexPath, StandardOpenOption.APPEND))));
             final DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(dataPath))))
        {
            int pos = 0;
            while (data.hasNext())
            {
                final T d = data.next();

                // Write raw data
                final long startPos = pos;

                final boolean doCompress = compress && d.isCompressible();
                final byte[] compressed = writeData(d, doCompress);
                dataOutputStream.writeByte(doCompress ? RecordType.LZMA2_PREFIXED_LENGTH.getId() : RecordType.UNCOMPRESSED_PREFIXED_LENGTH.getId());
                dataOutputStream.writeInt(compressed.length);
                dataOutputStream.write(compressed);

                // Write index in the raw file. Note that we can have multiple repeating IDs!
                indexOut.writeInt(d.getId());
                indexOut.writeInt((int) startPos);

                count++;
                pos += (1 + 4 + compressed.length);
            }
        }

        // Replace count
        try (final RandomAccessFile raf = new RandomAccessFile(indexPath.toFile(), "rw"))
        {
            raf.seek(0);
            raf.writeInt(count);
        }

        return count;
    }

    private byte[] writeData(final T d, final boolean compress) throws IOException
    {
        try (final FastByteArrayOutputStream out = new FastByteArrayOutputStream(10_240))
        {
            final DataOutputStream compOut = new DataOutputStream(compress ? CompressionUtil.compress(out) : out);
            this.write(d, compOut);
            compOut.close();
            return out.toByteArray();
        }
    }

    protected abstract void write(T data, DataOutputStream out) throws IOException;
}
