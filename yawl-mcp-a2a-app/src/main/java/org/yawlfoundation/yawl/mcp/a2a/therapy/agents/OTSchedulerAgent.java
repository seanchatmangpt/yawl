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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Agent 5: Therapy session scheduling and resource planning.
 *
 * <p>Generates a structured therapy schedule based on lifestyle goals, intervention
 * plan, and clinical risk level. Uses evidence-based frequency guidelines:
 * 3 sessions/week for outpatient, daily (5×/week) for inpatient programmes.</p>
 *
 * <h2>Session Planning Rules</h2>
 * <ul>
 *   <li>OUTPATIENT: Mon/Wed/Fri, 60-minute sessions</li>
 *   <li>INPATIENT: Mon–Fri daily, 45-minute sessions</li>
 *   <li>HIGH risk: shorter sessions (45 min), more frequent rest breaks</li>
 *   <li>Total sessions = frequency × target weeks of primary goal</li>
 * </ul>
 *
 * <h2>Context Requirements</h2>
 * <p>Expects {@code lifestyle_goals} (List&lt;LifestyleGoal&gt;),
 * {@code intervention_plan} (Map&lt;String, List&lt;String&gt;&gt;),
 * {@code programme_type} (String), and {@code risk_level} (String) in context.
 * Writes {@code therapy_sessions} (List&lt;TherapySession&gt;) and
 * {@code total_sessions} (int) to context.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class OTSchedulerAgent implements OTSwarmAgent {

    private static final Set<DayOfWeek> OUTPATIENT_DAYS = Set.of(
        DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY
    );
    private static final Set<DayOfWeek> INPATIENT_DAYS = Set.of(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    );
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int STANDARD_DURATION_MINUTES = 60;
    private static final int HIGH_RISK_DURATION_MINUTES = 45;
    private static final int INPATIENT_DURATION_MINUTES = 45;
    private static final int COMPLEX_DURATION_MINUTES = 90;
    private static final int SCHEDULING_LEAD_WEEKS = 1;

    /**
     * Returns the swarm phase this agent handles.
     *
     * @return SwarmPhase.SCHEDULING
     */
    @Override
    public SwarmPhase phase() {
        return SwarmPhase.SCHEDULING;
    }

    /**
     * Executes therapy session scheduling for the given patient.
     *
     * <p>Reads lifestyle goals, intervention plan, and clinical parameters from context,
     * then generates a structured schedule of therapy sessions spanning the duration
     * of the primary goal. Each session is assigned a specific date, duration, and
     * intervention type based on clinical guidelines and programme type.</p>
     *
     * @param patient the OTPatient to schedule (non-null)
     * @param context mutable context map containing goals and intervention plan
     * @return SwarmTaskResult.success() with scheduled sessions, or
     *         SwarmTaskResult.failure() if required context is missing
     */
    @Override
    @SuppressWarnings("unchecked")
    public SwarmTaskResult execute(OTPatient patient, Map<String, Object> context) {
        List<LifestyleGoal> goals = (List<LifestyleGoal>) context.get("goals");
        if (goals == null || goals.isEmpty()) {
            return SwarmTaskResult.failure(agentId(), phase(),
                "No goals in context — cannot schedule without goals");
        }

        Map<String, String> interventionPlan =
            (Map<String, String>) context.getOrDefault("interventions", Map.of());

        String programme = (String) context.getOrDefault("programme_type", "OUTPATIENT");
        String riskLevel = (String) context.getOrDefault("risk_level", "MEDIUM");

        List<TherapySession> sessions = generateSessions(patient, goals, interventionPlan, programme, riskLevel);

        Map<String, Object> data = new HashMap<>();
        data.put("therapy_sessions", sessions);
        data.put("total_sessions", sessions.size());
        data.put("programme_type", programme);
        data.put("scheduling_complete", true);

        String firstSessionDate = sessions.isEmpty() ? "N/A" : sessions.getFirst().scheduledDate();
        int totalWeeks = goals.stream().mapToInt(LifestyleGoal::targetWeeks).max().orElse(0);

        String output = String.format(
            "Schedule generated: %d sessions over %d weeks (%s programme). First session: %s.",
            sessions.size(),
            totalWeeks,
            programme,
            firstSessionDate
        );

        return SwarmTaskResult.success(agentId(), phase(), output, data);
    }

    /**
     * Generates therapy session objects spanning the target intervention duration.
     *
     * <p>Starting from one week ahead of today, sessions are scheduled on valid days
     * (outpatient: Mon/Wed/Fri, inpatient: Mon-Fri). Duration and intervention type
     * are assigned based on risk level and goal priorities.</p>
     *
     * @param patient the patient to schedule
     * @param goals list of lifestyle goals
     * @param interventionPlan mapping of occupational areas to intervention types
     * @param programme programme type (OUTPATIENT or INPATIENT)
     * @param riskLevel clinical risk level (HIGH, MEDIUM, LOW)
     * @return list of generated TherapySession objects
     */
    private List<TherapySession> generateSessions(OTPatient patient, List<LifestyleGoal> goals,
            Map<String, String> interventionPlan, String programme, String riskLevel) {
        List<TherapySession> sessions = new ArrayList<>();

        boolean isInpatient = "INPATIENT".equals(programme);
        Set<DayOfWeek> sessionDays = isInpatient ? INPATIENT_DAYS : OUTPATIENT_DAYS;
        int durationMinutes = computeSessionDuration(riskLevel, isInpatient);

        // Determine total intervention duration from longest goal
        int maxWeeks = goals.stream().mapToInt(LifestyleGoal::targetWeeks).max().orElse(8);

        // Start scheduling from next valid session day, one week ahead
        LocalDate startDate = LocalDate.now().plusWeeks(SCHEDULING_LEAD_WEEKS);
        while (!sessionDays.contains(startDate.getDayOfWeek())) {
            startDate = startDate.plusDays(1);
        }

        LocalDate current = startDate;
        int weekCount = 0;
        int sessionIndex = 0;

        // Generate sessions until we reach the target number of weeks
        while (weekCount < maxWeeks) {
            if (sessionDays.contains(current.getDayOfWeek())) {
                // Cycle through goals to assign interventions
                LifestyleGoal goal = goals.get(sessionIndex % goals.size());
                String interventionType = selectIntervention(goal, interventionPlan);

                String sessionId = "S" + String.format("%03d", sessions.size() + 1) + "-" +
                    UUID.randomUUID().toString().substring(0, 6);

                sessions.add(new TherapySession(
                    sessionId,
                    patient.id(),
                    interventionType,
                    current.format(DATE_FMT),
                    durationMinutes,
                    TherapySession.STATUS_SCHEDULED,
                    0.0
                ));
                sessionIndex++;
            }

            current = current.plusDays(1);

            // Track weeks (increment on Monday after processing)
            if (current.getDayOfWeek() == DayOfWeek.MONDAY && current.isAfter(startDate)) {
                weekCount++;
            }
        }

        return sessions;
    }

    /**
     * Computes appropriate session duration based on clinical risk and programme type.
     *
     * <p>HIGH-risk patients receive shorter sessions (45 min) to manage fatigue.
     * Inpatient programmes typically use 45-minute sessions. Standard outpatient
     * sessions are 60 minutes. Complex cases may require 90-minute sessions.</p>
     *
     * @param riskLevel clinical risk level
     * @param isInpatient whether programme is inpatient
     * @return session duration in minutes
     */
    private int computeSessionDuration(String riskLevel, boolean isInpatient) {
        if ("HIGH".equals(riskLevel)) {
            return HIGH_RISK_DURATION_MINUTES;
        }
        if (isInpatient) {
            return INPATIENT_DURATION_MINUTES;
        }
        return STANDARD_DURATION_MINUTES;
    }

    /**
     * Selects the intervention type for a given goal.
     *
     * <p>If the intervention plan contains a specific intervention for the goal's area,
     * it is selected. Otherwise, a generic intervention label is constructed from
     * the goal's target area.</p>
     *
     * @param goal the lifestyle goal
     * @param interventionPlan mapping of occupational areas to intervention descriptions
     * @return intervention type description
     */
    private String selectIntervention(LifestyleGoal goal, Map<String, String> interventionPlan) {
        String planIntervention = interventionPlan.get(goal.targetArea());
        if (planIntervention != null && !planIntervention.isEmpty()) {
            return planIntervention;
        }
        return "Occupational therapy — " + goal.targetArea();
    }
}
