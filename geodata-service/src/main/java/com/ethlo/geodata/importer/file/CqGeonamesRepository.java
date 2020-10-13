package com.ethlo.geodata.importer.file;

import static com.googlecode.cqengine.query.QueryFactory.attribute;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.ethlo.geodata.DataLoadedEvent;
import com.ethlo.geodata.IoUtils;
import com.ethlo.geodata.MapUtils;
import com.ethlo.geodata.ProgressListener;
import com.ethlo.geodata.importer.DataType;
import com.ethlo.geodata.importer.GeonamesImporter;
import com.ethlo.geodata.importer.Operation;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountrySummary;
import com.ethlo.geodata.model.GeoLocation;
import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.attribute.support.SimpleFunction;
import com.googlecode.cqengine.index.disk.DiskIndex;
import com.googlecode.cqengine.persistence.disk.DiskPersistence;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.query.QueryFactory;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.googlecode.cqengine.resultset.ResultSet;

@Component
public class CqGeonamesRepository extends BaseImporter
{
    public static final SimpleAttribute<GeoLocation, Long> ATTRIBUTE_ID = attribute(GeoLocation.class, Long.class, "id", GeoLocation::getId);
    public static final SimpleAttribute<GeoLocation, String> ATTRIBUTE_FEATURE_CLASS = QueryFactory.attribute(GeoLocation.class, String.class, "featureClass", new SimpleFunction<GeoLocation, String>()
    {
        @Override
        public String apply(GeoLocation object)
        {
            return object.getFeatureClass().toLowerCase();
        }
    });
    public static final SimpleAttribute<GeoLocation, String> ATTRIBUTE_FEATURE_CODE = QueryFactory.attribute(GeoLocation.class, String.class, "featureCode", new SimpleFunction<GeoLocation, String>()
    {
        @Override
        public String apply(GeoLocation object)
        {
            return object.getFeatureCode().toLowerCase();
        }
    });
    public static final Attribute<GeoLocation, Long> ATTRIBUTE_POPULATION = QueryFactory.nullableAttribute(GeoLocation.class, Long.class, "population", GeoLocation::getPopulation);
    public static final SimpleAttribute<GeoLocation, String> ATTRIBUTE_LC_NAME = QueryFactory.attribute(GeoLocation.class, String.class, "name", new SimpleFunction<GeoLocation, String>()
    {
        @Override
        public String apply(GeoLocation object)
        {
            return object.getName().toLowerCase();
        }
    });
    public static final Attribute<GeoLocation, String> ATTRIBUTE_COUNTRY_CODE = QueryFactory.nullableAttribute(GeoLocation.class, String.class, "country", new SimpleFunction<GeoLocation, String>()
    {
        @Override
        public String apply(GeoLocation object)
        {
            return object.getCountry() != null ? object.getCountry().getCode().toLowerCase() : null;
        }
    });
    private static final Logger logger = LoggerFactory.getLogger(CqGeonamesRepository.class);
    private Map<String, Country> countries;
    private Map<String, CountrySummary> countrySummaries;

    @Value("${geodata.geonames.source.names}")
    private String geoNamesAllCountriesUrl;

    @Value("${geodata.geonames.source.alternatenames}")
    private String geoNamesAlternateNamesUrl;

    @Value("${geodata.geonames.source.hierarchy}")
    private String geoNamesHierarchyUrl;

    private Set<String> exclusions;

    private ConcurrentIndexedCollection<GeoLocation> locations;

    public CqGeonamesRepository(ApplicationEventPublisher publisher)
    {
        super(publisher);
    }

    @Value("${geodata.geonames.features.excluded}")
    public void setExclusions(String csv)
    {
        exclusions = StringUtils.commaDelimitedListToSet(csv);
    }

    public void load()
    {
        loadCountries();
    }

    private void loadCountries()
    {
        countries = new LinkedHashMap<>();
        countrySummaries = new LinkedHashMap<>();
        final File countriesFile = new File(getBaseDirectory(), FileCountryImporter.FILENAME);
        logger.info("Loading countries");
        try (@SuppressWarnings("rawtypes") final CloseableIterator<Map> iter = new JsonIoReader<>(countriesFile, Map.class).iterator())
        {
            while (iter.hasNext())
            {
                @SuppressWarnings("unchecked") final Map<String, Object> m = iter.next();
                final Country c = mapCountry(m);
                final CountrySummary summary = new CountrySummary().setId(MapUtils.getLong(m, "geoname_id")).withCode(MapUtils.getString(m, "iso")).withName(c.getName());
                countries.put(summary.getCode(), c);
                countrySummaries.put(summary.getCode(), summary);
            }
        }
        logger.info("Loaded {} countries", countries.size());
        publish(new DataLoadedEvent(this, DataType.COUNTRY, Operation.LOAD, countries.size(), countries.size()));
    }

    private Country mapCountry(Map<String, Object> map)
    {
        final Country country = new Country();
        final Long id = MapUtils.getLong(map, "geoname_id");
        country.setId(id);
        country.setCode(MapUtils.getString(map, "iso"));
        country.setName(MapUtils.getString(map, "country"));
        country.setPopulation(MapUtils.getLong(map, "population"));
        final Set<String> languages = StringUtils.commaDelimitedListToSet(MapUtils.getString(map, "languages"));
        final String phone = MapUtils.getString(map, "phone");
        country.setLanguages(new ArrayList<>(languages));
        if (phone != null)
        {
            final Integer callingCode = Integer.parseInt(phone.replaceAll("[^\\d.]", ""));
            country.setCallingCode(callingCode);
        }
        return country;
    }

