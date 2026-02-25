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

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A multi-dimensional query against the agent marketplace for a specific YAWL transition slot.
 *
 * <p>This is the core query type that makes the marketplace economically interesting.
 * Unlike a string-based capability search, a {@code TransitionSlotQuery} simultaneously
 * constrains all five marketplace dimensions:</p>
 *
 * <ol>
 *   <li><b>Capability</b> — optional keyword filter on domain name</li>
 *   <li><b>Ontological</b> — required namespaces and SPARQL patterns the agent must cover</li>
 *   <li><b>Workflow</b> — required WCP pattern code, input and output token types</li>
 *   <li><b>Economic</b> — maximum acceptable price per coordination cycle</li>
 *   <li><b>Temporal</b> — maximum acceptable p99 latency at a target graph size</li>
 * </ol>
 *
 * <p>The marketplace evaluates this query via predicate composition — each dimension
 * is an independent filter, and a listing must satisfy ALL active predicates to be
 * returned. Empty/absent constraints are treated as unconstrained (pass-all).</p>
 *
 * <p>Only live agents (heartbeat within {@code staleness}) are considered.
 * The default staleness is 5 minutes.</p>
 *
 * <p>Build queries via the fluent {@link Builder}:</p>
 * <pre>{@code
 * TransitionSlotQuery query = TransitionSlotQuery.builder()
 *     .requireNamespace("http://www.yawlfoundation.org/yawlschema#")
 *     .requireSparqlPattern("?task yawls:hasDecomposition ?decomp")
 *     .requireWcpPattern("WCP-1")
 *     .requireInputType("yawls:WorkItem")
 *     .maxCostPerCycle(0.5)
 *     .maxP99LatencyMs(100)
 *     .atGraphSize(1_000_000_000L)
 *     .build();
 * }</pre>
 *
 * @since YAWL 6.0
 */
public final class TransitionSlotQuery {

    private final Optional<String> capabilityKeyword;
    private final List<String> requiredNamespaces;
    private final List<String> requiredSparqlPatterns;
    private final Optional<String> requiredWcpPattern;
    private final List<String> requiredInputTokenTypes;
    private final List<String> requiredOutputTokenTypes;
    private final double maxCostPerCycle;
    private final long maxP99LatencyMs;
    private final long targetGraphSizeTriples;
    private final Duration staleness;

    private TransitionSlotQuery(Builder b) {
        this.capabilityKeyword = b.capabilityKeyword;
        this.requiredNamespaces = List.copyOf(b.requiredNamespaces);
        this.requiredSparqlPatterns = List.copyOf(b.requiredSparqlPatterns);
        this.requiredWcpPattern = b.requiredWcpPattern;
        this.requiredInputTokenTypes = List.copyOf(b.requiredInputTokenTypes);
        this.requiredOutputTokenTypes = List.copyOf(b.requiredOutputTokenTypes);
        this.maxCostPerCycle = b.maxCostPerCycle;
        this.maxP99LatencyMs = b.maxP99LatencyMs;
        this.targetGraphSizeTriples = b.targetGraphSizeTriples;
        this.staleness = b.staleness;
    }

    /**
     * Returns true if the given listing satisfies all predicates in this query.
     *
     * <p>Evaluation order mirrors the CHATMAN circuit: Ψ (ontological) → Λ (workflow)
     * → H (cost) → Q (latency), short-circuiting on the first failure. Liveness
     * is checked first as the cheapest predicate.</p>
     *
     * @param listing the marketplace listing to evaluate
     * @return true iff the listing satisfies every active predicate and is live
     */
    public boolean matches(AgentMarketplaceListing listing) {
        Objects.requireNonNull(listing, "listing must not be null");

        // Liveness: cheapest check first
        if (!listing.isLive(staleness)) {
            return false;
        }

        AgentMarketplaceSpec spec = listing.spec();

        // Dimension 1: capability keyword (optional)
        if (capabilityKeyword.isPresent()) {
            String kw = capabilityKeyword.get().toLowerCase();
            String domain = spec.capability().domainName().toLowerCase();
            String desc = spec.capability().description().toLowerCase();
            if (!domain.contains(kw) && !desc.contains(kw)) {
                return false;
            }
        }

        // Dimension 2: ontological coverage
        OntologicalCoverage coverage = spec.ontologicalCoverage();
        if (!coverage.coversAllNamespaces(requiredNamespaces)) {
            return false;
        }
        if (!coverage.matchesAllPatterns(requiredSparqlPatterns)) {
            return false;
        }

        // Dimension 3: workflow transition compatibility
        if (requiredWcpPattern.isPresent() || !requiredInputTokenTypes.isEmpty()
                || !requiredOutputTokenTypes.isEmpty()) {
            boolean contractSatisfied = spec.transitionContracts().stream()
                    .anyMatch(c -> satisfiesContract(c));
            if (!contractSatisfied) {
                return false;
            }
        }

        // Dimension 4: economic cost
        if (!spec.costProfile().isWithinBudget(maxCostPerCycle)) {
            return false;
        }

        // Dimension 5: latency at target graph size
        if (maxP99LatencyMs < Long.MAX_VALUE) {
            Optional<LatencyProfile> profile = spec.latencyAt(targetGraphSizeTriples);
            if (profile.isPresent() && !profile.get().satisfiesLatency(maxP99LatencyMs)) {
                return false;
            }
            // If no latency profile is declared, the agent passes the latency predicate —
            // undeclared latency is treated as unconstrained, not as a disqualifier.
        }

        return true;
    }

