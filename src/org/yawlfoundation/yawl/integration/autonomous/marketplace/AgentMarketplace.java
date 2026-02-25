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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The CONSTRUCT-native agent marketplace: a formally typed, multi-dimensional exchange
 * where agents, capabilities, and workflows are expressed in the same semantic language.
 *
 * <p>This marketplace answers questions that no current agent registry can approach:</p>
 * <ul>
 *   <li>Which agents cover ALL of these RDF namespaces?</li>
 *   <li>Which agents can fill THIS YAWL transition slot while preserving soundness?</li>
 *   <li>Which agents can handle this workflow pattern for under N price units?</li>
 *   <li>Which agents meet a 100ms p99 latency SLA at 1 billion triples?</li>
 * </ul>
 *
 * <p>The core query is {@link #findForTransitionSlot(TransitionSlotQuery)}, which
 * performs predicate composition across all five marketplace dimensions simultaneously.
 * Results are ordered by cost (ascending) then latency (ascending) by default —
 * buyers get the cheapest, fastest agents that satisfy their constraints.</p>
 *
 * <p>Reputation emerges naturally: agents that consistently produce well-formed
 * output and maintain live heartbeats appear in more query results. Agents that
 * go offline drop out of results without any additional infrastructure.</p>
 *
 * <p>Thread safety: all operations are safe for concurrent use. The underlying
 * store is a {@link ConcurrentHashMap}; listings are immutable records. Heartbeat
 * updates use compare-and-replace semantics to prevent stale writes.</p>
 *
 * @since YAWL 6.0
 */
public final class AgentMarketplace {

    /** Default staleness window used when checking liveness without an explicit query. */
    static final Duration DEFAULT_STALENESS = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, AgentMarketplaceListing> listings =
            new ConcurrentHashMap<>();

    /**
     * Publishes an agent listing to the marketplace, replacing any prior listing
     * for the same agent ID.
     *
     * <p>Re-publishing is idempotent: publishing the same listing twice produces
     * the same observable state. Agents should re-publish when their spec changes
     * (e.g. after a ggen.toml rebuild that changes the hash).</p>
     *
     * @param listing the listing to publish; must not be null
     * @throws IllegalArgumentException if the agent ID is null or blank
     */
    public void publish(AgentMarketplaceListing listing) {
        Objects.requireNonNull(listing, "listing must not be null");
        String id = listing.agentInfo().getId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Agent ID must not be null or blank");
        }
        listings.put(id, listing);
    }

    /**
     * Removes an agent's listing from the marketplace.
     *
     * <p>After unpublishing, the agent will not appear in any marketplace query.
     * Idempotent: unpublishing an unknown ID is a no-op.</p>
     *
     * @param agentId the ID of the agent to remove
     */
    public void unpublish(String agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        listings.remove(agentId);
    }

    /**
     * Records a heartbeat for the given agent, extending its liveness window.
     *
     * <p>Agents must call this periodically to remain visible in marketplace queries.
     * If no listing exists for the agent ID, this is a no-op (the agent must
     * {@link #publish} first).</p>
     *
     * @param agentId     the ID of the agent updating its heartbeat
     * @param heartbeatAt the current timestamp
     */
    public void heartbeat(String agentId, Instant heartbeatAt) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(heartbeatAt, "heartbeatAt must not be null");
        listings.computeIfPresent(agentId,
                (id, existing) -> existing.withHeartbeat(heartbeatAt));
    }

    /**
     * The primary marketplace query: find all live agents that satisfy a
     * multi-dimensional transition slot constraint.
     *
     * <p>Results are ordered by cost ascending, then p99 latency ascending,
     * then agent ID ascending (for deterministic ordering in ties). Buyers
     * typically take the first result; the ordering ensures the cheapest,
     * fastest qualifying agent is returned first.</p>
     *
     * <p>Only agents whose heartbeat is within the query's staleness window
     * are considered. Agents with no declared latency profile are not penalised
     * on the latency dimension — their latency is treated as unconstrained.</p>
     *
     * @param query the multi-dimensional slot query; must not be null
     * @return an immutable ordered list of matching listings; never null, may be empty
     */
    public List<AgentMarketplaceListing> findForTransitionSlot(TransitionSlotQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return listings.values().stream()
                .filter(query::matches)
                .sorted(byEconomicOrder())
                .toList();
    }

    /**
     * Finds all live agents that declare the given RDF namespace IRI.
     *
     * <p>Useful for discovering agents capable of operating over a particular
     * ontology without specifying a full transition slot query.</p>
     *
     * @param namespaceIri the RDF namespace IRI to search for
     * @return matching live listings, ordered by cost then latency
     */
    public List<AgentMarketplaceListing> findByNamespace(String namespaceIri) {
        Objects.requireNonNull(namespaceIri, "namespaceIri must not be null");
        return liveListings()
                .filter(l -> l.spec().ontologicalCoverage()
                        .declaredNamespaces().contains(namespaceIri))
                .sorted(byEconomicOrder())
                .toList();
    }

    /**
     * Finds all live agents whose cost per coordination cycle is at or below the limit.
     *
     * @param maxCostPerCycle the maximum acceptable normalized price
     * @return matching live listings, ordered by cost ascending
     */
    public List<AgentMarketplaceListing> findByMaxCost(double maxCostPerCycle) {
        return liveListings()
                .filter(l -> l.spec().costProfile().isWithinBudget(maxCostPerCycle))
                .sorted(Comparator.comparingDouble(
                        l -> l.spec().costProfile().basePricePerCycle()))
                .toList();
    }

    /**
     * Finds all live agents whose worst-case p99 latency is at or below the limit.
     *
     * <p>Agents with no declared latency profiles are excluded from this query —
     * they cannot make a verifiable latency claim.</p>
     *
     * @param maxP99LatencyMs the maximum acceptable p99 latency in milliseconds
     * @return matching live listings with declared latency, ordered by p99 ascending
     */
    public List<AgentMarketplaceListing> findByMaxLatency(long maxP99LatencyMs) {
        return liveListings()
                .filter(l -> l.spec().worstCaseLatency()
                        .map(p -> p.satisfiesLatency(maxP99LatencyMs))
                        .orElse(false))
                .sorted(Comparator.comparingLong(l ->
                        l.spec().worstCaseLatency().map(LatencyProfile::p99Ms).orElse(Long.MAX_VALUE)))
                .toList();
    }

    /**
     * Finds all live agents that declare support for the given WCP pattern code.
     *
     * @param wcpCode the van der Aalst WCP code (e.g. {@code "WCP-17"})
     * @return matching live listings, ordered by cost then latency
     */
    public List<AgentMarketplaceListing> findByWcpPattern(String wcpCode) {
        Objects.requireNonNull(wcpCode, "wcpCode must not be null");
        return liveListings()
                .filter(l -> l.spec().transitionContracts().stream()
                        .anyMatch(c -> c.supportsPattern(wcpCode)))
                .sorted(byEconomicOrder())
                .toList();
    }

    /**
     * Returns the listing for a specific agent ID, if present and live.
     *
     * @param agentId     the agent ID to look up
     * @param maxStaleness the maximum acceptable heartbeat age
     * @return the listing if present and live, or empty
     */
    public Optional<AgentMarketplaceListing> findById(String agentId, Duration maxStaleness) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(maxStaleness, "maxStaleness must not be null");
        return Optional.ofNullable(listings.get(agentId))
                .filter(l -> l.isLive(maxStaleness));
    }

    /**
     * Returns all currently live listings using the default staleness window.
     *
     * @return an immutable list of live listings; never null
     */
    public List<AgentMarketplaceListing> allLiveListings() {
        return liveListings().toList();
    }

    /**
     * Returns the total number of published listings (including offline agents).
     *
     * @return total listing count
     */
    public int size() {
        return listings.size();
    }

    /**
     * Returns the number of currently live listings.
     *
     * @return live listing count
     */
    public long liveCount() {
        return liveListings().count();
    }

    // --- Private helpers ---

    private Stream<AgentMarketplaceListing> liveListings() {
        return listings.values().stream()
                .filter(l -> l.isLive(DEFAULT_STALENESS));
    }

    /**
     * Default result ordering: cost ascending, then worst-case p99 ascending,
     * then agent ID ascending for determinism.
     */
    private static Comparator<AgentMarketplaceListing> byEconomicOrder() {
        return Comparator
                .comparingDouble((AgentMarketplaceListing l) ->
                        l.spec().costProfile().basePricePerCycle())
                .thenComparingLong(l ->
                        l.spec().worstCaseLatency()
                                .map(LatencyProfile::p99Ms)
                                .orElse(Long.MAX_VALUE))
                .thenComparing(l -> l.agentInfo().getId());
    }
}
