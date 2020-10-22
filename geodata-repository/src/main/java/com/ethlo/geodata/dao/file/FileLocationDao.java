package com.ethlo.geodata.dao.file;

import java.nio.file.Path;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.model.RawLocation;
import com.ethlo.geodata.util.SerializationUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;

@Repository
public class FileLocationDao implements LocationDao
{
    public static final String LOCATION_FILE = "locations.data";
    private final Path basePath;

    public FileLocationDao(@Value("${geodata.base-path}") final Path basePath)
    {
        this.basePath = basePath;
    }

    @Override
    public Map<Integer, RawLocation> load()
    {
        final Map<Integer, RawLocation> locations = new Int2ObjectLinkedOpenHashMap<>(100_000);
        try (final CloseableIterator<RawLocation> locationIter = SerializationUtil.read(basePath.resolve(LOCATION_FILE), RawLocation::new))
        {
            while (locationIter.hasNext())
            {
                final RawLocation l = locationIter.next();
                locations.put(l.getId(), l);
            }
        }
        return locations;
    }
}
