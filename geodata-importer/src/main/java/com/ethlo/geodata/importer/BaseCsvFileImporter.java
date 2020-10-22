package com.ethlo.geodata.importer;


import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.iterators.FilterIterator;
import org.springframework.data.util.CloseableIterator;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.model.CompactSerializable;
import com.ethlo.geodata.util.CompressionUtil;
import com.ethlo.geodata.util.ResourceUtil;
import com.ethlo.geodata.util.SerializationUtil;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;

public abstract class BaseCsvFileImporter<T extends CompactSerializable> implements DataImporter
{
    private final Path basePath;
    private final String url;
    private final String type;
    private final List<String> header;
    private final boolean isTsv;
    private final int skipLines;
    private Path sourcePath;

    public BaseCsvFileImporter(final Path basePath, String type, String url, List<String> header, final boolean isTsv, int skipLines)
    {
        this.type = type;
        this.basePath = basePath;
        this.url = url;
        this.header = header;
        this.isTsv = isTsv;
        this.skipLines = skipLines;
    }

    protected Path getDataFilePath()
    {
        return basePath.resolve(type + ".data");
    }

    protected Path getSourcePath()
    {
        return sourcePath;
    }

    @Override
    public void purgeData()
    {
        try
        {
            Files.deleteIfExists(getDataFilePath());
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private int writeData(Iterator<T> data) throws IOException
    {
        int count = 0;
        try (final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(CompressionUtil.compress(Files.newOutputStream(getDataFilePath())))))
        {
            while (data.hasNext())
            {
                data.next().write(out);
                count++;
            }
        }
        return count;
    }

    protected CloseableIterator<T> iterator(final Path csvFile, List<String> header) throws IOException
    {
        final CloseableIterator<Map<String, String>> lineIterator = rawIterator(csvFile, header);
        final Iterator<T> entryIterator = new AbstractIterator<>()
        {
            @Override
            protected T computeNext()
            {
                T result = null;
                while (lineIterator.hasNext() && (result = processLine(lineIterator.next())) == null) ;
                if (result != null)
                {
                    return result;
                }
                return endOfData();
            }
        };

        return SerializationUtil.wrapClosable(entryIterator, lineIterator);
    }

    private BufferedReader getPositionedReader(final Path csvFile) throws IOException
    {
        final BufferedReader reader = new BufferedReader(new FileReader(csvFile.toFile()));

        // Skip requested number of lines
        for (int i = 0; i < skipLines; i++)
        {
            reader.readLine();
        }
        return reader;
    }

    private Map<String, String> convert(String line, List<String> header)
    {
        final String[] entry = StringUtils.delimitedListToStringArray(line, isTsv ? "\t" : ",");
        final Map<String, String> data = new HashMap<>(entry.length);
        for (int i = 0; i < Math.min(header.size(), entry.length); i++)
        {
            data.put(header.get(i), entry[i]);
        }
        return data;
    }

    protected CloseableIterator<Map<String, String>> rawIterator(Path file, List<String> columns) throws IOException
    {
        final BufferedReader reader = getPositionedReader(file);
        final Iterator<String> lineIter = new AbstractIterator<>()
        {
            @Override
            protected String computeNext()
            {
                try
                {
                    String line = reader.readLine();
                    if (line != null)
                    {
                        return line;
                    }
                    return endOfData();
                }
                catch (IOException exc)
                {
                    throw new UncheckedIOException(exc);
                }
            }
        };
        final Iterator<String> nonNullIter = new FilterIterator<>(lineIter, line -> line != null && !line.isBlank() && !line.startsWith("#"));
        return SerializationUtil.wrapClosable(Iterators.transform(nonNullIter, line -> convert(line, columns)), reader);
    }

    protected abstract T processLine(final Map<String, String> next);

    @Override
    public int importData()
    {
        try
        {
            prepare();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }

        Map.Entry<Date, File> fileData;
        try
        {
            fileData = ResourceUtil.fetchResource(type, url);
            this.sourcePath = fileData.getValue().toPath();
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException("Unable to download resource: " + url, exc);
        }

        try (final CloseableIterator<T> iter = iterator(fileData.getValue().toPath(), header))
        {
            final int result = writeData(iter);
            processing();
            return result;
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    protected void prepare() throws IOException
    {
    }

    protected void processing()
    {
        // Callback method
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return ResourceUtil.getLastModified(url);
    }
}
