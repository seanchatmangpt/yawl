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
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OccupationalProfile;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.SwarmTaskResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 3: Collaborative lifestyle goal identification using COPM priorities.
 *
 * <p>Translates COPM assessment findings into up to 3 SMART (Specific, Measurable,
 * Achievable, Relevant, Time-bound) lifestyle goals. Goals are prioritised by
 * occupational need (lowest COPM scores = highest priority). Each goal incorporates
 * the patient's functional language and clinically-informed severity-based timescales.</p>
 *
 * <h2>Goal Formulation Logic</h2>
 * <p>Each goal is formulated from the corresponding COPM priority area,
 * incorporating the patient's stated functional goal and clinical findings.
 * Target timescales reflect the severity of impairment (lower COPM score = longer timeframe):</p>
 * <ul>
 *   <li>Score 1-3 (severe): 12 weeks</li>
 *   <li>Score 4-5 (moderate): 8 weeks</li>
 *   <li>Score 6-7 (mild): 6 weeks</li>
 *   <li>Score 8-10 (minimal): 4 weeks</li>
 * </ul>
 *
 * <h2>Context Requirements</h2>
 * <p>Expects {@code occupational_profile} (OccupationalProfile) in context.
 * Writes {@code lifestyle_goals} (List&lt;LifestyleGoal&gt;),
 * {@code goal_count} (int), and {@code primary_goal} (String) to context.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class OTGoalSettingAgent implements OTSwarmAgent {

    private static final Map<String, String> AREA_DESCRIPTIONS = Map.of(
        "self-care", "independently perform daily self-care activities with confidence",
        "productivity", "return to meaningful and sustained productive occupation",
        "leisure", "engage actively in valued leisure and recreational activities"
    );

    private static final Map<String, String> AREA_CRITERIA = Map.of(
        "self-care", "achieve independence on COPM self-care items scored ≥7/10 on re-assessment",
        "productivity", "sustain productive activity for ≥4 hours/day, 5 days/week without fatigue",
        "leisure", "engage in at least 2 leisure activities per week independently and with satisfaction"
    );

    /**
     * Returns the swarm phase this agent handles.
     *
     * @return SwarmPhase.GOAL_SETTING
     */
    @Override
    public SwarmPhase phase() {
        return SwarmPhase.GOAL_SETTING;
    }

    /**
     * Executes goal-setting for the given patient.
     *
     * <p>Reads the occupational profile from context, generates up to 3 SMART goals
     * based on COPM priority areas and performance scores, and populates context
     * with goal metadata for downstream intervention planning.</p>
     *
     * @param patient the OTPatient to set goals for (non-null)
     * @param context mutable context map containing occupational_profile from prior assessment
     * @return SwarmTaskResult.success() with goal metadata, or SwarmTaskResult.failure()
     *         if occupational profile is missing
     */
    @Override
    public SwarmTaskResult execute(OTPatient patient, Map<String, Object> context) {
        if (patient == null) {
            return SwarmTaskResult.failure(agentId(), phase(),
                "Patient cannot be null");
        }

        Object profileObj = context.get("profile");
        if (profileObj == null || !(profileObj instanceof OccupationalProfile profile)) {
            return SwarmTaskResult.failure(agentId(), phase(),
                "Profile not found in context — assessment must precede goal-setting");
        }

        List<LifestyleGoal> goals = generateGoals(patient, profile);

        if (goals.isEmpty()) {
            return SwarmTaskResult.failure(agentId(), phase(),
                "Unable to generate goals: no priority areas found in occupational profile");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("goals", goals);
        data.put("goal_count", goals.size());
        data.put("primary_goal", goals.getFirst().description());
        data.put("goal_areas", goals.stream().map(LifestyleGoal::targetArea).toList());

        String output = String.format(
            "Goal-setting complete: %d SMART goals identified. Primary: \"%s\" (%d weeks target).",
            goals.size(), goals.getFirst().description(), goals.getFirst().targetWeeks()
        );

        return SwarmTaskResult.success(agentId(), phase(), output, data);
    }

    /**
     * Generates up to 3 SMART lifestyle goals from COPM priority areas.
     *
     * <p>Translates each priority area into a SMART goal description that incorporates
     * the patient's functional language, performance severity, and measurement criteria.</p>
     *
     * @param patient the patient to generate goals for
     * @param profile the occupational profile from COPM assessment
     * @return list of generated LifestyleGoal records (up to 3)
     */
    private List<LifestyleGoal> generateGoals(OTPatient patient, OccupationalProfile profile) {
        List<LifestyleGoal> goals = new ArrayList<>();

        List<String> priorityAreas = profile.priorityAreas();
        int maxGoals = Math.min(3, priorityAreas.size());

        for (int i = 0; i < maxGoals; i++) {
            String area = priorityAreas.get(i);
            Integer performanceScore = profile.performanceScores().get(area);

            if (performanceScore == null || performanceScore < 1 || performanceScore > 10) {
                performanceScore = 5;
            }

            int targetWeeks = calculateTargetWeeks(performanceScore);
            String description = buildGoalDescription(patient, area, performanceScore);
            String criteria = AREA_CRITERIA.getOrDefault(area,
                "demonstrate measurable improvement on standardised OT outcome measures");

            LifestyleGoal goal = new LifestyleGoal(
                generateGoalId(i),
                description,
                i + 1,
                area,
                criteria,
                targetWeeks
            );

            goals.add(goal);
        }

        return goals;
    }

    /**
     * Calculates target weeks for goal achievement based on COPM performance score.
     *
     * <p>Applies clinical heuristic: lower performance scores indicate greater
     * functional impairment and require longer intervention periods.</p>
     *
     * @param performanceScore COPM performance score (1-10)
     * @return target weeks (4, 6, 8, or 12)
     */
    private int calculateTargetWeeks(int performanceScore) {
        if (performanceScore <= 3) {
            return 12;
        }
        if (performanceScore <= 5) {
            return 8;
        }
        if (performanceScore <= 7) {
            return 6;
        }
        return 4;
    }

    /**
     * Builds a SMART goal description incorporating patient language and clinical context.
     *
     * <p>Formulates the goal statement using the SMART framework (Specific, Measurable,
     * Achievable, Relevant, Time-bound), incorporating the patient's own functional
     * goal keywords where possible.</p>
     *
     * @param patient the patient (source of functional goal language)
     * @param area the occupational performance area (self-care, productivity, leisure)
     * @param performanceScore the COPM performance score for this area
     * @return a complete SMART goal description
     */
    private String buildGoalDescription(OTPatient patient, String area, int performanceScore) {
        String areaDescription = AREA_DESCRIPTIONS.getOrDefault(area,
            "improve occupational performance");
        String goalKeyword = extractFunctionalKeyword(patient.functionalGoal());
        int weeks = calculateTargetWeeks(performanceScore);

        return String.format(
            "%s will %s, specifically relating to %s, within %d weeks as evidenced by COPM re-assessment scoring ≥7/10 on identified priority areas",
            patient.name(), areaDescription, goalKeyword, weeks
        );
    }

    /**
     * Extracts the core functional activity concept from the patient's stated goal.
     *
     * <p>Performs simple keyword matching on the patient's functional goal to identify
     * the primary activity domain they are describing, then returns a clinically
     * appropriate descriptor for incorporation into the formal goal statement.</p>
     *
     * @param functionalGoal the patient-stated functional goal in their own words
     * @return a functional activity descriptor suitable for a SMART goal
     */
    private String extractFunctionalKeyword(String functionalGoal) {
        if (functionalGoal == null || functionalGoal.isBlank()) {
            return "functional daily activities";
        }

        String lower = functionalGoal.toLowerCase();

        if (lower.contains("walk") || lower.contains("mobil") || lower.contains("ambul")) {
            return "mobility and ambulation";
        }
        if (lower.contains("cook") || lower.contains("meal") || lower.contains("food")) {
            return "meal preparation and nutrition";
        }
        if (lower.contains("work") || lower.contains("job") || lower.contains("employ")) {
            return "work participation and employment";
        }
        if (lower.contains("dress") || lower.contains("groom") || lower.contains("shower")
            || lower.contains("bath") || lower.contains("toilet")) {
            return "personal care and grooming";
        }
        if (lower.contains("social") || lower.contains("family") || lower.contains("friend")) {
            return "social participation and relationships";
        }
        if (lower.contains("drive") || lower.contains("transport") || lower.contains("commut")) {
            return "community transportation";
        }
        if (lower.contains("care") || lower.contains("parent") || lower.contains("child")) {
            return "caregiving responsibilities";
        }
        if (lower.contains("hobby") || lower.contains("recreation") || lower.contains("sport")) {
            return "recreational and leisure engagement";
        }
        if (lower.contains("home") || lower.contains("house") || lower.contains("clean")) {
            return "home management and maintenance";
        }

        return "functional daily activities";
    }

    /**
     * Generates a unique goal identifier.
     *
     * <p>Creates a goal ID combining priority indicator and UUID fragment
     * for guaranteed uniqueness within a patient's programme.</p>
     *
     * @param priorityIndex zero-based priority index (0=first priority)
     * @return unique goal identifier
     */
    private String generateGoalId(int priorityIndex) {
        String uuidFragment = UUID.randomUUID().toString().substring(0, 8);
        return "G" + (priorityIndex + 1) + "-" + uuidFragment;
    }
}
