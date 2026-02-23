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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 7: Dynamic therapy plan adaptation using graded activity progression.
 *
 * <p>Activated when goal attainment scaling indicates insufficient progress
 * (score below 0.85 threshold). Applies evidence-based adaptation strategies
 * to modify the therapy programme for better outcomes.</p>
 *
 * <h2>Adaptation Strategies</h2>
 * <ul>
 *   <li><b>Intensity Progression</b>: increase session duration by 20% (cycle 1)</li>
 *   <li><b>Frequency Boost</b>: add supplementary home practice sessions (cycle 2)</li>
 *   <li><b>Intervention Switch</b>: change therapeutic modality (cycle 3+)</li>
 * </ul>
 *
 * <h2>Adaptation Cycle Limit</h2>
 * <p>A hard maximum of 3 adaptation cycles prevents infinite loops. Beyond cycle 3,
 * the system treats the patient as requiring extended or specialist programme review
 * rather than incremental adjustments.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class OTAdaptationAgent implements OTSwarmAgent {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_ADAPTATION_CYCLES = 3;
    private static final double INTENSITY_BOOST_FACTOR = 1.20;
    private static final int MAX_SESSION_DURATION = 180;
    private static final int BOOSTER_SESSION_DURATION = 30;

    private static final Map<String, String> ALTERNATIVE_INTERVENTIONS = Map.of(
        "self-care", "Intensive ADL retraining with robotics-assisted practice",
        "productivity", "Supported Employment programme with job coach",
        "leisure", "Therapeutic recreation specialist co-facilitation"
    );

    @Override
    public SwarmPhase phase() {
        return SwarmPhase.ADAPTATION;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SwarmTaskResult execute(OTPatient patient, Map<String, Object> context) {
        List<TherapySession> sessions = (List<TherapySession>) context.get("therapy_sessions");
        List<LifestyleGoal> goals = (List<LifestyleGoal>) context.getOrDefault("goals",
            context.get("lifestyle_goals"));
        double currentProgress = (double) context.getOrDefault("progress_score", 0.0);
        int adaptationCycle = (int) context.getOrDefault("adaptation_cycle", 0);

        // Sessions are optional - if no sessions, create empty adapted list
        if (sessions == null) {
            sessions = new ArrayList<>();
        }

        if (goals == null || goals.isEmpty()) {
            return SwarmTaskResult.failure(agentId(), phase(),
                "No goals in context — cannot adapt without goals");
        }

        // Hard stop if max cycles exceeded
        if (adaptationCycle >= MAX_ADAPTATION_CYCLES) {
            return SwarmTaskResult.failure(agentId(), phase(),
                String.format(
                    "Maximum adaptation cycles (%d) reached. Patient requires specialist review.",
                    MAX_ADAPTATION_CYCLES
                ));
        }

        int newCycle = adaptationCycle + 1;
        String strategy = selectAdaptationStrategy(newCycle);
        List<TherapySession> adaptedSessions = applyAdaptation(patient, sessions, goals, strategy, newCycle);

        Map<String, Object> data = new HashMap<>();
        data.put("adaptation_cycle", newCycle);
        data.put("adapted_sessions", adaptedSessions);
        data.put("therapy_sessions", adaptedSessions);
        data.put("adaptation_strategy", strategy);
        data.put("pre_adaptation_progress", currentProgress);

        long modifiedCount = countAdaptedSessions(adaptedSessions, sessions);
        String output = String.format(
            "Adaptation cycle %d: Applied \"%s\". %d sessions modified/added. " +
            "Prior progress: %.0f%%. Re-entering intervention → scheduling → monitoring cycle.",
            newCycle, strategy, modifiedCount, currentProgress * 100
        );

        return SwarmTaskResult.success(agentId(), phase(), output, data);
    }

    /**
     * Selects the adaptation strategy based on which cycle we're in.
     *
     * <p>Progression logic:</p>
     * <ul>
     *   <li>Cycle 1: Increase intensity gradually (safest, least disruptive)</li>
     *   <li>Cycle 2: Add complementary frequency (booster sessions)</li>
     *   <li>Cycle 3+: Switch intervention modality (highest change, specialist-guided)</li>
     * </ul>
     *
     * @param cycle the current adaptation cycle (1, 2, or 3+)
     * @return descriptive strategy name
     */
    private String selectAdaptationStrategy(int cycle) {
        return switch (cycle) {
            case 1 -> "Intensity Progression (20% duration increase)";
            case 2 -> "Frequency Boost (add home practice programme)";
            default -> "Intervention Switch (alternative therapeutic modality)";
        };
    }

    /**
     * Applies the selected adaptation strategy to the existing therapy sessions.
     *
     * <p>Preserves all completed sessions unchanged. Modifies scheduled sessions
     * according to the cycle-specific strategy. Adds new booster sessions for
     * cycle 2 and beyond.</p>
     *
     * @param patient the patient
     * @param sessions original list of therapy sessions
     * @param goals lifestyle goals (used to select intervention areas)
     * @param strategy the adaptation strategy (descriptive only)
     * @param cycle the current adaptation cycle
     * @return adapted list of sessions
     */
    @SuppressWarnings("unchecked")
    private List<TherapySession> applyAdaptation(OTPatient patient, List<TherapySession> sessions,
            List<LifestyleGoal> goals, String strategy, int cycle) {
        List<TherapySession> adapted = new ArrayList<>();

        // Keep completed sessions unchanged, adapt scheduled/adapted ones
        for (TherapySession session : sessions) {
            if (TherapySession.STATUS_COMPLETED.equals(session.status())) {
                adapted.add(session);
            } else {
                // Adapt the scheduled/adapted session based on current cycle
                TherapySession adaptedSession = adaptSession(session, goals, cycle);
                adapted.add(adaptedSession);
            }
        }

        // Add booster sessions for cycle 2+
        if (cycle >= 2) {
            adapted.addAll(generateBoosterSessions(patient, goals, cycle));
        }
        return adapted;
    }

    /**
     * Adapts a single therapy session based on the adaptation cycle.
     *
     * <p>Cycle 1: Increases duration by 20% (within max 180 min).
     * Cycle 2+: Switches intervention type to alternative modality.</p>
     *
     * @param session the original session
     * @param goals lifestyle goals (for selecting alternative intervention)
     * @param cycle the current cycle
     * @return adapted session with status updated to STATUS_ADAPTED
     */
    private TherapySession adaptSession(TherapySession session, List<LifestyleGoal> goals, int cycle) {
        // Cycle 1: increase duration by 20%, but cap at 180 min
        if (cycle == 1) {
            int newDuration = Math.min(MAX_SESSION_DURATION,
                (int) (session.durationMinutes() * INTENSITY_BOOST_FACTOR));
            return new TherapySession(
                session.id(),
                session.patientId(),
                session.interventionType(),
                session.scheduledDate(),
                newDuration,
                TherapySession.STATUS_ADAPTED,
                session.progressScore()
            );
        }
        // Cycle 2+: switch intervention type based on top goal
        String targetArea = goals.stream()
            .findFirst()
            .map(LifestyleGoal::targetArea)
            .orElse("self-care");
        String alternativeIntervention = ALTERNATIVE_INTERVENTIONS.getOrDefault(targetArea,
            "Enhanced occupational therapy programme");
        return new TherapySession(
            session.id(),
            session.patientId(),
            alternativeIntervention,
            session.scheduledDate(),
            session.durationMinutes(),
            TherapySession.STATUS_ADAPTED,
            session.progressScore()
        );
    }

    /**
     * Generates booster sessions for enhanced programme support.
     *
     * <p>Creates 3 supplementary home practice sessions, each 30 minutes long,
     * scheduled 2 days apart starting from 1-2 weeks hence (depending on cycle).
     * Each booster targets one of the patient's priority areas.</p>
     *
     * @param patient the patient
     * @param goals lifestyle goals (each booster targets one goal's area)
     * @param cycle the current cycle (affects start date)
     * @return list of new booster TherapySession objects
     */
    private List<TherapySession> generateBoosterSessions(OTPatient patient, List<LifestyleGoal> goals, int cycle) {
        List<TherapySession> boosters = new ArrayList<>();
        LocalDate boosterStart = LocalDate.now().plusWeeks(1 + cycle);

        // Generate 3 booster sessions (one per priority area, cycling through goals)
        for (int i = 0; i < 3; i++) {
            LifestyleGoal goal = goals.get(i % goals.size());
            String sessionId = "B" + cycle + String.format("%02d", i + 1) + "-" +
                UUID.randomUUID().toString().substring(0, 6);

            boosters.add(new TherapySession(
                sessionId,
                patient.id(),
                "Home practice programme — " + goal.targetArea(),
                boosterStart.plusDays((long) i * 2).format(DATE_FMT),
                BOOSTER_SESSION_DURATION,
                TherapySession.STATUS_SCHEDULED,
                0.0
            ));
        }
        return boosters;
    }

    /**
     * Counts how many sessions were adapted or added (not counting completed sessions).
     *
     * @param adapted the adapted session list
     * @param original the original session list
     * @return count of sessions with STATUS_ADAPTED status plus any new boosters
     */
    private long countAdaptedSessions(List<TherapySession> adapted, List<TherapySession> original) {
        long adaptedCount = adapted.stream()
            .filter(s -> TherapySession.STATUS_ADAPTED.equals(s.status()))
            .count();
        long newCount = adapted.size() - original.size();
        return adaptedCount + Math.max(0, newCount);
    }
}
