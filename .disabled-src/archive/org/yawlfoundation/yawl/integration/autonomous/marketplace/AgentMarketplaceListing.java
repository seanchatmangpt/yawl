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

import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A published marketplace entry combining agent identity with its five-dimensional spec.
 *
 * <p>A listing is the unit of exchange in the agent marketplace. It binds the
 * network-addressable {@link AgentInfo} (how to reach the agent) to the
 * formally typed {@link AgentMarketplaceSpec} (what the agent declares it can do
 * and at what cost and latency).</p>
 *
 * <p>Listings are time-bounded by a heartbeat. An agent that stops updating its
 * heartbeat is considered offline and is excluded from marketplace queries.
 * The {@link #isLive(Duration)} predicate enforces this contract: buyers
 * receive only currently-reachable agents.</p>
 *
 * <p>Listing immutability: heartbeats are recorded as a new listing instance
 * (via {@link #withHeartbeat(Instant)}), preserving the audit trail in the
 * coordination graph without mutation.</p>
 *
 * @param agentInfo         network identity of the agent; never null
 * @param spec              five-dimensional marketplace specification; never null
 * @param registeredAt      when this listing was first published; never null
 * @param lastHeartbeatAt   when this agent last confirmed it was alive; never null,
 *                          must be &gt;= registeredAt
 * @since YAWL 6.0
 */
public record AgentMarketplaceListing(
        AgentInfo agentInfo,
        AgentMarketplaceSpec spec,
        Instant registeredAt,
        Instant lastHeartbeatAt) {

    /** Compact constructor: validates fields and temporal ordering. */
    public AgentMarketplaceListing {
        Objects.requireNonNull(agentInfo, "agentInfo is required");
        Objects.requireNonNull(spec, "spec is required");
        Objects.requireNonNull(registeredAt, "registeredAt is required");
        Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt is required");

        if (lastHeartbeatAt.isBefore(registeredAt)) {
            throw new IllegalArgumentException(
                "lastHeartbeatAt must be >= registeredAt: " +
                "registered=" + registeredAt + ", heartbeat=" + lastHeartbeatAt);
        }
    }

    /**
     * Returns true if this agent's last heartbeat is within the given staleness window.
     *
     * <p>Marketplace queries pass only live listings to buyers. The staleness
     * window is caller-controlled: tight windows (e.g. 30s) suit high-throughput
     * workflows; loose windows (e.g. 5min) suit batch-oriented workflows.</p>
     *
     * @param maxStaleness the maximum acceptable age of the last heartbeat
     * @return true iff the heartbeat is more recent than {@code now - maxStaleness}
     */
    public boolean isLive(Duration maxStaleness) {
        Objects.requireNonNull(maxStaleness, "maxStaleness must not be null");
        return Duration.between(lastHeartbeatAt, Instant.now()).compareTo(maxStaleness) <= 0;
    }

    /**
     * Returns a new listing with an updated heartbeat timestamp.
     *
     * <p>Used to record that the agent is still alive without mutating the
     * existing listing. The new listing preserves the original {@code registeredAt}.</p>
     *
     * @param heartbeatAt the new heartbeat timestamp; must be &gt;= registeredAt
     * @return a new listing with the updated heartbeat
     */
    public AgentMarketplaceListing withHeartbeat(Instant heartbeatAt) {
        return new AgentMarketplaceListing(agentInfo, spec, registeredAt, heartbeatAt);
    }

    /**
     * Creates a new listing registered and heartbeated at the given instant.
     *
     * @param agentInfo the agent's network identity
     * @param spec      the agent's five-dimensional spec
     * @param now       the registration and initial heartbeat timestamp
     * @return a new listing
     */
    public static AgentMarketplaceListing of(
            AgentInfo agentInfo, AgentMarketplaceSpec spec, Instant now) {
        return new AgentMarketplaceListing(agentInfo, spec, now, now);
    }

    /**
     * Creates a new listing registered and heartbeated at {@code Instant.now()}.
     *
     * @param agentInfo the agent's network identity
     * @param spec      the agent's five-dimensional spec
     * @return a new listing
     */
    public static AgentMarketplaceListing publishNow(
            AgentInfo agentInfo, AgentMarketplaceSpec spec) {
        return of(agentInfo, spec, Instant.now());
    }
}
