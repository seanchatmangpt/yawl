/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.util.java25;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;

/**
 * Latency and throughput metrics using Java 25 {@link Gatherer} API (JEP 485).
 *
 * <p>This is the first use of {@code java.util.stream.Gatherers} in the YAWL codebase.
 * Gatherers are intermediate stream operations with mutable state, enabling patterns
 * not expressible with standard {@code map/filter/reduce}.
 *
 * <p>Usage examples:
 * <pre>{@code
 * // Compute percentile snapshot from latency samples
 * PercentileSnapshot snap = latencyList.stream()
 *         .gather(StreamMetrics.percentiles())
 *         .findFirst()
 *         .orElseThrow();
 *
 * // Compute rolling throughput rates from completion timestamps (nanoseconds)
 * List<Double> rates = timestamps.stream()
 *         .gather(StreamMetrics.rollingThroughputPerSec(20))
 *         .toList();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class StreamMetrics {

    private StreamMetrics() {
        throw new UnsupportedOperationException("StreamMetrics is a utility class");
    }

    /**
     * Immutable snapshot of latency percentiles.
     *
     * @param count  total number of samples
     * @param p50    50th percentile (median) in the same unit as inputs
     * @param p95    95th percentile
     * @param p99    99th percentile
     * @param p99_9  99.9th percentile
     */
    public record PercentileSnapshot(long count, long p50, long p95, long p99, long p99_9) {

        @Override
        public String toString() {
            return String.format("PercentileSnapshot{count=%d, p50=%d, p95=%d, p99=%d, p99.9=%d}",
                    count, p50, p95, p99, p99_9);
        }
    }

    /**
     * Custom sequential {@link Gatherer} that accumulates all {@code Long} samples and
     * emits exactly one {@link PercentileSnapshot} at stream completion.
     *
     * <p>Accumulator state: an {@code ArrayList<Long>} that collects samples.
     * Finisher: sorts the array and extracts p50/p95/p99/p99.9 via rank-order indexing.
     *
     * <p>This gatherer is <em>sequential</em> (not combiner-capable) because percentile
     * computation requires a globally sorted view of all samples.
     *
     * @return a sequential Gatherer that emits one PercentileSnapshot at end of stream
     */
    public static Gatherer<Long, ?, PercentileSnapshot> percentiles() {
        Supplier<ArrayList<Long>> initializer = ArrayList::new;

        Gatherer.Integrator<ArrayList<Long>, Long, PercentileSnapshot> integrator =
                Gatherer.Integrator.ofGreedy((state, element, downstream) -> {
                    state.add(element);
                    return true;
                });

        BiConsumer<ArrayList<Long>, Gatherer.Downstream<? super PercentileSnapshot>> finisher =
                (state, downstream) -> {
                    if (state.isEmpty()) {
                        downstream.push(new PercentileSnapshot(0L, 0L, 0L, 0L, 0L));
                        return;
                    }
                    long[] sorted = state.stream().mapToLong(Long::longValue).sorted().toArray();
                    downstream.push(new PercentileSnapshot(
                            sorted.length,
                            pct(sorted, 50.0),
                            pct(sorted, 95.0),
                            pct(sorted, 99.0),
                            pct(sorted, 99.9)
                    ));
                };

        return Gatherer.ofSequential(initializer, integrator, finisher);
    }

    /**
     * Sliding-window throughput gatherer built on {@link Gatherers#windowSliding(int)}.
     *
     * <p>Each input element is a nanosecond timestamp of a completed event.
     * Each output {@code Double} is the throughput rate (events per second) of the
     * most recent {@code windowSize} events, computed as:
     * <pre>
     *     rate = (windowSize - 1) / (lastTimestamp - firstTimestamp) * 1e9
     * </pre>
     *
     * <p>Windows of size {@code < 2} emit rate {@code 0.0} (cannot compute elapsed time).
     *
     * @param windowSize number of events per sliding window (must be ≥ 2)
     * @return a Gatherer that emits one {@code Double} rate per input element after the
     *         first {@code windowSize - 1} elements have been consumed
     * @throws IllegalArgumentException if windowSize &lt; 2
     */
    public static Gatherer<Long, ?, Double> rollingThroughputPerSec(int windowSize) {
        if (windowSize < 2) {
            throw new IllegalArgumentException("windowSize must be >= 2, got " + windowSize);
        }
        return Gatherers.<Long>windowSliding(windowSize)
                .andThen(Gatherer.ofSequential(
                        () -> new Object(),   // no state needed
                        Gatherer.Integrator.ofGreedy((state, window, downstream) -> {
                            if (window.size() < 2) {
                                downstream.push(0.0);
                            } else {
                                long first = window.getFirst();
                                long last = window.getLast();
                                long elapsedNs = last - first;
                                double rate = elapsedNs > 0
                                        ? (double) (window.size() - 1) / elapsedNs * 1_000_000_000.0
                                        : 0.0;
                                downstream.push(rate);
                            }
                            return true;
                        })
                ));
    }

    /**
     * Bulk convenience method: compute a {@link PercentileSnapshot} from a {@code List}
     * without constructing an explicit stream pipeline.
     *
     * @param latencies list of latency samples (any consistent unit: ns, ms, etc.)
     * @return snapshot; returns all-zero snapshot for empty input
     */
    public static PercentileSnapshot summarize(List<Long> latencies) {
        if (latencies == null || latencies.isEmpty()) {
            return new PercentileSnapshot(0L, 0L, 0L, 0L, 0L);
        }
        return latencies.stream()
                .gather(percentiles())
                .findFirst()
                .orElse(new PercentileSnapshot(0L, 0L, 0L, 0L, 0L));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static long pct(long[] sorted, double p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }
}
