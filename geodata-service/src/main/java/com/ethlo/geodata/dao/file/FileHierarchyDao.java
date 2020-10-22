package com.ethlo.geodata.dao.file;

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

import com.ethlo.geodata.IntIntMapSerializer;
import com.ethlo.geodata.dao.HierarchyDao;
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
