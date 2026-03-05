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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous.marketplace;

/**
 * Dimension 5: Latency guarantee expressed as SPARQL query performance profile.
 *
 * <p>Latency in the CONSTRUCT-native marketplace is not an API response time — it is
 * the time for a coordination cycle to complete against a graph of a known size.
 * An agent that can complete its CONSTRUCT queries against 1B triples in under 100ms
 * (consistent with QLever benchmark data) occupies a fundamentally different market
 * position than one requiring 2s — even if both produce correct results.</p>
 *
 * <p>Profiles are declared per graph size because SPARQL performance degrades
 * non-linearly with graph scale. A buyer with a latency-critical transition slot
 * specifies both their maximum acceptable p99 and the expected graph size, and
 * the marketplace matches against agents whose profile at that scale satisfies
 * the constraint.</p>
 *
 * <p>All latency values are in milliseconds. All values must be non-negative and
 * ordered: p50 ≤ p95 ≤ p99.</p>
 *
 * @param graphSizeTriples the number of RDF triples in the coordination graph
 *                         for which these latency values were measured or declared;
 *                         must be &gt; 0
 * @param p50Ms            median latency in milliseconds; must be &gt;= 0
 * @param p95Ms            95th-percentile latency in milliseconds; must be &gt;= p50Ms
 * @param p99Ms            99th-percentile latency in milliseconds; must be &gt;= p95Ms
 * @since YAWL 6.0
 */
public record LatencyProfile(
        long graphSizeTriples,
        long p50Ms,
        long p95Ms,
        long p99Ms) {

    /** Compact constructor: validates ordering and non-negativity constraints. */
    public LatencyProfile {
        if (graphSizeTriples <= 0) {
            throw new IllegalArgumentException(
                "graphSizeTriples must be > 0, was: " + graphSizeTriples);
        }
        if (p50Ms < 0) {
            throw new IllegalArgumentException("p50Ms must be >= 0, was: " + p50Ms);
        }
        if (p95Ms < p50Ms) {
            throw new IllegalArgumentException(
                "p95Ms must be >= p50Ms, was: p50=" + p50Ms + ", p95=" + p95Ms);
        }
        if (p99Ms < p95Ms) {
            throw new IllegalArgumentException(
                "p99Ms must be >= p95Ms, was: p95=" + p95Ms + ", p99=" + p99Ms);
        }
    }

    /**
     * Returns true if this profile's p99 latency satisfies the given maximum.
     *
     * <p>This is the primary predicate used in transition-slot matching for
     * latency-constrained workflow positions. Buyers express their hard deadline
     * as a p99 bound; the marketplace filters agents that cannot meet it.</p>
     *
     * @param maxP99Ms the maximum acceptable p99 latency in milliseconds
     * @return true iff {@code p99Ms <= maxP99Ms}
     */
    public boolean satisfiesLatency(long maxP99Ms) {
        return p99Ms <= maxP99Ms;
    }

    /**
     * A profile representing QLever-class performance: sub-100ms p99 at 1 billion triples.
     *
     * <p>Used as the benchmark reference point for high-performance CONSTRUCT agents.</p>
     *
     * @return a profile at 1B triples with p50=5ms, p95=50ms, p99=95ms
     */
    public static LatencyProfile qleverClass() {
        return new LatencyProfile(1_000_000_000L, 5L, 50L, 95L);
    }

    /**
     * A profile representing standard SPARQL endpoint performance at 10 million triples.
     *
     * @return a profile at 10M triples with p50=80ms, p95=500ms, p99=1200ms
     */
    public static LatencyProfile standardSparql() {
        return new LatencyProfile(10_000_000L, 80L, 500L, 1200L);
    }

    /**
     * A profile for inference-dominant agents where latency is governed by LLM
     * round-trip time rather than graph query performance.
     *
     * @return a profile at 1M triples with p50=800ms, p95=3000ms, p99=8000ms
     */
    public static LatencyProfile inferenceLatency() {
        return new LatencyProfile(1_000_000L, 800L, 3000L, 8000L);
    }
}
