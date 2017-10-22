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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.validation.Valid;

import org.apache.commons.collections4.trie.PatriciaTrie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import com.ethlo.geodata.importer.HierarchyImporter;
import com.ethlo.geodata.importer.file.FileCountryImporter;
import com.ethlo.geodata.importer.file.FileGeonamesImporter;
import com.ethlo.geodata.importer.file.JsonIoReader;
import com.ethlo.geodata.model.Continent;
import com.ethlo.geodata.model.Coordinates;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.CountrySummary;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationDistance;
import com.ethlo.geodata.model.View;
import com.ethlo.geodata.repository.GeoRepository;
import com.ethlo.geodata.util.GeometryUtil;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.UnsignedInteger;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;

@Service
public class GeodataServiceImpl implements GeodataService
{
    private final Logger logger = LoggerFactory.getLogger(GeodataServiceImpl.class);

    @Autowired
    private GeoRepository geoRepository;
    
    private RangeMap<Long, Long> ipRanges;
    
    private PatriciaTrie<Long> trie = new PatriciaTrie<>();
    
    private Map<Long, GeoLocation> locations;
    
    private RtreeRepository rTree;
    
    private static final Map<String, Long> CONTINENT_IDS = new LinkedHashMap<>();

    public static final double RAD_TO_KM_RATIO = 111.195D;
    
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
    
    private List<Continent> continents;
    private Set<Node> roots;
    private Map<Long, Node> nodes;
    private Map<String, Country> countries;
    private Long locationCount;

    @Value("${geodata.boundaries.quality}")
    private int qualityConstant;

    @Value("${data.directory}")
    private File baseDirectory;
         
    @PostConstruct
    public void load() throws IOException
    {
        loadIps();
        loadCountries();
        loadLocations();
        loadHierarchy();
        loadMbr();
    }
    
    private void loadMbr() throws IOException
    {
        logger.info("Loading MBR boundaries", locationCount);
        rTree = new RtreeRepository();
        
        final Iterator<Map.Entry<Long, byte[]>> iter = rTree.getReader().iterator();
        final WKBReader r = new WKBReader();
        while (iter.hasNext())
        {
            final Entry<Long, byte[]> e = iter.next();
            try
            {
                final Geometry g = r.read(e.getValue());
                rTree = rTree.add(new RTreePayload(e.getKey(), g.getArea(), g.getEnvelopeInternal()));
            }
            catch (ParseException exc)
            {
                throw new DataAccessResourceFailureException(exc.getMessage(), exc);
            }
        }
        logger.info("Loaded {} MBR boundaries", rTree.size());
    }
    
    public void loadLocations()
    {
        locations = new HashMap<>();
        final File locationsFile = new File(baseDirectory, FileGeonamesImporter.FILENAME);
        if (locationsFile.exists())
        {
            logger.info("Loading locations");
            try (CloseableIterator<GeoLocation> iter = geoRepository.locations())
            {
                while (iter.hasNext())
                {
                    final GeoLocation l = iter.next();
                    locations.put(l.getId(), l);
                    
                    trie.put(l.getName().toLowerCase(), l.getId());
                }
            }
            logger.info("Loaded {} locations", locations.size());
        }
    }
    
    public void loadIps()
    {
        logger.info("Loading IP ranges");
        ipRanges = TreeRangeMap.create();
        final AtomicInteger rangeCount = new AtomicInteger();
        try (CloseableIterator<Map.Entry<Long, Range<Long>>> r = geoRepository.ipRanges())
        {
            r.forEachRemaining(e->{ipRanges.put(e.getValue(), e.getKey()); rangeCount.incrementAndGet();});
        }
        logger.info("Loaded {} IP ranges", rangeCount.intValue());
    }
    
    @Override
    public GeoLocation findByIp(String ip)
    {
        final boolean isValid = InetAddresses.isInetAddress(ip);
        final InetAddress address = InetAddresses.forString(ip);
        final boolean isLocalAddress = address.isLoopbackAddress() || address.isAnyLocalAddress();
        if (!isValid || isLocalAddress)
        {
            return null;
        }
        
        long ipLong;
        try
        {
            ipLong = UnsignedInteger.fromIntBits(InetAddresses.coerceToInteger(InetAddresses.forString(ip))).longValue();
        }
        catch (IllegalArgumentException exc)
        {
            throw new InvalidIpException(ip, exc.getMessage(), exc);
        }
        
        final Long id = ipRanges.get(ipLong);
        return id != null ? findById(id) : null;
    }
    
    @Override
    public GeoLocation findById(long geoNameId)
    {
        return locations.get(geoNameId);
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
        throw new UnsupportedOperationException("Not implemented");
    }
    
    @Override
    public List<GeoLocation> findByIds(Collection<Long> ids)
    {
        return ids.stream().map(id->locations.get(id)).collect(Collectors.toList());
    }

    @Override
    public byte[] findBoundaries(long id)
    {
        return rTree.getReader().read(id);
    }

    @Override
    public Page<GeoLocation> findChildren(long locationId, Pageable pageable)
    {
        loadNodes();
        
        final Node node = nodes.get(locationId);
        final long total = node.getChildren().size();
        final List<Long> ids = node.getChildren()
            .stream()
            .skip(pageable.getOffset())
            .limit(pageable.getPageSize())
            .map(n->n.getId())
            .collect(Collectors.toList());
        final List<GeoLocation> locations = findByIds(ids);
        locations.sort((a, b)->a.getName().compareTo(b.getName()));
        return new PageImpl<>(locations, pageable, total);
    }

