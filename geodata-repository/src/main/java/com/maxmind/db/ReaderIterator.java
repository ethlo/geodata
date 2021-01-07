package com.maxmind.db;

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