    private boolean satisfiesContract(WorkflowTransitionContract contract) {
        if (requiredWcpPattern.isPresent()
                && !contract.supportsPattern(requiredWcpPattern.get())) {
            return false;
        }
        if (!contract.acceptsInputTypes(requiredInputTokenTypes)) {
            return false;
        }
        if (!contract.producesOutputTypes(requiredOutputTokenTypes)) {
            return false;
        }
        return true;
    }

    // --- Accessors ---

    /** Returns the optional capability keyword filter. */
    public Optional<String> capabilityKeyword() { return capabilityKeyword; }

    /** Returns the required RDF namespaces. */
    public List<String> requiredNamespaces() { return requiredNamespaces; }

    /** Returns the required SPARQL BGP patterns. */
    public List<String> requiredSparqlPatterns() { return requiredSparqlPatterns; }

    /** Returns the required WCP pattern code, if any. */
    public Optional<String> requiredWcpPattern() { return requiredWcpPattern; }

    /** Returns the required input place token types. */
    public List<String> requiredInputTokenTypes() { return requiredInputTokenTypes; }

    /** Returns the required output place token types. */
    public List<String> requiredOutputTokenTypes() { return requiredOutputTokenTypes; }

    /** Returns the maximum acceptable price per coordination cycle. */
    public double maxCostPerCycle() { return maxCostPerCycle; }

    /** Returns the maximum acceptable p99 latency in milliseconds. */
    public long maxP99LatencyMs() { return maxP99LatencyMs; }

    /** Returns the target graph size in triples for latency evaluation. */
    public long targetGraphSizeTriples() { return targetGraphSizeTriples; }

    /** Returns the maximum acceptable heartbeat staleness. */
    public Duration staleness() { return staleness; }

    /**
     * Returns a builder for constructing a {@code TransitionSlotQuery}.
     *
     * @return a new builder with unconstrained defaults
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@code TransitionSlotQuery}. */
    public static final class Builder {
        private Optional<String> capabilityKeyword = Optional.empty();
        private final java.util.ArrayList<String> requiredNamespaces = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> requiredSparqlPatterns = new java.util.ArrayList<>();
        private Optional<String> requiredWcpPattern = Optional.empty();
        private final java.util.ArrayList<String> requiredInputTokenTypes = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> requiredOutputTokenTypes = new java.util.ArrayList<>();
        private double maxCostPerCycle = Double.MAX_VALUE;
        private long maxP99LatencyMs = Long.MAX_VALUE;
        private long targetGraphSizeTriples = 1_000_000L;
        private Duration staleness = Duration.ofMinutes(5);

        private Builder() {}

        /** Filters by capability domain keyword (case-insensitive substring match). */
        public Builder capabilityKeyword(String keyword) {
            this.capabilityKeyword = Optional.of(
                Objects.requireNonNull(keyword, "keyword must not be null"));
            return this;
        }

        /** Requires the agent to declare this RDF namespace. */
        public Builder requireNamespace(String namespace) {
            requiredNamespaces.add(
                Objects.requireNonNull(namespace, "namespace must not be null"));
            return this;
        }

        /** Requires the agent to match this SPARQL BGP pattern. */
        public Builder requireSparqlPattern(String pattern) {
            requiredSparqlPatterns.add(
                Objects.requireNonNull(pattern, "pattern must not be null"));
            return this;
        }

        /** Requires the agent to support this WCP pattern code. */
        public Builder requireWcpPattern(String wcpCode) {
            this.requiredWcpPattern = Optional.of(
                Objects.requireNonNull(wcpCode, "wcpCode must not be null"));
            return this;
        }

        /** Requires the agent to accept this input place token type. */
        public Builder requireInputType(String type) {
            requiredInputTokenTypes.add(
                Objects.requireNonNull(type, "type must not be null"));
            return this;
        }

        /** Requires the agent to produce this output place token type. */
        public Builder requireOutputType(String type) {
            requiredOutputTokenTypes.add(
                Objects.requireNonNull(type, "type must not be null"));
            return this;
        }

        /** Sets the maximum acceptable price per coordination cycle. */
        public Builder maxCostPerCycle(double maxCost) {
            if (maxCost < 0) throw new IllegalArgumentException("maxCost must be >= 0");
            this.maxCostPerCycle = maxCost;
            return this;
        }

        /** Sets the maximum acceptable p99 latency in milliseconds. */
        public Builder maxP99LatencyMs(long maxLatencyMs) {
            if (maxLatencyMs < 0) throw new IllegalArgumentException("maxLatencyMs must be >= 0");
            this.maxP99LatencyMs = maxLatencyMs;
            return this;
        }

        /** Sets the target graph size in triples for latency evaluation. */
        public Builder atGraphSize(long graphSizeTriples) {
            if (graphSizeTriples <= 0)
                throw new IllegalArgumentException("graphSizeTriples must be > 0");
            this.targetGraphSizeTriples = graphSizeTriples;
            return this;
        }

        /** Sets the maximum acceptable heartbeat staleness (default: 5 minutes). */
        public Builder staleness(Duration staleness) {
            this.staleness = Objects.requireNonNull(staleness, "staleness must not be null");
            return this;
        }

        /** Builds the {@code TransitionSlotQuery}. */
        public TransitionSlotQuery build() {
            return new TransitionSlotQuery(this);
        }
    }
}
