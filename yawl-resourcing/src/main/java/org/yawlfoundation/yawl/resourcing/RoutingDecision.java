/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.resourcing;

import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceListing;

/**
 * The result of a capability matching decision for a YAWL work item.
 *
 * <p>A {@code RoutingDecision} is a sealed type with exactly two variants:
 * <ul>
 *   <li>{@link AgentRoute} — the work item should be delegated to an AI agent found in
 *       the marketplace, along with a human-readable rationale.</li>
 *   <li>{@link HumanRoute} — no suitable agent was found; normal human task allocation
 *       proceeds uninterrupted.</li>
 * </ul>
 *
 * <p>There is no silent fallback. If agent dispatch is selected ({@link AgentRoute}) and
 * the dispatch subsequently fails, {@link AgentRoutingException} is thrown — the system
 * never silently demotes an agent route to a human route.
 *
 * @since YAWL 6.0
 */
public sealed interface RoutingDecision
        permits RoutingDecision.AgentRoute, RoutingDecision.HumanRoute {

    /**
     * Route to an AI agent found in the marketplace.
     *
     * @param listing   the marketplace listing for the selected agent
     * @param rationale a human-readable explanation of why this agent was selected
     */
    record AgentRoute(AgentMarketplaceListing listing, String rationale)
            implements RoutingDecision {

        /** Compact constructor enforces non-null invariants. */
        public AgentRoute {
            if (listing == null) {
                throw new IllegalArgumentException("listing must not be null");
            }
            if (rationale == null || rationale.isBlank()) {
                throw new IllegalArgumentException("rationale must not be null or blank");
            }
        }
    }

    /**
     * Route to a human participant. No agent was available or suitable.
     * Normal YAWL resource allocation proceeds unchanged.
     */
    record HumanRoute() implements RoutingDecision {}
}
