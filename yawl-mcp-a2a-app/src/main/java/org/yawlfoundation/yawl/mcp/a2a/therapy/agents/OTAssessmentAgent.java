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
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OTPatient;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OccupationalProfile;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.SwarmTaskResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 2: COPM occupational assessment and profile generation.
 *
 * <p>Conducts the Canadian Occupational Performance Measure (COPM) assessment
 * across three performance areas: self-care, productivity, and leisure. Generates
 * performance (1-10) and satisfaction (1-10) scores using clinical heuristics
 * derived from the patient's condition and referral information.</p>
 *
 * <h2>COPM Score Interpretation</h2>
 * <ul>
 *   <li>1-3: Unable to perform (severe impairment)</li>
 *   <li>4-6: Able to perform with significant difficulty</li>
 *   <li>7-8: Able to perform with moderate difficulty</li>
 *   <li>9-10: Able to perform well (near-normal)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class OTAssessmentAgent implements OTSwarmAgent {

    private static final List<String> OCCUPATIONAL_AREAS = List.of(
        "self-care", "productivity", "leisure"
    );

    /**
     * Returns the swarm phase this agent handles.
     *
     * @return SwarmPhase.ASSESSMENT
     */
    @Override
    public SwarmPhase phase() {
        return SwarmPhase.ASSESSMENT;
    }

    /**
     * Executes COPM occupational assessment for the given patient.
     *
     * <p>Generates performance and satisfaction scores across three occupational
     * performance areas (self-care, productivity, leisure) using clinical heuristics
     * derived from the patient's condition. Ranks priority areas and generates an
     * OccupationalProfile record for downstream use.</p>
     *
     * @param patient the OTPatient to assess (non-null)
     * @param context mutable context map populated by this agent and prior agents
     * @return SwarmTaskResult.success() with occupational profile, or
     *         SwarmTaskResult.failure() if validation fails
     */
    @Override
    public SwarmTaskResult execute(OTPatient patient, Map<String, Object> context) {
        if (patient == null) {
            return SwarmTaskResult.failure(
                agentId(), phase(),
                "Patient cannot be null"
            );
        }

        var performanceScores = assessPerformance(patient);
        var satisfactionScores = assessSatisfaction(patient, performanceScores);
        var priorityAreas = rankPriorityAreas(performanceScores, satisfactionScores, patient);
        var notes = buildAssessmentNotes(patient, performanceScores, satisfactionScores);

        var profile = new OccupationalProfile(
            patient.id(), performanceScores, satisfactionScores, priorityAreas, notes
        );

        var data = new HashMap<String, Object>();
        data.put("profile", profile);
        data.put("mean_performance", profile.meanPerformanceScore());
        data.put("mean_satisfaction", profile.meanSatisfactionScore());
        data.put("priority_area", profile.topPriorityArea());

        var output = String.format(
            "COPM assessment complete. Performance: %.1f/10, Satisfaction: %.1f/10. " +
            "Priority area: %s. %d areas assessed.",
            profile.meanPerformanceScore(), profile.meanSatisfactionScore(),
            profile.topPriorityArea(), performanceScores.size()
        );

        return SwarmTaskResult.success(agentId(), phase(), output, data);
    }

    /**
     * Assesses occupational performance across the three performance areas.
     *
     * <p>Uses condition-based heuristics to score each area on a 1-10 scale.
     * Neurological conditions impact self-care most. Cognitive/mental health conditions
     * impact productivity. Leisure is typically least affected.</p>
     *
     * @param patient the patient to assess
     * @return map of area name to performance score (1-10)
     */
    private Map<String, Integer> assessPerformance(OTPatient patient) {
        var scores = new LinkedHashMap<String, Integer>();
        String conditionLower = patient.condition().toLowerCase();

        int baseScore = deriveBaseScore(conditionLower);

        // Self-care heavily impacted by neurological and physical conditions
        int selfCareScore = conditionLower.contains("stroke") || conditionLower.contains("brain") ?
            Math.max(1, baseScore - 2) : baseScore;

        // Productivity impacted by cognitive and mental health conditions
        int productivityScore = conditionLower.contains("depression") || conditionLower.contains("cognitive") ?
            Math.max(1, baseScore - 2) : Math.min(10, baseScore + 1);

        // Leisure typically last affected, first recovered
        int leisureScore = Math.min(10, baseScore + 1);

        scores.put("self-care", selfCareScore);
        scores.put("productivity", productivityScore);
        scores.put("leisure", leisureScore);
        return scores;
    }

    /**
     * Derives a base COPM score from condition severity.
     *
     * <p>Maps condition keywords to approximate baseline performance severity,
     * which is then adjusted per-area by assessPerformance().</p>
     *
     * @param conditionLower condition string in lowercase
     * @return base score reflecting general impairment severity
     */
    private int deriveBaseScore(String conditionLower) {
        if (conditionLower.contains("severe") || conditionLower.contains("traumatic")) return 3;
        if (conditionLower.contains("moderate") || conditionLower.contains("stroke")) return 4;
        if (conditionLower.contains("chronic") || conditionLower.contains("depression")) return 5;
        if (conditionLower.contains("mild") || conditionLower.contains("anxiety")) return 6;
        return 6; // default moderate impairment
    }

    /**
     * Assesses patient satisfaction with occupational performance.
     *
     * <p>Satisfaction tends to be 1-2 points lower than performance. Geriatric
     * patients often report lower satisfaction relative to their actual performance.</p>
     *
     * @param patient the patient to assess
     * @param performanceScores map of area to performance score from assessPerformance()
     * @return map of area name to satisfaction score (1-10)
     */
    private Map<String, Integer> assessSatisfaction(OTPatient patient,
                                                      Map<String, Integer> performanceScores) {
        var scores = new LinkedHashMap<String, Integer>();
        performanceScores.forEach((area, perfScore) -> {
            // Geriatric patients often have lower satisfaction with current function
            int satScore = patient.isGeriatric() ?
                Math.max(1, perfScore - 2) : Math.max(1, perfScore - 1);
            scores.put(area, satScore);
        });
        return scores;
    }

    /**
     * Ranks occupational areas by priority based on performance and satisfaction deficits.
     *
     * <p>Priority is determined by the lowest combined performance+satisfaction score,
     * indicating the area with greatest need for improvement.</p>
     *
     * @param performance map of area to performance score
     * @param satisfaction map of area to satisfaction score
     * @param patient the patient being assessed (unused but available for future heuristics)
     * @return ordered list of areas from highest to lowest priority
     */
    private List<String> rankPriorityAreas(Map<String, Integer> performance,
                                           Map<String, Integer> satisfaction,
                                           OTPatient patient) {
        var areas = new ArrayList<>(OCCUPATIONAL_AREAS);
        areas.sort((a, b) -> {
            int scoreA = performance.getOrDefault(a, 10) + satisfaction.getOrDefault(a, 10);
            int scoreB = performance.getOrDefault(b, 10) + satisfaction.getOrDefault(b, 10);
            return Integer.compare(scoreA, scoreB);
        });
        return areas;
    }

    /**
     * Builds a structured COPM assessment note as a clinical document.
     *
     * <p>Presents performance and satisfaction scores in tabular format with
     * clinical impression based on the patient's condition.</p>
     *
     * @param patient the patient being assessed
     * @param perf map of area to performance score
     * @param sat map of area to satisfaction score
     * @return formatted assessment notes
     */
    private String buildAssessmentNotes(OTPatient patient,
                                        Map<String, Integer> perf,
                                        Map<String, Integer> sat) {
        var sb = new StringBuilder();
        sb.append("COPM Assessment Results\n");
        sb.append("Patient: ").append(patient.name()).append("\n");
        sb.append("Area | Performance | Satisfaction\n");
        perf.forEach((area, score) ->
            sb.append(area).append(" | ")
              .append(score).append("/10 | ")
              .append(sat.getOrDefault(area, 1)).append("/10\n")
        );
        sb.append("Clinical impression: ").append(patient.condition())
          .append(" presenting with functional deficits across occupational performance areas.");
        return sb.toString();
    }
}
