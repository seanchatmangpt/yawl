/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.processmining.ocpm;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Performance analysis for Object-Centric Process Mining (OCPM).
 *
 * <p>Analyzes OCEL 2.0 logs to extract:
 * <ul>
 *   <li>Cycle time per object type (time from first to last event)</li>
 *   <li>Throughput per object type (objects processed per unit time)</li>
 *   <li>Wait times between activities (average time from activity to next activity)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class OcpmPerformanceAnalyzer {

    /**
     * Performance analysis result.
     *
     * @param avgCycleTimeByObjectType average cycle time per object type
     * @param throughputByObjectType throughput (objects/hour) per object type
     * @param avgWaitTimeByActivity average wait time between consecutive activities
     */
    public record OcpmPerformanceResult(
        Map<String, Duration> avgCycleTimeByObjectType,
        Map<String, Long> throughputByObjectType,
        Map<String, Duration> avgWaitTimeByActivity
    ) {}

    /**
     * Analyze performance metrics from an OCEL 2.0 log.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>For each object type:
     *       <ul>
     *         <li>For each object of that type:
     *             <ul>
     *               <li>Get first and last event timestamps</li>
     *               <li>Compute cycle time = last - first</li>
     *             </ul>
     *         <li>Average cycle times across all objects of that type</li>
     *       </ul>
     *   <li>Compute throughput as (object count) / (log timespan in hours)</li>
     *   <li>For each activity pair (a, b) where a → b occurs:
     *       <ul>
     *         <li>Compute wait time = time(b) - time(a)</li>
     *         <li>Average across all occurrences</li>
     *       </ul>
     * </ol>
     *
     * @param log OCEL 2.0 input log
     * @return performance analysis result
     * @throws NullPointerException if log is null
     */
    public static OcpmPerformanceResult analyze(OcpmInput log) {
        Objects.requireNonNull(log, "log is required");

        if (log.events().isEmpty()) {
            return new OcpmPerformanceResult(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap()
            );
        }

        // Group events by object
        Map<String, List<OcpmInput.OcpmEvent>> eventsByObject = new HashMap<>();
        for (OcpmInput.OcpmEvent evt : log.events()) {
            for (String objectId : evt.relatedObjects().values()) {
                eventsByObject.computeIfAbsent(objectId, k -> new ArrayList<>())
                    .add(evt);
            }
        }

        // Compute cycle times per object type
        Map<String, Duration> avgCycleTimeByObjectType = computeCycleTimes(
            log.objects(), eventsByObject
        );

        // Compute throughput
        Map<String, Long> throughputByObjectType = computeThroughput(
            log.objects(), log.events(), eventsByObject
        );

        // Compute wait times between activities
        Map<String, Duration> avgWaitTimeByActivity = computeWaitTimes(eventsByObject);

        return new OcpmPerformanceResult(
            Collections.unmodifiableMap(avgCycleTimeByObjectType),
            Collections.unmodifiableMap(throughputByObjectType),
            Collections.unmodifiableMap(avgWaitTimeByActivity)
        );
    }

    /**
     * Compute average cycle time per object type.
     */
    private static Map<String, Duration> computeCycleTimes(
            List<OcpmInput.OcpmObject> objects,
            Map<String, List<OcpmInput.OcpmEvent>> eventsByObject) {

        Map<String, List<Duration>> cyclesByType = new HashMap<>();

        for (OcpmInput.OcpmObject obj : objects) {
            List<OcpmInput.OcpmEvent> events = eventsByObject
                .getOrDefault(obj.objectId(), new ArrayList<>());

            if (events.size() >= 2) {
                Instant first = events.get(0).timestamp();
                Instant last = events.get(events.size() - 1).timestamp();
                Duration cycleTime = Duration.between(first, last);

                cyclesByType.computeIfAbsent(obj.objectType(), k -> new ArrayList<>())
                    .add(cycleTime);
            }
        }

        // Average cycle times
        Map<String, Duration> result = new HashMap<>();
        for (String objectType : cyclesByType.keySet()) {
            List<Duration> cycles = cyclesByType.get(objectType);
            long totalNanos = cycles.stream()
                .mapToLong(Duration::toNanos)
                .sum();
            long avgNanos = totalNanos / cycles.size();
            result.put(objectType, Duration.ofNanos(avgNanos));
        }

        return result;
    }

    /**
     * Compute throughput (objects/hour) per object type.
     */
    private static Map<String, Long> computeThroughput(
            List<OcpmInput.OcpmObject> objects,
            List<OcpmInput.OcpmEvent> events,
            Map<String, List<OcpmInput.OcpmEvent>> eventsByObject) {

        if (events.isEmpty()) {
            return Collections.emptyMap();
        }

        // Log timespan
        Instant first = events.get(0).timestamp();
        Instant last = events.get(events.size() - 1).timestamp();
        long logSpanHours = Math.max(Duration.between(first, last).toHours(), 1);

        // Count objects per type
        Map<String, Long> objectsPerType = objects.stream()
            .collect(Collectors.groupingBy(
                OcpmInput.OcpmObject::objectType,
                Collectors.counting()
            ));

        // Compute throughput
        Map<String, Long> result = new HashMap<>();
        for (String objectType : objectsPerType.keySet()) {
            long objectCount = objectsPerType.get(objectType);
            long throughput = logSpanHours > 0 ? objectCount / logSpanHours : 0;
            result.put(objectType, Math.max(throughput, 1)); // At least 1 object/hour
        }

        return result;
    }

    /**
     * Compute average wait time between consecutive activities.
     */
    private static Map<String, Duration> computeWaitTimes(
            Map<String, List<OcpmInput.OcpmEvent>> eventsByObject) {

        Map<String, List<Duration>> waitsByEdge = new HashMap<>();

        // For each object's trace, compute wait times between consecutive activities
        for (List<OcpmInput.OcpmEvent> trace : eventsByObject.values()) {
            for (int i = 0; i < trace.size() - 1; i++) {
                String fromActivity = trace.get(i).activity();
                String toActivity = trace.get(i + 1).activity();
                String edgeLabel = fromActivity + " → " + toActivity;

                Duration waitTime = Duration.between(
                    trace.get(i).timestamp(),
                    trace.get(i + 1).timestamp()
                );

                waitsByEdge.computeIfAbsent(edgeLabel, k -> new ArrayList<>())
                    .add(waitTime);
            }
        }

        // Average wait times
        Map<String, Duration> result = new HashMap<>();
        for (String edge : waitsByEdge.keySet()) {
            List<Duration> waits = waitsByEdge.get(edge);
            long totalNanos = waits.stream()
                .mapToLong(Duration::toNanos)
                .sum();
            long avgNanos = totalNanos / waits.size();
            result.put(edge, Duration.ofNanos(avgNanos));
        }

        return result;
    }
}
