package com.ethlo.geodata;

/*-
 * #%L
 * geodata-server
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


import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.locationtech.jts.util.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ethlo.geodata.rest.v1.model.V1GeoLocation;
import com.google.common.net.InetAddresses;

@ConditionalOnProperty(value = "benchmark.enabled", havingValue = "true")
@RestController
@RequestMapping(value = "/benchmark", produces = "application/json")
public class BenchmarkController
{
    private static final int MAX_OPERATIONS = 1_000_000;
    private final AtomicBoolean running = new AtomicBoolean();

    @Autowired
    private V1ApiImpl controller;

    @GetMapping("/")
    public ResponseEntity<String> staticResponse()
    {
        return ResponseEntity.ok("hello");
    }

    @GetMapping("/v1/ips")
    public Map<String, Object> ipLookup(@RequestParam("count") final Integer count, @RequestParam("iterations") final Integer iterations)
    {
        return runExclusively(() -> doIpLookup(count, iterations));
    }

    private Map<String, Object> runExclusively(Supplier<Map<String, Object>> supplier)
    {
        try
        {
            if (running.compareAndSet(false, true))
            {
                return supplier.get();
            }
            else
            {
                throw new EmptyResultDataAccessException("Already running benchmark", 1);
            }
        } finally
        {
            running.set(false);
        }
    }

    private Map<String, Object> doIpLookup(final Integer count, final Integer iterations)
    {
        final int hits = count * iterations;
        Assert.isTrue(hits <= MAX_OPERATIONS, "Max operations are " + MAX_OPERATIONS);
        final Random random = new Random();
        final List<String> ips = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            final String ipString = InetAddresses.fromInteger(random.nextInt()).getHostAddress();
            ips.add(ipString);
        }

        final long nano = System.nanoTime();
        int empty = 0;
        for (int iteration = 0; iteration < iterations; iteration++)
        {
            for (String ip : ips)
            {
                try
                {
                    final ResponseEntity<V1GeoLocation> location = controller.findByIp(ip);
                }
                catch (EmptyResultDataAccessException exc)
                {
                    empty++;
                }
            }
        }
        final long ended = System.nanoTime();
        final Duration elapsed = Duration.ofNanos(ended - nano);

        final Map<String, Object> result = createResult(count, iterations, elapsed);
        result.put("empty", empty / iterations);
        return result;
    }

    private Map<String, Object> createResult(final Integer count, final Integer iterations, final Duration elapsed)
    {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("hits", count * iterations);
        result.put("average_elapsed", elapsed.dividedBy(count * iterations));
        result.put("per_second", (count * iterations) * 1000L / (double) elapsed.toMillis());
        result.put("count", count);
        result.put("iterations", iterations);
        result.put("elapsed", elapsed);
        return result;
    }
}
