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

import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The five-dimensional marketplace specification for a CONSTRUCT-native agent.
 *
 * <p>This record is the Java materialization of {@code ggen.toml} — making the
 * TOML manifest the canonical publication artifact for marketplace registration.
 * Where current agent marketplaces accept a README and an API key, this
 * architecture accepts a formally verifiable specification of what the agent
 * constructs and under what ontological conditions.</p>
 *
 * <p>The five dimensions:</p>
 * <ol>
 *   <li><b>Capability</b> ({@link AgentCapability}) — what the agent does,
 *       in domain language. The shallow dimension that existing registries
 *       already capture.</li>
 *   <li><b>Ontological coverage</b> ({@link OntologicalCoverage}) — which RDF
 *       namespaces the agent formally understands, which SPARQL patterns it can
 *       match, and which CONSTRUCT templates it produces. Machine-verifiable.</li>
 *   <li><b>Workflow fit</b> ({@link WorkflowTransitionContract}) — YAWL
 *       transition-slot compatibility: input/output place token types and WCP
 *       pattern support, enabling soundness-preserving slot matching.</li>
 *   <li><b>Economic cost</b> ({@link CoordinationCostProfile}) — CONSTRUCT
 *       queries per call, LLM inference ratio, and normalized price per
 *       coordination cycle. Makes the build-vs-infer tradeoff priceable.</li>
 *   <li><b>Latency</b> ({@link LatencyProfile}) — p50/p95/p99 at declared
 *       graph sizes. Multiple profiles can be declared for different scales.</li>
 * </ol>
 *
 * <p>The {@code ggenTomlHash} field creates an auditable link between the
 * published spec and the generative artifact that produced the agent's code.
 * When an agent's implementation changes, its ggen.toml hash changes, and the
 * marketplace listing must be re-published — preventing stale specifications.</p>
 *
 * @param specVersion          semantic version of this spec (e.g. {@code "1.0.0"});
 *                             never null or blank
 * @param capability           dimension 1: domain capability declaration;
 *                             never null
 * @param ontologicalCoverage  dimension 2: formal ontology coverage;
 *                             never null
 * @param transitionContracts  dimension 3: YAWL transition compatibility;
 *                             never null, may be empty (agent fits no slot constraints)
 * @param costProfile          dimension 4: economic coordination cost;
 *                             never null
 * @param latencyProfiles      dimension 5: latency at declared graph sizes;
 *                             never null, may be empty (latency unconstrained)
 * @param ggenTomlHash         SHA-256 hex digest of the {@code ggen.toml} this
 *                             spec was derived from; empty string if not derived
 *                             from ggen.toml
 * @since YAWL 6.0
 */
public record AgentMarketplaceSpec(
        String specVersion,
        AgentCapability capability,
        OntologicalCoverage ontologicalCoverage,
        List<WorkflowTransitionContract> transitionContracts,
        CoordinationCostProfile costProfile,
        List<LatencyProfile> latencyProfiles,
        String ggenTomlHash) {

    /** Compact constructor: validates required fields and creates immutable copies. */
    public AgentMarketplaceSpec {
        Objects.requireNonNull(specVersion, "specVersion is required");
        Objects.requireNonNull(capability, "capability is required");
        Objects.requireNonNull(ontologicalCoverage, "ontologicalCoverage is required");
        Objects.requireNonNull(transitionContracts, "transitionContracts is required");
        Objects.requireNonNull(costProfile, "costProfile is required");
        Objects.requireNonNull(latencyProfiles, "latencyProfiles is required");
        Objects.requireNonNull(ggenTomlHash, "ggenTomlHash is required");

        if (specVersion.isBlank()) {
            throw new IllegalArgumentException("specVersion must not be blank");
        }

        transitionContracts = List.copyOf(transitionContracts);
        latencyProfiles = List.copyOf(latencyProfiles);
    }

    /**
     * Returns the most restrictive (worst-case) latency profile across all declared
     * graph sizes, or empty if no latency profiles are declared.
     *
     * <p>Used by marketplace buyers who want a conservative latency estimate
     * without specifying a particular graph size.</p>
     *
     * @return the profile with the highest p99, or empty if none declared
     */
    public Optional<LatencyProfile> worstCaseLatency() {
        return latencyProfiles.stream()
                .max(java.util.Comparator.comparingLong(LatencyProfile::p99Ms));
    }

    /**
     * Returns the latency profile closest to the given graph size, or empty if
     * no profiles are declared.
     *
     * <p>Selects the profile whose {@code graphSizeTriples} is nearest to the
     * requested size, enabling interpolated latency estimation.</p>
     *
     * @param graphSizeTriples the target graph size in triples
     * @return the nearest latency profile, or empty if none declared
     */
    public Optional<LatencyProfile> latencyAt(long graphSizeTriples) {
        return latencyProfiles.stream()
                .min(java.util.Comparator.comparingLong(
                        p -> Math.abs(p.graphSizeTriples() - graphSizeTriples)));
    }

    /**
     * Returns true if this spec was derived from a ggen.toml artifact.
     *
     * @return true iff {@code ggenTomlHash} is non-blank
     */
    public boolean isDerivedFromGgenToml() {
        return !ggenTomlHash.isBlank();
    }

    /**
     * Returns a builder for constructing an {@code AgentMarketplaceSpec}.
     *
     * @param version   the spec version
     * @param capability the agent's domain capability
     * @return a new builder
     */
    public static Builder builder(String version, AgentCapability capability) {
        return new Builder(version, capability);
    }

    /** Builder for {@code AgentMarketplaceSpec}. */
    public static final class Builder {
        private final String specVersion;
        private final AgentCapability capability;
        private OntologicalCoverage ontologicalCoverage = OntologicalCoverage.empty();
        private final java.util.ArrayList<WorkflowTransitionContract> contracts =
                new java.util.ArrayList<>();
        private CoordinationCostProfile costProfile =
                CoordinationCostProfile.pureInference(1.0);
        private final java.util.ArrayList<LatencyProfile> latencyProfiles =
                new java.util.ArrayList<>();
        private String ggenTomlHash = "";

        private Builder(String specVersion, AgentCapability capability) {
            this.specVersion = Objects.requireNonNull(specVersion, "specVersion is required");
            this.capability = Objects.requireNonNull(capability, "capability is required");
        }

        /** Sets the ontological coverage (dimension 2). */
        public Builder ontologicalCoverage(OntologicalCoverage coverage) {
            this.ontologicalCoverage = Objects.requireNonNull(coverage);
            return this;
        }

        /** Adds a workflow transition contract (dimension 3). */
        public Builder transitionContract(WorkflowTransitionContract contract) {
            contracts.add(Objects.requireNonNull(contract));
            return this;
        }

        /** Sets the coordination cost profile (dimension 4). */
        public Builder costProfile(CoordinationCostProfile profile) {
            this.costProfile = Objects.requireNonNull(profile);
            return this;
        }

        /** Adds a latency profile at a specific graph scale (dimension 5). */
        public Builder latencyProfile(LatencyProfile profile) {
            latencyProfiles.add(Objects.requireNonNull(profile));
            return this;
        }

        /** Sets the SHA-256 hash of the originating ggen.toml. */
        public Builder ggenTomlHash(String hash) {
            this.ggenTomlHash = Objects.requireNonNull(hash);
            return this;
        }

        /** Builds the {@code AgentMarketplaceSpec}. */
        public AgentMarketplaceSpec build() {
            return new AgentMarketplaceSpec(
                    specVersion, capability, ontologicalCoverage,
                    contracts, costProfile, latencyProfiles, ggenTomlHash);
        }
    }
}
