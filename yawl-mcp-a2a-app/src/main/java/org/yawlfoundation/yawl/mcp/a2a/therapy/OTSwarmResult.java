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

import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.LifestyleGoal;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OTPatient;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OccupationalProfile;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.SwarmTaskResult;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.TherapySession;

import java.util.List;

/**
 * Aggregate result of a complete OT swarm lifecycle execution.
 *
 * <p>Produced by {@link OTSwarmCoordinator#execute} after the YAWL workflow
 * reaches OutcomeEvaluation or is terminated by timeout/error. This immutable
 * record captures all relevant outputs from the swarm execution.</p>
 *
 * @param caseId YAWL case identifier assigned at workflow launch (non-null)
 * @param patient the patient who was treated (non-null)
 * @param profile COPM occupational profile from assessment phase (nullable)
 * @param goals lifestyle goals identified during goal-setting (unmodifiable)
 * @param sessions therapy sessions scheduled and executed (unmodifiable)
 * @param agentResults ordered list of results from each swarm agent, in phase order
 *        (unmodifiable, non-null)
 * @param completedPhase the last phase that completed successfully (non-null)
 * @param success true if workflow reached OutcomeEvaluation phase; false if
 *        terminated early due to timeout, error, or max adaptation cycles exceeded
 * @param summary narrative outcome summary from the final agent or error message
 *        (non-null, may be empty)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record OTSwarmResult(
    String caseId,
    OTPatient patient,
    OccupationalProfile profile,
    List<LifestyleGoal> goals,
    List<TherapySession> sessions,
    List<SwarmTaskResult> agentResults,
    SwarmPhase completedPhase,
    boolean success,
    String summary
) {
    /**
     * Canonical constructor with validation and defensive copying.
     *
     * @throws IllegalArgumentException if required fields are null or blank
     */
    public OTSwarmResult {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("Case ID is required and must not be blank");
        }
        if (patient == null) {
            throw new IllegalArgumentException("Patient is required (non-null)");
        }
        if (agentResults == null) {
            throw new IllegalArgumentException("Agent results are required (non-null)");
        }
        if (completedPhase == null) {
            throw new IllegalArgumentException("Completed phase is required (non-null)");
        }

        // Defensive copies to prevent external mutation
        goals = goals != null ? List.copyOf(goals) : List.of();
        sessions = sessions != null ? List.copyOf(sessions) : List.of();
        agentResults = List.copyOf(agentResults);

        if (summary == null) {
            summary = "";
        }
    }

    /**
     * Returns the number of swarm agents that reported success.
     *
     * <p>Counts all {@link SwarmTaskResult#success success} agents among
     * {@link #agentResults}.</p>
     *
     * @return count of successful agents (0 to agentResults.size())
     */
    public long successfulAgentCount() {
        return agentResults.stream().filter(SwarmTaskResult::success).count();
    }

    /**
     * Returns the number of adaptation cycles that occurred.
     *
     * <p>Counts all agents with {@link SwarmPhase#ADAPTATION ADAPTATION} phase
     * in the results. Each adaptation cycle corresponds to one ADAPTATION agent result.</p>
     *
     * @return count of adaptation cycles (0 or more)
     */
    public long adaptationCycleCount() {
        return agentResults.stream()
            .filter(r -> r.phase() == SwarmPhase.ADAPTATION)
            .count();
    }
}
