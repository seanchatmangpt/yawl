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

package org.yawlfoundation.yawl.mcp.a2a.therapy;

import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OTPatient;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.SwarmTaskResult;

import java.util.Map;

/**
 * Functional interface for an autonomous agent in the OT lifestyle redesign swarm.
 *
 * <p>Each of the 8 swarm phases has a dedicated agent implementation that
 * executes the phase-specific work. The agent receives the current patient state
 * and a mutable context map containing results from all previous agents, enabling
 * downstream agents to build on upstream outputs.</p>
 *
 * <p>All implementations must be stateless across invocations and thread-safe
 * by construction. This allows agents to be reused across multiple concurrent
 * swarm executions.</p>
 *
 * <h2>Execution Contract</h2>
 * <ul>
 *   <li>Agent reads patient data and prior results from context</li>
 *   <li>Agent performs domain-specific work (e.g., assessment, scheduling)</li>
 *   <li>Agent writes structured output to context under a well-known key</li>
 *   <li>Agent returns {@link SwarmTaskResult} describing success/failure and findings</li>
 * </ul>
 *
 * <h2>Context Map Convention</h2>
 * <p>Agents use the context map to share intermediate results. Keys should be
 * named after the phase (e.g., {@code "profile"} for ASSESSMENT phase results).
 * Prior agents' results are always available in context for downstream agents.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see SwarmPhase
 * @see SwarmTaskResult
 * @see OTSwarmCoordinator
 */
@FunctionalInterface
public interface OTSwarmAgent {

    /**
     * Execute this agent for the given patient and accumulated context.
     *
     * <p>This method is invoked by the {@link OTSwarmCoordinator} to execute
     * the agent's phase-specific work. The agent should:</p>
     * <ol>
     *   <li>Read patient data from the {@code patient} parameter</li>
     *   <li>Read prior results from the {@code context} map if needed</li>
     *   <li>Perform domain-specific work (assessment, planning, etc.)</li>
     *   <li>Write results back to {@code context} under a phase-specific key</li>
     *   <li>Return a {@link SwarmTaskResult} describing outcomes</li>
     * </ol>
     *
     * @param patient the patient being treated (non-null)
     * @param context shared context map from prior agents (non-null, mutable).
     *        Agents read prior results and write their own outputs here.
     * @return result of this agent's execution (never null). Must include
     *         phase information, success flag, and relevant findings.
     *
     * @throws IllegalArgumentException if patient is null or required fields are missing
     * @throws RuntimeException if the agent encounters an unrecoverable error
     */
    SwarmTaskResult execute(OTPatient patient, Map<String, Object> context);

    /**
     * Returns the canonical identifier for this agent type.
     *
     * <p>Default implementation returns the simple class name. Concrete agents
     * may override for custom identifiers (e.g., "intake-agent-v2.0").</p>
     *
     * @return agent identifier string (never null)
     */
    default String agentId() {
        return getClass().getSimpleName();
    }

    /**
     * Returns the swarm phase this agent handles.
     *
     * <p>Default implementation throws {@link UnsupportedOperationException}.
     * All concrete implementations must override this method to return the
     * corresponding {@link SwarmPhase}.</p>
     *
     * @return the {@link SwarmPhase} associated with this agent
     * @throws UnsupportedOperationException if not overridden by subclass
     */
    default SwarmPhase phase() {
        throw new UnsupportedOperationException(
            getClass().getSimpleName() + " must override phase() to return its SwarmPhase");
    }
}
