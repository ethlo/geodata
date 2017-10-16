package com.ethlo.geodata.boundaries;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.util.CloseableIterator;

import com.google.common.collect.AbstractIterator;

public class WkbDataReader implements AutoCloseable
{
    // ID to offset map
    private final Map<Long, Map.Entry<Long, Integer>> offsetMap = new LinkedHashMap<>();
    private final File file;
    private final File indexFile;
    private RandomAccessFile raf;
    
    public WkbDataReader(File file)
    {
        this.file = file;
        this.indexFile = new File(file.getAbsolutePath() + ".index");
        
        try
        {
            openFile();
            loadIndex();
        }
        catch (IOException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }
    }
    
    private void openFile() throws IOException
    {
        this.raf = new RandomAccessFile(file,"r");
    }

    private void loadIndex() throws IOException
    {
        try (final DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(indexFile))))
        {
            try
            {
                while (true)
                {
                    final long id = in.readLong();
                    final long offset = in.readLong();
                    final int length = in.readInt();
                    offsetMap.put(id, new AbstractMap.SimpleEntry<>(offset, length));
                }
            }
            catch (EOFException exc)
            {
                // Ignored
            }
        }
    }
    
    public byte[] read(long id)
    {
        final Map.Entry<Long, Integer> offsetAndLength = offsetMap.get(id);
        try
        {
            return offsetAndLength != null ? readAtOffset(offsetAndLength) : null;
        }
        catch (IOException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }
    }

    private byte[] readAtOffset(Entry<Long, Integer> offsetAndLength) throws IOException
    {
        synchronized (raf)
        {
            final byte[] b = new byte[offsetAndLength.getValue()];
            raf.seek(offsetAndLength.getKey());
            raf.read(b);
            return b;
        }
    }

    @Override
    public void close() throws Exception
    {
        if (raf != null)
        {
            raf.close();
        }
    }

    @SuppressWarnings("resource")
    public CloseableIterator<Entry<Long, byte[]>> iterator()
    {
        final Iterator<Entry<Long, Entry<Long, Integer>>> iter = offsetMap.entrySet().iterator();
        DataInputStream in;
        try
        {
            in = new DataInputStream(new FileInputStream(file));
        }
        catch (FileNotFoundException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }
        
        final Iterator<Map.Entry<Long,byte[]>> closable = new AbstractIterator<Map.Entry<Long,byte[]>>()
        {
            @Override
            protected Entry<Long, byte[]> computeNext()
            {
                if (iter.hasNext())
                {
                    final Entry<Long, Entry<Long, Integer>> e = iter.next();
                    final byte[] data = new byte[e.getValue().getValue()];
                    try
                    {
                        in.readFully(data);
                    }
                    catch (IOException exc)
                    {
                        throw new DataAccessResourceFailureException(exc.getMessage(), exc);
                    }
                    return new AbstractMap.SimpleEntry<>(e.getKey(), data);
                }
                return endOfData();
            }
        };
        return new CloseableIterator<Entry<Long, byte[]>>()
        {

            @Override
            public boolean hasNext()
            {
                return closable.hasNext();
            }

            @Override
            public Entry<Long, byte[]> next()
            {
                return closable.next();
            }

            @Override
            public void close()
            {
                try
                {
                    in.close();
                }
                catch (IOException exc)
                {
                    throw new DataAccessResourceFailureException(exc.getMessage(), exc);
                }
            }
        };
    }
}
