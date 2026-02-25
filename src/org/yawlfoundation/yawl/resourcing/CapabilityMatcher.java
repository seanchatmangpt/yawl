/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.resourcing;

import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplace;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceListing;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.TransitionSlotQuery;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Matches YAWL work items to AI agents in the {@link AgentMarketplace}.
 *
 * <p>When a work item is enabled, the engine asks the {@code CapabilityMatcher} whether
 * any live marketplace agent can handle it. The matcher builds a
 * {@link TransitionSlotQuery} from the work item's task name, applies economic and
 * latency constraints, and returns the result as a sealed {@link RoutingDecision}:
 * <ul>
 *   <li>{@link RoutingDecision.AgentRoute} — a matching agent was found; delegate to it.</li>
 *   <li>{@link RoutingDecision.HumanRoute} — no agent met all constraints; normal
 *       human task allocation proceeds.</li>
 * </ul>
 *
 * <p>The decision is a point-in-time snapshot. The marketplace is queried on each call
 * to {@link #match(YWorkItem)}; heartbeat staleness is enforced by the query's
 * {@code staleness} parameter (default: 5 minutes, as per
 * {@link AgentMarketplace#DEFAULT_STALENESS}).</p>
 *
 * <p>Thread safety: this class is immutable after construction and safe for concurrent use.
 * The underlying {@link AgentMarketplace} is also thread-safe.</p>
 *
 * @since YAWL 6.0
 */
public final class CapabilityMatcher {

    private final AgentMarketplace marketplace;
    private final double maxCostPerCycle;
    private final long maxP99LatencyMs;
    private final Duration staleness;

    /**
     * Constructs a matcher with explicit economic and temporal constraints.
     *
     * @param marketplace      the agent marketplace to query; must not be null
     * @param maxCostPerCycle  the maximum acceptable normalized price per cycle
     *                         (use {@code Double.MAX_VALUE} for unconstrained)
     * @param maxP99LatencyMs  the maximum acceptable p99 response latency in ms
     *                         (use {@code Long.MAX_VALUE} for unconstrained)
     * @param staleness        the maximum acceptable heartbeat age for liveness;
     *                         must not be null
     * @throws IllegalArgumentException if marketplace or staleness is null,
     *                                  or if cost/latency bounds are negative
     */
    public CapabilityMatcher(AgentMarketplace marketplace,
                             double maxCostPerCycle,
                             long maxP99LatencyMs,
                             Duration staleness) {
        Objects.requireNonNull(marketplace, "marketplace must not be null");
        Objects.requireNonNull(staleness, "staleness must not be null");
        if (maxCostPerCycle < 0) {
            throw new IllegalArgumentException("maxCostPerCycle must be >= 0");
        }
        if (maxP99LatencyMs < 0) {
            throw new IllegalArgumentException("maxP99LatencyMs must be >= 0");
        }
        this.marketplace = marketplace;
        this.maxCostPerCycle = maxCostPerCycle;
        this.maxP99LatencyMs = maxP99LatencyMs;
        this.staleness = staleness;
    }

    /**
     * Matches a work item to a marketplace agent.
     *
     * <p>Builds a {@link TransitionSlotQuery} from the work item's task name
     * (as a capability keyword), applies the configured cost/latency bounds,
     * and queries the marketplace. Returns an {@link RoutingDecision.AgentRoute}
     * for the first (cheapest, fastest) result, or {@link RoutingDecision.HumanRoute}
     * if no agent satisfies all constraints.</p>
     *
     * @param workItem the enabled work item to route; must not be null
     * @return the routing decision; never null
     * @throws IllegalArgumentException if workItem is null
     */
    public RoutingDecision match(YWorkItem workItem) {
        Objects.requireNonNull(workItem, "workItem must not be null");

        TransitionSlotQuery query = buildQuery(workItem);
        List<AgentMarketplaceListing> candidates = marketplace.findForTransitionSlot(query);

        if (candidates.isEmpty()) {
            return new RoutingDecision.HumanRoute();
        }

        AgentMarketplaceListing selected = candidates.get(0);
        String rationale = buildRationale(selected, workItem.getTaskID());
        return new RoutingDecision.AgentRoute(selected, rationale);
    }

    /**
     * Builds a {@link TransitionSlotQuery} from a work item's task name.
     *
     * <p>Package-private for testing without constructing a live marketplace.</p>
     *
     * @param workItem the work item whose task name seeds the capability keyword
     * @return a fully configured query
     */
    TransitionSlotQuery buildQuery(YWorkItem workItem) {
        return TransitionSlotQuery.builder()
                .capabilityKeyword(workItem.getTaskID())
                .maxCostPerCycle(maxCostPerCycle)
                .maxP99LatencyMs(maxP99LatencyMs)
                .staleness(staleness)
                .build();
    }

    /**
     * Builds a human-readable routing rationale string.
     *
     * <p>Package-private for testing without constructing a live marketplace.</p>
     *
     * @param listing  the selected marketplace listing
     * @param taskId   the work item's task ID
     * @return a non-null, non-blank rationale string
     */
    String buildRationale(AgentMarketplaceListing listing, String taskId) {
        return "Task '%s' routed to agent '%s' [cost=%.4f, endpoint=%s]".formatted(
                taskId,
                listing.agentInfo().getName(),
                listing.spec().costProfile().basePricePerCycle(),
                listing.agentInfo().getEndpointUrl());
    }
}
