package com.ethlo.geodata.importer;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;

import com.ethlo.geodata.model.IntIdentifiable;
import com.ethlo.geodata.util.CompressionUtil;
import com.google.common.io.CountingOutputStream;

@SuppressWarnings("UnstableApiUsage")
public abstract class BinaryIndexedFileWriter<T extends IntIdentifiable>
{
    private final Path directory;
    private final String alias;

    protected BinaryIndexedFileWriter(final Path directory, final String alias)
    {
        this.directory = directory;
        this.alias = alias;
    }

    protected int writeData(Iterator<T> data) throws IOException
    {
        int count = 0;

        final Path indexPath = directory.resolve(alias + ".index");
        final Path uncompressedDataPath = directory.resolve(alias + ".data");

        try (final OutputStream uncompressedIndex = Files.newOutputStream(indexPath))
        {
            // Write placeholder
            uncompressedIndex.write(new byte[4]);
        }

        try (final DataOutputStream indexOut = new DataOutputStream(CompressionUtil.compress(new BufferedOutputStream(Files.newOutputStream(indexPath, StandardOpenOption.APPEND))));
             final CountingOutputStream countingBytes = new CountingOutputStream(new BufferedOutputStream(Files.newOutputStream(uncompressedDataPath)));
             final DataOutputStream uncompressedOut = new DataOutputStream(countingBytes))
        {
            while (data.hasNext())
            {
                final T d = data.next();

                // Write raw data
                final long startPos = countingBytes.getCount();
                this.write(d, uncompressedOut);

                // Write index in the raw file
                indexOut.writeInt(d.getId());
                indexOut.writeInt((int) startPos);

                count++;
            }
        }

        // Replace count
        try (final RandomAccessFile raf = new RandomAccessFile(indexPath.toFile(), "rw");)
        {
            raf.seek(0);
            raf.writeInt(count);
        }

        return count;
    }

    protected abstract void write(T data, DataOutputStream out) throws IOException;
}
