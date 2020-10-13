package com.ethlo.geodata;

/*-
 * #%L
 * geodata
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.CloseableIterator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.ethlo.geodata.importer.DataType;
import com.ethlo.geodata.importer.HierarchyImporter;
import com.ethlo.geodata.importer.Operation;
import com.ethlo.geodata.importer.file.CqGeonamesRepository;
import com.ethlo.geodata.importer.file.FileGeonamesBoundaryImporter;
import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.View;
import com.ethlo.geodata.repository.GeoRepository;
import com.ethlo.geodata.util.DistanceUtil;
import com.ethlo.geodata.util.GeometryUtil;
import com.github.davidmoten.guavamini.Lists;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.query.QueryFactory;
import com.googlecode.cqengine.query.logical.And;
import com.googlecode.cqengine.resultset.ResultSet;

@Lazy
@Primary
@Service
public class GeodataServiceImpl implements GeodataService
{
    public static final double RAD_TO_KM_RATIO = 111.195D;
    private static final Map<String, Long> CONTINENT_IDS = new LinkedHashMap<>();

    static
    {
        CONTINENT_IDS.put("AF", 6255146L);
        CONTINENT_IDS.put("AS", 6255147L);
        CONTINENT_IDS.put("EU", 6255148L);
        CONTINENT_IDS.put("NA", 6255149L);
        CONTINENT_IDS.put("OC", 6255151L);
        CONTINENT_IDS.put("SA", 6255150L);
        CONTINENT_IDS.put("AN", 6255152L);
    }

    private final Logger logger = LoggerFactory.getLogger(GeodataServiceImpl.class);
    @Autowired
    private GeoRepository geoRepository;
    @Autowired
    private GeoMetaService geoMetaService;
    @Autowired
    private CqGeonamesRepository geoNamesRepository;
    @Autowired
    private ApplicationEventPublisher publisher;
    private RangeMap<Long, Long> ipRanges;
    private RtreeRepository rTree;
    private Map<Long, Node> nodes;

    @Value("${geodata.boundaries.quality}")
    private int qualityConstant;
    private File baseDirectory;

    @Value("${data.directory}")
    public void setBaseDirectory(File baseDirectory)
    {
        this.baseDirectory = new File(baseDirectory.getPath().replaceFirst("^~", System.getProperty("user.home")));
    }

    public void load()
    {
        ensureBaseDirectory();

        final SourceDataInfoSet sourceInfo = geoMetaService.getSourceDataInfo();
        if (sourceInfo.isEmpty())
        {
            logger.error("Cannot start geodata server as there is no data. Please run with 'update' parameter to import data");
            System.exit(1);
        }

        loadHierarchy();

        final ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(3);
        taskExecutor.setThreadNamePrefix("data-loading-");
        taskExecutor.initialize();

        taskExecutor.execute(this::loadLocations);
        taskExecutor.execute(this::loadMbr);
        taskExecutor.execute(this::loadIps);

        taskExecutor.setAwaitTerminationSeconds(Integer.MAX_VALUE);
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.shutdown();

        //final ResultSet<GeoLocation> result = geoNamesRepository.retrieve(QueryFactory.equal(CqGeonamesRepository.ATTRIBUTE_FEATURE_CODE, "ADM1"));
        //result.forEach(this::connectAdm1WithCountry);

        publisher.publishEvent(new DataLoadedEvent(this, DataType.ALL, Operation.LOAD, 1, 1));
    }

    private void connectAdm1WithCountry(GeoLocation l)
    {
        final Country c = geoNamesRepository.getCountries().get(l.getCountry().getCode());
        final long countryId = c.getId();
        l.setParentLocationId(countryId);

        final Node countryNode = findOrCreate(countryId);
        final Node locationNode = findOrCreate(l.getId());
        countryNode.addChild(locationNode);
        nodes.put(countryId, countryNode);
        nodes.put(l.getId(), locationNode);
    }

    private Node findOrCreate(long id)
    {
        Node existing = nodes.get(id);
        if (existing == null)
        {
            existing = new Node(id);
        }
        return existing;
    }

    private void loadLocations()
    {
        logger.info("Loading locations");
        geoNamesRepository.load();
        logger.info("Loaded {} locations", geoNamesRepository.size());
    }

    private void ensureBaseDirectory()
    {
        logger.info("Ensuring data directory {} exists", baseDirectory.getAbsolutePath());
        if (!baseDirectory.exists())
        {
            Assert.isTrue(baseDirectory.mkdirs(), "Could not create directory " + baseDirectory.getAbsolutePath());
        }
    }

    @SuppressWarnings("rawtypes")
    private void loadMbr()
    {
        logger.info("Loading MBR boundaries");
        rTree = new RtreeRepository(new File(baseDirectory, FileGeonamesBoundaryImporter.ENVELOPE_FILENAME));

        final Iterator<Map> iter = rTree.getEnvelopeReader().iterator();
        while (iter.hasNext())
        {
            final Map map = iter.next();
            final long id = MapUtils.getLong(map, "id");
            final Envelope e = new Envelope(
                    MapUtils.getDouble(map, "minX"),
                    MapUtils.getDouble(map, "maxX"),
                    MapUtils.getDouble(map, "minY"),
                    MapUtils.getDouble(map, "maxY")
            );
            rTree = rTree.add(new RTreePayload(id, e.getArea(), e));
        }
        logger.info("Loaded {} MBR boundaries", rTree.size());
        publisher.publishEvent(new DataLoadedEvent(this, DataType.BOUNDARY, Operation.LOAD, rTree.size(), rTree.size()));
    }

    public void loadIps()
    {
        logger.info("Loading IP ranges");

        final long size = geoMetaService.getSourceDataInfo().get(DataType.IP).getCount();
        final ProgressListener prg = new ProgressListener(l -> publisher.publishEvent(new DataLoadedEvent(this, DataType.IP, Operation.LOAD, l, size)));

        ipRanges = TreeRangeMap.create();
        try (CloseableIterator<Map.Entry<Long, Range<Long>>> r = geoRepository.ipRanges())
        {
            r.forEachRemaining(e -> {
                ipRanges.put(e.getValue(), e.getKey());
                prg.update();
            });
        }
        publisher.publishEvent(new DataLoadedEvent(this, DataType.IP, Operation.LOAD, size, size));
        logger.info("Loaded {} IP ranges", size);

    }

    @Override
    public GeoLocation findByIp(String ip)
    {
        if (!InetAddresses.isInetAddress(ip))
        {
            return null;
        }

        final InetAddress address = InetAddresses.forString(ip);
        final boolean isLocalAddress = address.isLoopbackAddress() || address.isAnyLocalAddress();
        if (isLocalAddress)
        {
            return null;
        }

        final long ipLong = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(ip))).longValue();

        final Long id = ipRanges.get(ipLong);
        return id != null ? findById(id) : null;
    }

    @Override
    public GeoLocation findById(long geoNameId)
    {
        final Query<GeoLocation> query = QueryFactory.equal(CqGeonamesRepository.ATTRIBUTE_ID, geoNameId);
        return oneOrNull(geoNamesRepository.retrieve(query));
    }

    private GeoLocation oneOrNull(ResultSet<GeoLocation> results)
    {
        return results.isNotEmpty() ? results.iterator().next() : null;
    }

    @Override
    public GeoLocation findWithin(Coordinates point, int maxDistanceInKilometers)
    {
        final Long id = rTree.find(point);
        return id != null ? findById(id) : null;
    }

    @Override
    public Page<GeoLocationDistance> findNear(Coordinates point, int maxDistanceInKilometers, Pageable pageable)
    {
        final Iterator<Long> ids = rTree.findNear(point, maxDistanceInKilometers, pageable);
        final List<GeoLocationDistance> content = new LinkedList<>();
        while (ids.hasNext())
        {
            final long id = ids.next();
            final GeoLocationDistance e = new GeoLocationDistance();
            final GeoLocation location = findById(id);
            if (location != null)
            {
                e.setLocation(location);
                final double distance = DistanceUtil.distance(location.getCoordinates(), point);
                e.setDistance(distance);
                content.add(e);
            }
        }
        final long total = content.size();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<GeoLocation> findByIds(Collection<Long> ids)
    {
        final Query<GeoLocation> query = QueryFactory.in(CqGeonamesRepository.ATTRIBUTE_ID, ids);
        return Lists.newArrayList(geoNamesRepository.retrieve(query).iterator());
    }

    @Override
    public byte[] findBoundaries(long id)
    {
        return rTree.getReader().read(id);
    }

    @Override
    public Page<GeoLocation> findChildren(long locationId, Pageable pageable)
    {
        final Node node = nodes.get(locationId);
        final long total = node.getChildren().size();
        final List<Long> ids = node.getChildren()
                .stream()
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .map(Node::getId)
                .collect(Collectors.toList());
        final List<GeoLocation> content = findByIds(ids).stream().filter(Objects::nonNull).collect(Collectors.toList());

        content.sort((a, b) -> a.getName().compareTo(b.getName()));
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<Continent> findContinents()
    {
        final List<Continent> continents = findByIds(CONTINENT_IDS.values())
                .stream()
                .filter(Objects::nonNull)
                .map(l -> new Continent(getContinentCode(l.getId()), l))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new PageImpl<>(continents);
    }

    private String getContinentCode(Long id)
    {
        for (Entry<String, Long> e : CONTINENT_IDS.entrySet())
        {
            if (e.getValue().equals(id))
            {
                return e.getKey();
            }
        }
        throw new IllegalArgumentException("Unknown continent ID: " + id);
    }

    @Override
    public Page<Country> findCountriesOnContinent(String continentCode, Pageable pageable)
    {
        final Long continentId = CONTINENT_IDS.get(continentCode.toUpperCase());
        if (continentId == null)
        {
            return new PageImpl<>(Collections.emptyList());
        }
        return findChildren(continentId, pageable).map(l -> findCountryById(l.getId()));
    }

    private Country findCountryById(Long id)
    {
        for (Country c : geoNamesRepository.getCountries().values())
        {
            if (c.getId().equals(id))
            {
                return c;
            }
        }
        return null;
    }

    @Override
    public Page<Country> findCountries(Pageable pageable)
    {
        final List<Country> content = geoNamesRepository.getCountries().values().stream().skip(pageable.getOffset()).limit(pageable.getPageSize()).collect(Collectors.toList());
        return new PageImpl<>(content, pageable, geoNamesRepository.getCountries().size());
    }


    @Override
    public Country findCountryByCode(String countryCode)
    {
        if (countryCode != null)
        {
            return geoNamesRepository.getCountries().get(countryCode.toUpperCase());
        }
        return null;
    }

    @Override
    public Page<GeoLocation> findChildren(String countryCode, Pageable pageable)
    {
        final Country country = geoNamesRepository.getCountries().get(countryCode.toLowerCase());
        if (country == null)
        {
            return null;
        }
        return findChildren(country.getId(), pageable);
    }

    public int loadHierarchy()
    {
        nodes = new HashMap<>();

        final File hierarchyFile = new File(baseDirectory, "hierarchy");
        if (!hierarchyFile.exists())
        {
            return 0;
        }

        final Map<Long, Long> childToParent = new HashMap<>();
        try
        {
            new HierarchyImporter(hierarchyFile).processFile(r ->
            {
                final String featureCode = r.get("feature_code");
                if (featureCode == null || "adm".equalsIgnoreCase(featureCode))
                {
                    final long parentId = Long.parseLong(r.get("parent_id"));
                    final long childId = Long.parseLong(r.get("child_id"));
                    final Node parent = nodes.getOrDefault(parentId, new Node(parentId));
                    final Node child = nodes.getOrDefault(childId, new Node(childId));
                    nodes.put(parent.getId(), parent);
                    nodes.put(child.getId(), child);
                    childToParent.put(childId, parentId);
                }
            });
        }
        catch (IOException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }

        // Process hierarchy after, as we do not know the order of the pairs
        childToParent.entrySet().forEach(e ->
        {
            final long child = e.getKey();
            final long parent = e.getValue();
            final Node childNode = nodes.get(child);
            final Node parentNode = nodes.get(parent);
            parentNode.addChild(childNode);
            childNode.setParent(parentNode);
        });

        final Set<Node> roots = new HashSet<>();
        for (Entry<Long, Node> e : nodes.entrySet())
        {
            if (e.getValue().getParent() == null)
            {
                roots.add(e.getValue());
            }
        }

        publisher.publishEvent(new DataLoadedEvent(this, DataType.HIERARCHY, Operation.LOAD, childToParent.size(), childToParent.size()));

        return childToParent.size();
    }

    @Override
    public Country findByPhonenumber(String phoneNumber)
    {
        final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        PhoneNumber pn;
        try
        {
            pn = phoneUtil.parse("+" + phoneNumber, "US");
        }
        catch (NumberParseException exc)
        {
            return null;
        }

        final Integer countryCode = pn.getCountryCode();
        for (Country country : geoNamesRepository.getCountries().values())
        {
            if (countryCode.equals(country.getCallingCode()))
            {
                return country;
            }
        }
        return null;
    }

    @Override
    public boolean isInsideAny(List<Long> locations, long location)
    {
        final GeoLocation loc = findById(location);
        if (loc == null)
        {
            throw new EmptyResultDataAccessException("No such location found " + location, 1);
        }

        for (Long l : locations)
        {
            if (l.equals(location) || isLocationInside(location, l))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isOutsideAll(List<Long> locations, long location)
    {
        final GeoLocation loc = findById(location);
        if (loc == null)
        {
            throw new EmptyResultDataAccessException("No such location found " + location, 1);
        }

        for (long l : locations)
        {
            if (isLocationInside(l, loc.getId()))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isLocationInside(long locationId, long suspectedParentId)
    {
        final List<Long> path = getPath(locationId);
        return path.contains(suspectedParentId);
    }

    private List<Long> getPath(long id)
    {
        Node node = this.nodes.get(id);
        final List<Long> path = new LinkedList<>();
        while (node != null)
        {
            path.add(node.getId());
            node = node.getParent();
        }
        return path;
    }

    @Override
    public Continent findContinent(String continentCode)
    {
        final Long id = CONTINENT_IDS.get(continentCode.toUpperCase());
        if (id != null)
        {
            return new Continent(continentCode.toUpperCase(), findById(id));
        }
        return null;
    }

    @Override
    public GeoLocation findParent(long id)
    {
        final GeoLocation location = findById(id);
        return location != null && location.getParentLocationId() != null ? findById(location.getParentLocationId()) : null;
    }

    @Override
    public GeoLocation findbyCoordinate(Coordinates point, int distance)
    {
        GeoLocation location = findWithin(point, distance);

        // Fall back to nearest match
        if (location == null)
        {
            final Page<GeoLocationDistance> nearest = findNear(point, distance, PageRequest.of(0, 1));
            location = nearest.hasContent() ? nearest.getContent().get(0).getLocation() : null;
        }

        if (location != null)
        {
            return location;
        }
        throw new EmptyResultDataAccessException("Cannot find a location for position lat=" + point.getLat() + ", lng=" + point.getLng(), 1);
    }

    @Override
    public byte[] findBoundaries(long id, @Valid View view)
    {
        final byte[] fullWkb = this.findBoundaries(id);

        if (fullWkb == null)
        {
            return null;
        }

        final WKBReader reader = new WKBReader();
        try
        {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            final Geometry full = reader.read(fullWkb);
            Geometry simplified = GeometryUtil.simplify(full, view, qualityConstant);
            if (simplified == null)
            {
                return createEmptyGeometry();
            }

            final Geometry clipped = GeometryUtil.clip(new Envelope(view.getMinLng(), view.getMaxLng(), view.getMinLat(), view.getMaxLat()), simplified);
            if (clipped != null)
            {
                simplified = clipped;
            }

            logger.debug("locationId: {}, original points: {}, remaining points: {}, ratio: {}, elapsed: {}", id, full.getNumPoints(), simplified.getNumPoints(), full.getNumPoints() / (double) simplified.getNumPoints(), stopwatch);
            return new WKBWriter().write(simplified);
        }
        catch (ParseException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }
    }

    private byte[] createEmptyGeometry()
    {
        return new WKBWriter().write(GeometryUtil.createEmptyGeomtryCollection());
    }

    @Override
    public byte[] findBoundaries(long id, double maxTolerance)
    {
        final byte[] fullWkb = this.findBoundaries(id);

        if (fullWkb == null)
        {
            return null;
        }

        final WKBReader reader = new WKBReader();
        try
        {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            final Geometry full = reader.read(fullWkb);
            final Geometry simplified = GeometryUtil.simplify(full, maxTolerance);
            if (simplified == null)
            {
                return createEmptyGeometry();
            }
            logger.debug("locationId: {}, original points: {}, remaining points: {}, ratio: {}, elapsed: {}", id, full.getNumPoints(), simplified.getNumPoints(), full.getNumPoints() / (double) simplified.getNumPoints(), stopwatch);
            return new WKBWriter().write(simplified);
        }
        catch (ParseException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }
    }

    @Override
    public Page<GeoLocation> filter(LocationFilter filter, Pageable pageable)
    {
        final Collection<Query<GeoLocation>> queries = new LinkedList<>();

        if (filter.getName() != null)
        {
            if (filter.getName().endsWith("*"))
            {
                queries.add(QueryFactory.startsWith(CqGeonamesRepository.ATTRIBUTE_LC_NAME, StringUtils.strip(filter.getName(), "*")));
            }
            else
            {
                queries.add(QueryFactory.equal(CqGeonamesRepository.ATTRIBUTE_LC_NAME, filter.getName()));
            }
        }

        if (!filter.getFeatureClasses().isEmpty())
        {
            queries.add(QueryFactory.in(CqGeonamesRepository.ATTRIBUTE_FEATURE_CLASS, filter.getFeatureClasses()));
        }

        if (!filter.getFeatureCodes().isEmpty())
        {
            queries.add(QueryFactory.in(CqGeonamesRepository.ATTRIBUTE_FEATURE_CODE, filter.getFeatureCodes()));
        }

        if (!filter.getCountryCodes().isEmpty())
        {
            queries.add(QueryFactory.in(CqGeonamesRepository.ATTRIBUTE_COUNTRY_CODE, filter.getCountryCodes()));
        }

        Query<GeoLocation> query;

        if (queries.isEmpty())
        {
            query = QueryFactory.none(GeoLocation.class);
        }
        else if (queries.size() == 1)
        {
            query = queries.iterator().next();
        }
        else
        {
            query = new And<>(queries);
        }

        final ResultSet<GeoLocation> result = geoNamesRepository.retrieve(query,
                QueryFactory.queryOptions(QueryFactory.orderBy(QueryFactory.descending(QueryFactory.missingLast(CqGeonamesRepository.ATTRIBUTE_POPULATION))))
        );
        int index = 0;
        final List<GeoLocation> content = new LinkedList<>();
        for (GeoLocation l : result)
        {
            if (index >= pageable.getOffset() && content.size() < pageable.getPageSize())
            {
                content.add(l);
            }
            index++;
        }

        return new PageImpl<>(content, pageable, index);
    }
}