    private void loadNodes()
    {
        if (nodes == null)
        {
            try
            {
                loadHierarchy();
            }
            catch (IOException exc)
            {
                throw new DataAccessResourceFailureException(exc.getMessage(), exc);
            }
        }
    }

    @Override
    public Page<Continent> findContinents()
    {
        if (continents == null)
        {
            continents = findByIds(CONTINENT_IDS.values())
                .stream()
                .map(l->new Continent(getContinentCode(l.getId()), l))
                .collect(Collectors.toList());
        }
        return new PageImpl<>(continents, new PageRequest(0, 7), 7);
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
        throw new UnsupportedOperationException("Not implemented");
    }
    
    @Override
    public Page<Country> findCountries(Pageable pageable)
    {
        final List<Country> content = countries.values().stream().skip(pageable.getOffset()).limit(pageable.getPageSize()).collect(Collectors.toList());
        return new PageImpl<>(content, pageable, countries.size());
    }


    @Override
    public Country findCountryByCode(String countryCode)
    {
        if (countryCode != null)
        {
            return countries.get(countryCode.toUpperCase());
        }
        return null;
    }

    @Override
    public Page<GeoLocation> findChildren(String countryCode, Pageable pageable)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
    
    public void loadCountries()
    {
        countries = new LinkedHashMap<>();
        final File countriesFile = new File (baseDirectory, FileCountryImporter.FILENAME);
        if (countriesFile.exists())
        {
            logger.info("Loading countries");
            try (@SuppressWarnings("rawtypes")
            CloseableIterator<Map> iter = new JsonIoReader<>(countriesFile, Map.class).iterator())
            {
                while (iter.hasNext())
                {
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> m = iter.next();
                    final GeoLocation l = mapLocation(m);
                    final Country c = Country.from(l);
                    countries.put(m.get("country_code").toString(), c);
                }
            }
            logger.info("Loaded {} countries", locations.size());
        }
    }
    
    private GeoLocation mapLocation(Map<String, Object> map)
    {
        final GeoLocation l = new GeoLocation();
        l.setId(Long.parseLong(map.get("id").toString()));
        l.setName(map.get("name").toString());
        l.setCoordinates(Coordinates.from(Double.parseDouble(map.get("lat").toString()), Double.parseDouble(map.get("lng").toString())));
        
        // TODO: Set country name
        l.setCountry(new CountrySummary().withCode(map.get("country_code").toString()).withName(""));
        return l;
    }

    public int loadHierarchy() throws IOException
    {
        nodes = new HashMap<>();
        
        final File hierarchyFile = new File("/tmp/hierarchy.tsv");
        final Map<Long, Long> childToParent = new HashMap<>();
        new HierarchyImporter(hierarchyFile).processFile(r->
        {
            final long parentId = Long.parseLong(r.get("parent_id"));
            final long childId = Long.parseLong(r.get("child_id"));
            final Node parent = nodes.getOrDefault(parentId, new Node(parentId));
            final Node child = nodes.getOrDefault(childId, new Node(childId));
            nodes.put(parent.getId(), parent);
            nodes.put(child.getId(), child);            
            childToParent.put(childId, parentId);
        });
        
        // Process hierarchy after, as we do not know the order of the pairs
        childToParent.entrySet().forEach(e->
        {
            final long child = e.getKey();
            final long parent = e.getValue();
            final Node childNode = nodes.get(child);
            final Node parentNode = nodes.get(parent);
            parentNode.addChild(childNode);
            childNode.setParent(parentNode);
        });
        
        roots = new HashSet<>();
        for (Entry<Long, Node> e : nodes.entrySet())
        {
            if (e.getValue().getParent() == null)
            {
                roots.add(e.getValue());
            }
        }
        
        return childToParent.size();
    }

    @Override
    public Country findByPhonenumber(String phoneNumber)
    {
        String stripped = phoneNumber.replaceAll("[^\\d.]", "");
        stripped = stripped.replaceFirst("^0+(?!$)", "");
        
        throw new UnsupportedOperationException("Not implememnted");
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
        loadNodes();
        
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
        return location.getParentLocationId() != null ? findById(location.getParentLocationId()) : null;
    }

    @Override
    public GeoLocation findbyCoordinate(Coordinates point, int distance)
    {
        GeoLocation location = findWithin(point, distance);
        
        // Fall back to nearest match
        if (location == null)
        {
            final Page<GeoLocationDistance> nearest = findNear(point, distance, new PageRequest(0, 1));
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
	        
	        logger.debug("locationId: {}, original points: {}, remaining points: {}, ratio: {}, elapsed: {}", id, full.getNumPoints(), simplified.getNumPoints(), full.getNumPoints() / (double)simplified.getNumPoints(), stopwatch);
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
	        logger.debug("locationId: {}, original points: {}, remaining points: {}, ratio: {}, elapsed: {}", id, full.getNumPoints(), simplified.getNumPoints(), full.getNumPoints() / (double)simplified.getNumPoints(), stopwatch);
	        return new WKBWriter().write(simplified);
        }
        catch (ParseException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }
    }

    @Override
    public Page<GeoLocation> findByName(String name, Pageable pageable)
    {
        final SortedMap<String, Long> matches = trie.prefixMap(name.toLowerCase());
        final List<GeoLocation> content = matches.values()
            .stream()
            .skip(pageable.getOffset())
            .limit(pageable.getPageSize())
            .map(id->findById(id))
            .collect(Collectors.toList());
        return new PageImpl<>(content, pageable, matches.size());
    }
}
