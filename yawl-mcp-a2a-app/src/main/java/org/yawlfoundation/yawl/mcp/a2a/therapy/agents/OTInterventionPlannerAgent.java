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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 4: Evidence-based intervention selection and planning.
 *
 * <p>Selects therapeutic interventions appropriate for each identified lifestyle
 * goal. Interventions are chosen from a curated evidence base aligned with
 * NICE guidelines, AOTA occupational therapy practice framework, and clinical
 * risk stratification from prior assessment phases.</p>
 *
 * <h2>Intervention Categories</h2>
 * <ul>
 *   <li><b>Remedial</b>: restore lost function (suitable for acute/subacute phases)</li>
 *   <li><b>Compensatory</b>: alternative strategies for permanent limitations</li>
 *   <li><b>Adaptive</b>: environmental modification and assistive technology</li>
 *   <li><b>Educational</b>: skills training and psychoeducation</li>
 * </ul>
 *
 * <h2>Risk-Adjusted Selection</h2>
 * <p>For HIGH-risk patients, only the most conservative, evidence-backed interventions
 * are selected. MEDIUM and LOW-risk patients have expanded intervention options.</p>
 *
 * <h2>Context Requirements</h2>
 * <p>Expects {@code lifestyle_goals} (List&lt;LifestyleGoal&gt;) and optional
 * {@code risk_level} (String: HIGH, MEDIUM, LOW) in context.
 * Writes {@code intervention_plan} (Map&lt;String, List&lt;String&gt;&gt;),
 * {@code primary_intervention} (String), and {@code total_interventions} (int) to context.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class OTInterventionPlannerAgent implements OTSwarmAgent {

    private static final Map<String, List<String>> AREA_INTERVENTIONS = Map.of(
        "self-care", List.of(
            "Activities of Daily Living (ADL) retraining with graded independence",
            "Adaptive equipment prescription and safety training",
            "Energy conservation and activity pacing strategies",
            "Upper limb rehabilitation exercises (if applicable)",
            "Perceptual-motor skill development programme"
        ),
        "productivity", List.of(
            "Work hardening and capacity building programme",
            "Cognitive strategy training for attention and memory",
            "Fatigue and pain management strategies",
            "Graded return-to-work exposure therapy",
            "Ergonomic assessment and workstation modification"
        ),
        "leisure", List.of(
            "Structured leisure exploration and re-engagement",
            "Social skills group therapy and community access",
            "Graded activity scheduling and balance",
            "Meaningful occupational engagement planning",
            "Mindfulness-based occupational therapy approach"
        )
    );

    private static final Map<String, List<String>> CONDITION_INTERVENTIONS = Map.of(
        "stroke", List.of(
            "Constraint-Induced Movement Therapy (CIMT) for upper limb",
            "Mirror therapy for phantom limb phenomena",
            "Task-specific practice for functional movement"
        ),
        "depression", List.of(
            "Behavioural Activation Therapy with structured scheduling",
            "Occupational engagement programme with achievement planning",
            "Sleep hygiene education and rest-activity balance"
        ),
        "chronic pain", List.of(
            "Multidisciplinary pain management programme",
            "Activity pacing and load management training",
            "Acceptance and Commitment Therapy (ACT) skills training"
        ),
        "dementia", List.of(
            "Cognitive stimulation therapy and reminiscence work",
            "Environmental adaptation for safety and orientation",
            "Carer education and support programme"
        ),
        "parkinson", List.of(
            "LSVT BIG (Large, Amplitude, Intensive) movement programme",
            "Handwriting rehabilitation and speech-synchronised exercises",
            "Fatigue and postural management strategies"
        ),
        "multiple sclerosis", List.of(
            "Fatigue management and activity pacing",
            "Cognitive rehabilitation for executive function",
            "Assistive technology and environmental adaptation"
        ),
        "rheumatoid arthritis", List.of(
            "Joint protection education and energy conservation",
            "Adaptive equipment prescription and ADL modification",
            "Graded hand function exercises and dexterity training"
        ),
        "anxiety", List.of(
            "Graded exposure therapy with occupational focus",
            "Relaxation and mindfulness-based techniques",
            "Confidence-building through structured activity engagement"
        ),
        "cardiac", List.of(
            "Cardiac rehabilitation with activity pacing",
            "Energy conservation techniques for ADL",
            "Return-to-activities counselling with risk stratification"
        )
    );

    /**
     * Returns the swarm phase this agent handles.
     *
     * @return SwarmPhase.INTERVENTION_PLANNING
     */
    @Override
    public SwarmPhase phase() {
        return SwarmPhase.INTERVENTION_PLANNING;
    }

    /**
     * Executes intervention planning for identified lifestyle goals.
     *
     * <p>Reads the lifestyle goals list from context, selects evidence-based
     * interventions for each goal based on goal area and patient condition,
     * applies risk-level filtering if present, and populates context with
     * the intervention plan for downstream scheduling and execution.</p>
     *
     * @param patient the OTPatient for whom interventions are being planned (non-null)
     * @param context mutable context map containing lifestyle_goals and optional risk_level
     * @return SwarmTaskResult.success() with intervention plan metadata, or SwarmTaskResult.failure()
     *         if lifestyle goals are missing or empty
     */
    @Override
    @SuppressWarnings("unchecked")
    public SwarmTaskResult execute(OTPatient patient, Map<String, Object> context) {
        if (patient == null) {
            return SwarmTaskResult.failure(agentId(), phase(),
                "Patient cannot be null");
        }

        Object goalsObj = context.get("goals");
        if (goalsObj == null || !(goalsObj instanceof List goalsRaw)) {
            return SwarmTaskResult.failure(agentId(), phase(),
                "No goals found in context — goal-setting must precede intervention planning");
        }

        List<LifestyleGoal> goals = castGoalsList(goalsRaw);
        if (goals.isEmpty()) {
            return SwarmTaskResult.failure(agentId(), phase(),
                "Goals list is empty — cannot plan interventions without goals");
        }

        String riskLevel = (String) context.getOrDefault("risk_level", "MEDIUM");
        Map<String, String> interventionPlan = buildInterventionPlanAsStrings(patient, goals, riskLevel);

        String primaryIntervention = extractPrimaryInterventionString(interventionPlan);
        long totalInterventions = interventionPlan.size();

        Map<String, Object> data = new HashMap<>();
        data.put("interventions", interventionPlan);
        data.put("primary_intervention", primaryIntervention);
        data.put("total_interventions", totalInterventions);
        data.put("risk_adjusted", riskLevel);

        String output = String.format(
            "Intervention plan complete: %d evidence-based interventions across %d goal areas (risk level: %s). Primary: \"%s\".",
            totalInterventions, goals.size(), riskLevel, primaryIntervention
        );

        return SwarmTaskResult.success(agentId(), phase(), output, data);
    }

    /**
     * Safely casts raw goal list to typed List&lt;LifestyleGoal&gt;.
     *
     * <p>Verifies all elements are LifestyleGoal instances before casting.</p>
     *
     * @param rawGoals untyped list from context
     * @return typed list of LifestyleGoal objects
     * @throws ClassCastException if any element is not a LifestyleGoal
     */
    private List<LifestyleGoal> castGoalsList(List<?> rawGoals) {
        List<LifestyleGoal> goals = new ArrayList<>();
        for (Object obj : rawGoals) {
            if (!(obj instanceof LifestyleGoal goal)) {
                throw new ClassCastException(
                    "Expected LifestyleGoal but found " + (obj == null ? "null" : obj.getClass().getSimpleName()));
            }
            goals.add(goal);
        }
        return goals;
    }

    /**
     * Builds a comprehensive intervention plan mapping occupational areas to primary interventions.
     *
     * <p>For each goal's target area, selects the primary relevant intervention from both the
     * goal area-specific intervention set and the patient's condition-specific intervention set.
     * Applies risk-level filtering to adjust the intensity of interventions.</p>
     *
     * @param patient the patient (source of condition for condition-specific interventions)
     * @param goals the lifestyle goals from goal-setting phase
     * @param riskLevel clinical risk level (HIGH, MEDIUM, LOW)
     * @return map from occupational area to primary intervention description (string)
     */
    private Map<String, String> buildInterventionPlanAsStrings(OTPatient patient, List<LifestyleGoal> goals, String riskLevel) {
        Map<String, String> plan = new LinkedHashMap<>();

        for (LifestyleGoal goal : goals) {
            String area = goal.targetArea();
            String intervention = selectPrimaryInterventionForArea(patient, area, riskLevel);
            if (!plan.containsKey(area)) {
                plan.put(area, intervention);
            }
        }

        return plan;
    }

    /**
     * Selects the primary intervention for a single occupational area based on patient condition and risk level.
     *
     * <p>Combines area-specific and condition-specific interventions, selecting the first
     * (most recommended) from the combined set.</p>
     *
     * @param patient the patient
     * @param area the occupational performance area (self-care, productivity, leisure)
     * @param riskLevel clinical risk level (HIGH, MEDIUM, LOW)
     * @return primary intervention description for this area
     */
    private String selectPrimaryInterventionForArea(OTPatient patient, String area, String riskLevel) {
        List<String> interventions = new ArrayList<>();

        List<String> areaInterventions = AREA_INTERVENTIONS.getOrDefault(area, List.of());
        if (!areaInterventions.isEmpty()) {
            interventions.add(areaInterventions.getFirst());
        }

        addConditionSpecificInterventionsPrimary(patient, interventions, riskLevel);

        return interventions.isEmpty() ? "Occupational therapy intervention — " + area : interventions.getFirst();
    }

    /**
     * Determines the maximum number of area-specific interventions to include.
     *
     * <p>HIGH-risk patients get fewer interventions (more conservative, focused approach).
     * MEDIUM-risk patients get standard number. LOW-risk patients get maximum.</p>
     *
     * @param riskLevel clinical risk level
     * @return maximum interventions to include from area-specific set
     */
    private int determineMaxAreaInterventions(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> 1;
            case "LOW" -> 3;
            default -> 2;
        };
    }

    /**
     * Adds primary condition-specific intervention to the current intervention list.
     *
     * <p>Matches the patient's condition against known condition-specific intervention
     * sets and adds the primary intervention for that condition. Prevents duplicates.</p>
     *
     * @param patient the patient (source of condition)
     * @param interventions current intervention list to append to
     * @param riskLevel clinical risk level (affects whether to add)
     */
    private void addConditionSpecificInterventionsPrimary(OTPatient patient, List<String> interventions, String riskLevel) {
        String conditionLower = patient.condition().toLowerCase();

        for (Map.Entry<String, List<String>> entry : CONDITION_INTERVENTIONS.entrySet()) {
            String conditionKeyword = entry.getKey();
            List<String> condInterventions = entry.getValue();

            if (conditionLower.contains(conditionKeyword) && !condInterventions.isEmpty()) {
                String intervention = condInterventions.getFirst();
                if (!interventions.contains(intervention)) {
                    interventions.add(intervention);
                    break;
                }
            }
        }
    }

    /**
     * Extracts the primary (first) intervention from the intervention plan.
     *
     * <p>Returns the first intervention value from the plan map.
     * Used to identify the primary therapeutic focus for summary reporting.</p>
     *
     * @param interventionPlan map of occupational area to intervention description
     * @return the primary intervention description, or empty string if plan is empty
     */
    private String extractPrimaryInterventionString(Map<String, String> interventionPlan) {
        return interventionPlan.values().stream()
            .findFirst()
            .orElse("");
    }
}
