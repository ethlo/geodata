package com.ethlo.geodata;

/*-
 * #%L
 * geodata-server
 * %%
 * Copyright (C) 2017 - 2020 Morten Haraldsen (ethlo)
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.EvictingQueue;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.PathTemplateMatch;

@SuppressWarnings("UnstableApiUsage")
public class PerformanceHandler implements HttpHandler
{
    private static final int PER_THREAD_HISTORY = 100;
    private final ConcurrentHashMap<Thread, PerformanceTracker> perThreadPerformance = new ConcurrentHashMap<>();
    private final HttpHandler delegate;

    public PerformanceHandler(HttpHandler delegate)
    {
        final PathHandler pathHandler = Handlers.path(delegate);
        pathHandler.addPrefixPath("/sysadmin/performance", exchange -> BaseServerHandler.json(exchange, getPerformanceMap()));
        this.delegate = pathHandler;
    }

    private Map<String, Object> getPerformanceMap()
    {
        final PerformanceTracker combined = new PerformanceTracker(Integer.MAX_VALUE);
        final Map<String, Long> totalInvocations = new HashMap<>();
        for (final PerformanceTracker tracker : perThreadPerformance.values())
        {
            for (Map.Entry<String, EvictingQueue<Long>> entry : tracker.results.entrySet())
            {
                combined.results.compute(entry.getKey(), (k, v) ->
                {
                    if (v == null)
                    {
                        v = EvictingQueue.create(perThreadPerformance.size() * PER_THREAD_HISTORY);
                    }
                    v.addAll(entry.getValue());
                    return v;
                });
                totalInvocations.compute(entry.getKey(), (k, v) ->
                {
                    if (v == null)
                    {
                        v = 0L;
                    }
                    v += tracker.getTotalInvocations(entry.getKey());
                    return v;
                });
            }
        }

        final Map<String, Object> results = new LinkedHashMap<>();
        for (Map.Entry<String, EvictingQueue<Long>> entry : combined.results.entrySet())
        {
            final Map<String, Object> details = new LinkedHashMap<>();

            final LongSummaryStatistics stats = new ArrayList<>(entry.getValue()).stream().mapToLong(l -> l).summaryStatistics();
            final Map<String, Object> statsMap = new LinkedHashMap<>();
            statsMap.put("average", Duration.ofNanos((long) stats.getAverage()).toString());
            statsMap.put("min", Duration.ofNanos(stats.getMin()).toString());
            statsMap.put("max", Duration.ofNanos(stats.getMax()).toString());
            statsMap.put("count", stats.getCount());

            details.put("latest", statsMap);
            details.put("total_invocations", totalInvocations.get(entry.getKey()));
            results.put(entry.getKey(), details);
        }

        return results;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception
    {
        final long started = System.nanoTime();

        delegate.handleRequest(exchange);

        final PathTemplateMatch path = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
        if (path != null)
        {

            final long ended = System.nanoTime();
            final long elapsed = ended - started;
            perThreadPerformance.compute(Thread.currentThread(), (k, v) ->
            {
                if (v == null)
                {
                    v = new PerformanceTracker(PER_THREAD_HISTORY);
                }
                v.addResult(path.getMatchedTemplate(), elapsed);
                return v;
            });
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    public static class PerformanceTracker
    {
        private final Map<String, EvictingQueue<Long>> results = new HashMap<>();
        private final Map<String, Long> totalInvocations = new HashMap<>();
        private final int maxEntries;

        public PerformanceTracker(final int maxEntries)
        {
            this.maxEntries = maxEntries;
        }

        public void addResult(String path, long elapsed)
        {
            totalInvocations.compute(path, (k, v) ->
            {
                if (v == null)
                {
                    v = 0L;
                }
                v += 1;
                return v;
            });

            results.compute(path, (k, v) ->
            {
                if (v == null)
                {
                    v = EvictingQueue.create(maxEntries);
                }
                v.add(elapsed);
                return v;
            });
        }

        public Map<String, EvictingQueue<Long>> getResults()
        {
            return results;
        }

        public Long getTotalInvocations(String path)
        {
            return totalInvocations.get(path);
        }
    }
}