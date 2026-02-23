/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.therapy.domain;

import org.yawlfoundation.yawl.mcp.a2a.therapy.SwarmPhase;

import java.util.Map;

/**
 * Result produced by a single swarm agent for one therapy workflow phase.
 *
 * <p>Each agent contributes exactly one SwarmTaskResult per phase execution.
 * Results accumulate in the coordinator context to inform subsequent agents.</p>
 *
 * @param agentId class name of the agent that produced this result
 * @param phase the workflow phase this result belongs to
 * @param output narrative summary of what the agent did
 * @param success whether the agent completed successfully
 * @param errorMessage error detail if success is false, null otherwise
 * @param data structured data produced (shared to downstream agents via context)
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record SwarmTaskResult(
    String agentId,
    SwarmPhase phase,
    String output,
    boolean success,
    String errorMessage,
    Map<String, Object> data
) {
    /** Canonical constructor with validation. */
    public SwarmTaskResult {
        if (agentId == null || agentId.isBlank()) throw new IllegalArgumentException("Agent id required");
        if (phase == null) throw new IllegalArgumentException("Phase required");
        if (output == null) output = "";
        if (!success && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("Error message required when success=false");
        }
        if (success && errorMessage != null) errorMessage = null; // normalize
        data = data != null ? Map.copyOf(data) : Map.of();
    }

    /** Factory: successful result with data. */
    public static SwarmTaskResult success(String agentId, SwarmPhase phase, String output, Map<String, Object> data) {
        return new SwarmTaskResult(agentId, phase, output, true, null, data);
    }

    /** Factory: failed result. */
    public static SwarmTaskResult failure(String agentId, SwarmPhase phase, String errorMessage) {
        return new SwarmTaskResult(agentId, phase, "", false, errorMessage, Map.of());
    }
}