    @Override
    public long importData() throws IOException
    {
        loadCountries();

        final Map.Entry<Date, File> hierarchyFile = fetchResource(DataType.HIERARCHY, geoNamesHierarchyUrl);

        final Map.Entry<Date, File> alternateNamesFile = fetchResource(DataType.LOCATION_ALTERNATE_NAMES, geoNamesAlternateNamesUrl);

        final Map.Entry<Date, File> allCountriesFile = fetchResource(DataType.LOCATION, geoNamesAllCountriesUrl);

        return doUpdate(allCountriesFile.getValue(), alternateNamesFile.getValue(), hierarchyFile.getValue());
    }

    @Override
    public void purge()
    {
        this.locations.clear();
    }

    private long doUpdate(File allCountriesFile, File alternateNamesFile, File hierarchyFile) throws IOException
    {
        logger.info("Counting lines of {}", allCountriesFile);
        final long total = IoUtils.lineCount(allCountriesFile);
        final ProgressListener prg = new ProgressListener(l -> publish(new DataLoadedEvent(this, DataType.LOCATION, Operation.IMPORT, l, total)));

        final GeonamesImporter geonamesImporter = new GeonamesImporter.Builder().allCountriesFile(allCountriesFile).alternateNamesFile(alternateNamesFile).onlyHierarchical(false)
                .exclusions(exclusions).hierarchyFile(hierarchyFile).build();

        final int batchSize = 100_000;

        try (final CloseableIterator<Map<String, String>> iter = geonamesImporter.iterator())
        {
            final UnmodifiableIterator<List<Map<String, String>>> partitions = Iterators.partition(iter, batchSize);
            while (partitions.hasNext())
            {
                final List<Map<String, String>> partition = partitions.next();
                final List<GeoLocation> batch = partition.stream().map(this::mapLocation).collect(Collectors.toList());

                prg.update(batchSize);

                locations.addAll(batch);
            }
        }

        publish(new DataLoadedEvent(this, DataType.LOCATION, Operation.IMPORT, total, total));

        return total;
    }

    private GeoLocation mapLocation(Map<?, ?> m)
    {
        final GeoLocation geoLocation = new GeoLocation();
        final Long parentId = MapUtils.getLong(m, "parent_id") != null ? MapUtils.getLong(m, "parent_id") : null;
        final String countryCode = MapUtils.getString(m, "country_code");

        final CountrySummary country = countrySummaries.get(countryCode);
        Assert.notNull(country, "No country found for code " + countryCode);
        geoLocation.setCountry(country);

        final Long population = MapUtils.getLong(m, "population");

        geoLocation
                .setFeatureCode(MapUtils.getString(m, "feature_code"))
                .setFeatureClass(MapUtils.getString(m, "feature_class"))
                .setPopulation(population != null && population > 0 ? population : null)
                .setParentLocationId(parentId)
                .setId(MapUtils.getLong(m, "id"))
                .setName(MapUtils.getString(m, "name"))
                .setCoordinates(Coordinates.from(MapUtils.getDouble(m, "lat"), MapUtils.getDouble(m, "lng")));

        return geoLocation;
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return new Date(Math.max(getLastModified(geoNamesAllCountriesUrl).getTime(), getLastModified(geoNamesHierarchyUrl).getTime()));
    }

    public ResultSet<GeoLocation> retrieve(Query<GeoLocation> query)
    {
        return locations.retrieve(query);
    }

    public ResultSet<GeoLocation> retrieve(Query<GeoLocation> query, QueryOptions queryOptions)
    {
        return locations.retrieve(query, queryOptions);
    }

    public long size()
    {
        return locations.size();
    }

    @PostConstruct
    private void init()
    {
        publish(new DataLoadedEvent(this, DataType.LOCATION, Operation.LOAD, 0, 1));
        final File file = new File(getBaseDirectory(), "locations.cq");
        logger.info("Using locations file {}", file.getAbsolutePath());
        this.locations = new ConcurrentIndexedCollection<>(DiskPersistence.onPrimaryKeyInFile(ATTRIBUTE_ID, file));

        final DiskIndex<String, GeoLocation, ? extends Comparable<?>> nameIndex = DiskIndex.onAttribute(ATTRIBUTE_LC_NAME);
        locations.addIndex(nameIndex);

        final DiskIndex<Long, GeoLocation, ? extends Comparable<?>> populationIndex = DiskIndex.onAttribute(ATTRIBUTE_POPULATION);
        locations.addIndex(populationIndex);

        final DiskIndex<String, GeoLocation, ? extends Comparable<?>> featureClassIndex = DiskIndex.onAttribute(ATTRIBUTE_FEATURE_CLASS);
        locations.addIndex(featureClassIndex);

        final DiskIndex<String, GeoLocation, ? extends Comparable<?>> featureCodeIndex = DiskIndex.onAttribute(ATTRIBUTE_FEATURE_CODE);
        locations.addIndex(featureCodeIndex);

        publish(new DataLoadedEvent(this, DataType.LOCATION, Operation.LOAD, 1, 1));
    }

    public Map<String, Country> getCountries()
    {
        return countries;
    }
}
