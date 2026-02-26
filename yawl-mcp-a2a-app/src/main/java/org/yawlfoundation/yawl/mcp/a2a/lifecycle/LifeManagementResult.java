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

package org.yawlfoundation.yawl.mcp.a2a.lifecycle;

import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport;
import org.yawlfoundation.yawl.mcp.a2a.therapy.OTSwarmResult;

import java.time.Instant;
import java.util.List;

/**
 * Complete output record for the zero cognitive load life management system.
 *
 * <p>Aggregates results from all three pillars alongside the synthesised plan:</p>
 * <ol>
 *   <li>OT Lifestyle Redesign Swarm → {@code otResult}</li>
 *   <li>Van der Aalst WCP Demo → {@code wcpInsights}</li>
 *   <li>GregVerse Self-Play → {@code gvReport}</li>
 * </ol>
 *
 * @param sessionId    UUID for this orchestration run
 * @param context      the original user input
 * @param otResult     result from the OT swarm (non-null)
 * @param wcpInsights  WCP pattern insights (empty list if pattern demo skipped)
 * @param gvReport     GregVerse self-play report (null if advisor layer skipped)
 * @param plan         synthesised life management plan (non-null)
 * @param completedAt  timestamp when the orchestrator finished
 * @param success      true if all mandatory phases succeeded
 * @param summary      one-line outcome description
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record LifeManagementResult(
    String sessionId,
    LifeContext context,
    OTSwarmResult otResult,
    List<WcpPatternInsight> wcpInsights,
    GregVerseReport gvReport,
    LifeManagementPlan plan,
    Instant completedAt,
    boolean success,
    String summary
) {
    /** Canonical constructor with validation. */
    public LifeManagementResult {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("LifeManagementResult.sessionId must be non-blank");
        }
        if (context == null) {
            throw new IllegalArgumentException("LifeManagementResult.context must not be null");
        }
        if (otResult == null) {
            throw new IllegalArgumentException("LifeManagementResult.otResult must not be null");
        }
        if (wcpInsights == null) {
            throw new IllegalArgumentException("LifeManagementResult.wcpInsights must not be null");
        }
        if (plan == null) {
            throw new IllegalArgumentException("LifeManagementResult.plan must not be null");
        }
        if (completedAt == null) {
            throw new IllegalArgumentException("LifeManagementResult.completedAt must not be null");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("LifeManagementResult.summary must be non-blank");
        }
        wcpInsights = List.copyOf(wcpInsights);
    }

    /**
     * Number of OT phases that completed successfully.
     *
     * @return count of successful agent results in the OT swarm
     */
    public long successfulOtPhases() {
        return otResult.agentResults().stream()
            .filter(r -> r.success())
            .count();
    }

    /**
     * WCP patterns that were actually demonstrated by PatternDemoRunner.
     *
     * @return list of demonstrated insights
     */
    public List<WcpPatternInsight> demonstratedWcps() {
        return wcpInsights.stream().filter(WcpPatternInsight::demonstrated).toList();
    }

    /**
     * Number of GregVerse advisors that contributed insights.
     *
     * @return count of successful agent results, or 0 if advisor layer was skipped
     */
    public int advisorCount() {
        if (gvReport == null) return 0;
        return gvReport.getSuccessfulAgents();
    }
}
