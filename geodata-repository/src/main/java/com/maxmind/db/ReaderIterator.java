package com.maxmind.db;

/*-
 * #%L
 * geodata-repository
 * %%
 * Copyright (C) 2017 - 2021 Morten Haraldsen (ethlo)
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

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.util.ReflectionUtils;

import com.ethlo.geodata.dao.file.FileIpDao;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ReaderIterator
{
    private static final int DATA_SECTION_SEPARATOR_SIZE = 16;

    private final Method bufferHolderMethod;
    private final Reader reader;
    private final Metadata metadata;
    private final ByteBuffer buffer;
    private final Decoder decoder;

    public ReaderIterator(Reader reader)
    {
        this.reader = reader;
        this.metadata = reader.getMetadata();
        this.bufferHolderMethod = ReflectionUtils.findMethod(Reader.class, "getBufferHolder");
        Objects.requireNonNull(this.bufferHolderMethod, "Could not find buffer holder method").setAccessible(true);

        this.buffer = getBuffer();
        this.decoder = new Decoder(NoCache.getInstance(), buffer, metadata.getSearchTreeSize() + DATA_SECTION_SEPARATOR_SIZE);
    }

    private ByteBuffer getBuffer()
    {
        final BufferHolder holder = (BufferHolder) ReflectionUtils.invokeMethod(bufferHolderMethod, reader);
        return Objects.requireNonNull(holder, "holder must not be null").get();
    }

    public void iterateSearchTree(ReaderIterationCallback callback) throws IOException
    {
        final int maxDepth = metadata.getIpVersion() == 4 ? 32 : 128;
        iterateSearchTree(buffer, 0, 0, 1, maxDepth, callback);
    }

    private void iterateSearchTree(ByteBuffer buffer, int nodeNumber, long ipNum, int depth, int maxDepth, ReaderIterationCallback callback) throws IOException
    {
        for (int i = 0; i < 2; i++)
        {
            int value = readNode(buffer, nodeNumber, i);

            // We ignore empty branches of the search tree
            if (value == metadata.getNodeCount())
            {
                continue;
            }

            if (i == 1)
            {
                ipNum = ipNum | (1 << (maxDepth - depth));
            }

            if (value <= this.metadata.getNodeCount())
            {
                iterateSearchTree(buffer, value, ipNum, depth + 1, maxDepth, callback);
            }
            else
            {
                final int resolved = (value - this.metadata.getNodeCount()) + this.metadata.getSearchTreeSize();
                final FileIpDao.LookupResult data = decoder.decode(resolved, FileIpDao.LookupResult.class);

                final Optional<Integer> id = data.getMostSpecificId();
                if (id.isEmpty())
                {
                    System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(decoder.decode(resolved, Map.class)));
                    System.out.println();
                }

                callback.onData(data);
            }
        }
    }

    private int readNode(ByteBuffer buffer, int nodeNumber, int index)
            throws InvalidDatabaseException
    {
        int baseOffset = nodeNumber * this.metadata.getNodeByteSize();

        switch (this.metadata.getRecordSize())
        {
            case 24:
                buffer.position(baseOffset + index * 3);
                return Decoder.decodeInteger(buffer, 0, 3);
            case 28:
                int middle = buffer.get(baseOffset + 3);

                if (index == 0)
                {
                    middle = (0xF0 & middle) >>> 4;
                }
                else
                {
                    middle = 0x0F & middle;
                }
                buffer.position(baseOffset + index * 4);
                return Decoder.decodeInteger(buffer, middle, 3);
            case 32:
                buffer.position(baseOffset + index * 4);
                return Decoder.decodeInteger(buffer, 0, 4);
            default:
                throw new InvalidDatabaseException("Unknown record size: "
                        + this.metadata.getRecordSize());
        }
    }

    public interface ReaderIterationCallback
    {
        void onData(FileIpDao.LookupResult data);
    }
}
