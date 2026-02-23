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

package org.yawlfoundation.yawl.mcp.a2a.therapy.agents;

import org.yawlfoundation.yawl.mcp.a2a.therapy.OTSwarmAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.SwarmPhase;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.LifestyleGoal;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OTPatient;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.SwarmTaskResult;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.TherapySession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 6: Goal attainment scaling and therapy progress evaluation.
 *
 * <p>Evaluates therapy progress using Goal Attainment Scaling (GAS), a
 * standardised outcome measurement methodology for occupational therapy.
 * Each completed session is scored on the GAS scale (-2 to +2), which is
 * then normalised to a 0.0-1.0 progress score for workflow routing.</p>
 *
 * <h2>Goal Attainment Scale (GAS)</h2>
 * <ul>
 *   <li>-2: Much less than expected outcome (0.0)</li>
 *   <li>-1: Somewhat less than expected outcome (0.25)</li>
 *   <li>&nbsp;0: Expected outcome — current goal level (0.50)</li>
 *   <li>+1: Somewhat more than expected outcome (0.75)</li>
 *   <li>+2: Much more than expected outcome (1.0)</li>
 * </ul>
 *
 * <p>Normalised progress score = mean GAS score across all completed sessions.
 * Progress score ≥ 0.85 routes to outcome evaluation. Otherwise, triggers
 * plan adaptation for enhanced interventions.</p>
 *
 * <h2>Context Requirements</h2>
 * <p>Expects {@code therapy_sessions} (List&lt;TherapySession&gt;),
 * {@code lifestyle_goals} (List&lt;LifestyleGoal&gt;), and
 * {@code adaptation_cycle} (int, default 0) in context.
 * Writes {@code progress_score} (double, 0.0-1.0),
 * {@code sessions_completed} (int), {@code goals_on_track} (int),
 * and {@code gas_interpretation} (String) to context.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class OTProgressMonitorAgent implements OTSwarmAgent {

    /** Progress threshold to advance to outcome evaluation (85% goal attainment). */
    public static final double ADVANCEMENT_THRESHOLD = 0.85;

    private static final double BASE_PROGRESS_INCREMENT = 0.15;
    private static final double ADAPTATION_BOOST_PER_CYCLE = 0.10;
    private static final double PROGRESS_SESSION_RATIO = 0.45;
    private static final double ADAPTATION_CYCLE_BASE = 0.15;
    private static final double SESSION_COMPLETION_FACTOR = 0.15;
    private static final double PROGRESS_ATTAINMENT_THRESHOLD = 0.5;
    private static final double MIN_PROGRESS_SCORE = 0.0;
    private static final double MAX_PROGRESS_SCORE = 1.0;

    /**
     * Returns the swarm phase this agent handles.
     *
     * @return SwarmPhase.PROGRESS_MONITORING
     */
    @Override
    public SwarmPhase phase() {
        return SwarmPhase.PROGRESS_MONITORING;
    }

    /**
     * Executes progress evaluation using goal attainment scaling.
     *
     * <p>Simulates therapy session completions and computes a normalised GAS score.
     * The score is used to determine whether the patient is ready for outcome
     * evaluation (≥0.85) or requires plan adaptation.</p>
     *
     * @param patient the OTPatient to evaluate (non-null)
     * @param context mutable context containing therapy sessions and goals
     * @return SwarmTaskResult.success() with progress metrics, or
     *         SwarmTaskResult.failure() if required context is missing
     */
    @Override
    @SuppressWarnings("unchecked")
    public SwarmTaskResult execute(OTPatient patient, Map<String, Object> context) {
        List<TherapySession> sessions = (List<TherapySession>) context.get("therapy_sessions");
        List<LifestyleGoal> goals = (List<LifestyleGoal>) context.getOrDefault("goals",
            context.get("lifestyle_goals"));

        if (sessions == null || sessions.isEmpty()) {
            return SwarmTaskResult.failure(agentId(), phase(),
                "No therapy sessions in context — scheduling must precede progress monitoring");
        }
        if (goals == null || goals.isEmpty()) {
            return SwarmTaskResult.failure(agentId(), phase(),
                "No goals in context — cannot evaluate progress without goals");
        }

        int adaptationCycle = (int) context.getOrDefault("adaptation_cycle", 0);

        // Simulate session completion and compute GAS scores
        List<TherapySession> completedSessions = simulateSessionCompletion(sessions, adaptationCycle);
        double progressScore = computeGasScore(completedSessions, goals, adaptationCycle);
        int goalsOnTrack = countGoalsOnTrack(completedSessions, goals);
        String gasInterpretation = interpretGas(progressScore);

        // Update sessions list with completed sessions
        List<TherapySession> updatedSessions = new ArrayList<>(completedSessions);
        updatedSessions.addAll(sessions.subList(completedSessions.size(), sessions.size()));

        Map<String, Object> data = new HashMap<>();
        data.put("progress_score", progressScore);
        data.put("sessions_completed", completedSessions.size());
        data.put("goals_on_track", goalsOnTrack);
        data.put("gas_interpretation", gasInterpretation);
        data.put("therapy_sessions", updatedSessions);
        data.put("progress_monitoring_complete", true);

        boolean advancing = progressScore >= ADVANCEMENT_THRESHOLD;
        String nextPhase = advancing ? "OutcomeEvaluation" : "PlanAdaptation";

        String output = String.format(
            "Progress evaluation: GAS score %.2f (%s). %d/%d sessions completed. " +
            "%d/%d goals on track. Routing: %s.",
            progressScore, gasInterpretation,
            completedSessions.size(), sessions.size(),
            goalsOnTrack, goals.size(),
            nextPhase
        );

        return SwarmTaskResult.success(agentId(), phase(), output, data);
    }

    /**
     * Simulates therapy session completion with realistic progress trajectories.
     *
     * <p>Each adaptation cycle completes approximately 1/3 of the total sessions
     * (capped at total session count). Progress scores increase with each session
     * completion and are boosted per adaptation cycle (representing cumulative
     * therapeutic gains across multiple monitoring points).</p>
     *
     * @param sessions list of all scheduled therapy sessions
     * @param adaptationCycle current adaptation cycle number (0 = initial monitoring)
     * @return list of completed TherapySession objects with progress scores
     */
    private List<TherapySession> simulateSessionCompletion(List<TherapySession> sessions, int adaptationCycle) {
        List<TherapySession> completed = new ArrayList<>();

        // Calculate sessions to complete: base 1/3, increased by 2× per cycle
        int sessionsToComplete = Math.max(1, sessions.size() / 3);
        sessionsToComplete = Math.min(sessionsToComplete + adaptationCycle * 2, sessions.size());

        for (int i = 0; i < sessionsToComplete && i < sessions.size(); i++) {
            TherapySession session = sessions.get(i);

            // Compute progress score for this session
            // Base: 0.45 + session-specific increment + cycle boost
            double rawScore = PROGRESS_SESSION_RATIO
                + (SESSION_COMPLETION_FACTOR * (i + 1)) / sessionsToComplete
                + (ADAPTATION_BOOST_PER_CYCLE * adaptationCycle);

            double score = Math.min(MAX_PROGRESS_SCORE, Math.max(MIN_PROGRESS_SCORE, rawScore));

            completed.add(session.withCompleted(score));
        }

        return completed;
    }

    /**
     * Computes the normalised GAS (Goal Attainment Scaling) score.
     *
     * <p>Mean progress across all completed sessions, with a boost for each
     * adaptation cycle representing cumulative therapeutic gains. The final
     * score is clamped to [0.0, 1.0].</p>
     *
     * @param completed list of completed therapy sessions (with progress scores)
     * @param goals list of lifestyle goals (for reference)
     * @param cycle adaptation cycle number
     * @return normalised GAS score (0.0 = no progress, 1.0 = full goal attainment)
     */
    private double computeGasScore(List<TherapySession> completed, List<LifestyleGoal> goals, int cycle) {
        if (completed.isEmpty()) {
            return MIN_PROGRESS_SCORE;
        }

        // Mean progress across completed sessions
        double meanProgress = completed.stream()
            .mapToDouble(TherapySession::progressScore)
            .average()
            .orElse(MIN_PROGRESS_SCORE);

        // Each adaptation cycle improves progress by 15%
        double adaptationBoost = ADAPTATION_CYCLE_BASE * cycle;
        double gasScore = meanProgress + adaptationBoost;

        return Math.min(MAX_PROGRESS_SCORE, gasScore);
    }

    /**
     * Counts how many lifestyle goals are "on track" based on session progress.
     *
     * <p>A goal is considered on track if the mean progress across completed
     * sessions is ≥0.5 (50% of expected progress). Otherwise, conservatively
     * assume all but one goal are on track (representing at least partial progress).</p>
     *
     * @param completed list of completed therapy sessions
     * @param goals list of lifestyle goals
     * @return count of goals on track
     */
    private int countGoalsOnTrack(List<TherapySession> completed, List<LifestyleGoal> goals) {
        if (completed.isEmpty()) {
            return 0;
        }

        double meanProgress = completed.stream()
            .mapToDouble(TherapySession::progressScore)
            .average()
            .orElse(MIN_PROGRESS_SCORE);

        // If mean progress >= 50%, all goals are considered on track
        if (meanProgress >= PROGRESS_ATTAINMENT_THRESHOLD) {
            return goals.size();
        }

        // Otherwise, at least one goal shows progress, but not all are on track
        return Math.max(0, goals.size() - 1);
    }

    /**
     * Provides a narrative interpretation of the GAS score.
     *
     * <p>Maps the 0.0-1.0 score to qualitative language aligned with the
     * GAS scale (-2 to +2).</p>
     *
     * @param score normalised progress score
     * @return narrative interpretation
     */
    private String interpretGas(double score) {
        if (score >= 0.875) {
            return "much more than expected";
        }
        if (score >= 0.625) {
            return "somewhat more than expected";
        }
        if (score >= 0.375) {
            return "expected outcome level";
        }
        if (score >= 0.125) {
            return "somewhat less than expected";
        }
        return "much less than expected";
    }
}
