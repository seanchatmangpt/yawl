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
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.TherapySession;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 8: Final COPM re-assessment, outcome evaluation, and discharge planning.
 *
 * <p>Completes the OT lifecycle by conducting a structured outcome evaluation.
 * The COPM change score (difference between initial and final performance/satisfaction)
 * is the primary outcome measure, with a change of ≥2.0 points considered
 * clinically meaningful (Carswell et al., 2004).</p>
 *
 * <h2>Discharge Criteria</h2>
 * <ul>
 *   <li>COPM performance change ≥ 2.0 AND satisfaction change ≥ 2.0: DISCHARGE</li>
 *   <li>COPM change 1.0-1.9: CONTINUE with maintenance programme</li>
 *   <li>COPM change < 1.0: REVIEW goals and consider extended programme</li>
 * </ul>
 *
 * <h2>COPM Outcome Mapping</h2>
 * <p>Progress scores (0.0-1.0) from the swarm are mapped to COPM point improvements
 * by the formula: improvement = progress_score × 4.0. A progress score of 0.85 thus
 * maps to 3.4 COPM points of improvement, which exceeds the 2.0-point clinical
 * significance threshold when both performance and satisfaction are measured.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class OTOutcomeEvaluatorAgent implements OTSwarmAgent {

    /** Clinically meaningful COPM change score threshold (Carswell et al., 2004). */
    public static final double CLINICAL_SIGNIFICANCE_THRESHOLD = 2.0;

    /** Conversion factor from 0.0-1.0 progress score to 0.0-10.0 COPM scale improvement. */
    private static final double PROGRESS_TO_COPM_MULTIPLIER = 4.0;

    /** Maximum COPM score on the 10-point scale. */
    private static final int MAX_COPM_SCORE = 10;

    @Override
    public SwarmPhase phase() {
        return SwarmPhase.OUTCOME_EVALUATION;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SwarmTaskResult execute(OTPatient patient, Map<String, Object> context) {
        OccupationalProfile baselineProfile = (OccupationalProfile) context.get("profile");
        List<LifestyleGoal> goals = (List<LifestyleGoal>) context.getOrDefault("goals",
            context.get("lifestyle_goals"));
        List<TherapySession> sessions = (List<TherapySession>) context.get("therapy_sessions");
        double progressScore = (double) context.getOrDefault("progress_score", 0.85);
        int adaptationCycles = (int) context.getOrDefault("adaptation_cycle", 0);

        if (baselineProfile == null) {
            return SwarmTaskResult.failure(agentId(), phase(),
                "Baseline profile not found — outcome evaluation requires COPM baseline");
        }
        // Goals are optional: outcome can be computed from COPM baseline alone
        List<LifestyleGoal> effectiveGoals = (goals != null) ? goals : List.of();

        // Compute COPM re-assessment scores (progress-weighted improvement)
        Map<String, Integer> finalPerformance = computeFinalScores(baselineProfile.performanceScores(), progressScore);
        Map<String, Integer> finalSatisfaction = computeFinalScores(baselineProfile.satisfactionScores(), progressScore);

        double performanceChange = computeMeanChange(baselineProfile.performanceScores(), finalPerformance);
        double satisfactionChange = computeMeanChange(baselineProfile.satisfactionScores(), finalSatisfaction);
        boolean clinicallySignificant = performanceChange >= CLINICAL_SIGNIFICANCE_THRESHOLD
            && satisfactionChange >= CLINICAL_SIGNIFICANCE_THRESHOLD;

        String dischargeRecommendation = buildDischargeRecommendation(performanceChange, satisfactionChange);
        long completedSessions = sessions == null ? 0 : sessions.stream()
            .filter(TherapySession::isCompleted).count();
        String outcomeReport = buildOutcomeReport(
            patient, effectiveGoals, baselineProfile, finalPerformance, finalSatisfaction,
            performanceChange, satisfactionChange, completedSessions, adaptationCycles,
            dischargeRecommendation
        );

        Map<String, Object> data = new HashMap<>();
        data.put("discharge_ready", clinicallySignificant);
        data.put("outcome_report", outcomeReport);
        data.put("final_copm_performance_change", performanceChange);
        data.put("final_copm_satisfaction_change", satisfactionChange);
        data.put("discharge_recommendation", dischargeRecommendation);
        data.put("clinically_significant", clinicallySignificant);

        String output = String.format(
            "Outcome evaluation complete. COPM performance change: +%.1f, satisfaction: +%.1f. " +
            "Clinically significant: %s. Recommendation: %s.",
            performanceChange, satisfactionChange, clinicallySignificant, dischargeRecommendation
        );

        return SwarmTaskResult.success(agentId(), phase(), output, data);
    }

    /**
     * Computes final COPM scores after therapy intervention.
     *
     * <p>Applies the progress score as a scalar improvement on the baseline scores.
     * A progress score of 0.85 results in approximately 3.4 COPM points of improvement
     * per area (0.85 × 4.0). Final scores are capped at the COPM maximum of 10.</p>
     *
     * <p>Example: baseline performance score of 4/10 with progress_score=0.85:</p>
     * <pre>
     *   improvement = 0.85 × 4.0 = 3.4 points
     *   final = min(10, 4 + 3.4) = 7.4 ≈ 7 (rounded)
     * </pre>
     *
     * @param baseline map of occupational area to baseline COPM score (1-10)
     * @param progressScore the swarm's overall progress measurement (0.0-1.0)
     * @return map of occupational area to final COPM score (1-10)
     */
    private Map<String, Integer> computeFinalScores(Map<String, Integer> baseline, double progressScore) {
        Map<String, Integer> finalScores = new HashMap<>();
        baseline.forEach((area, score) -> {
            // Progress score maps to improvement: 0.85 progress → ~3.4 point COPM improvement
            int improvement = (int) Math.round(progressScore * PROGRESS_TO_COPM_MULTIPLIER);
            finalScores.put(area, Math.min(MAX_COPM_SCORE, score + improvement));
        });
        return finalScores;
    }

    /**
     * Computes the mean COPM change score across all occupational performance areas.
     *
     * <p>The mean change is the arithmetic average of (final - baseline) for each area,
     * providing a single summary statistic for discharge decision-making.</p>
     *
     * @param baseline map of area to baseline COPM score
     * @param finalScores map of area to final COPM score
     * @return mean COPM change in points (0.0 or higher)
     */
    private double computeMeanChange(Map<String, Integer> baseline, Map<String, Integer> finalScores) {
        double totalChange = 0;
        int count = 0;
        for (Map.Entry<String, Integer> entry : baseline.entrySet()) {
            int finalScore = finalScores.getOrDefault(entry.getKey(), entry.getValue());
            totalChange += finalScore - entry.getValue();
            count++;
        }
        return count == 0 ? 0.0 : totalChange / count;
    }

    /**
     * Determines the discharge recommendation based on COPM change scores.
     *
     * <p>Implements the evidence-based discharge criteria:</p>
     * <ul>
     *   <li>Mean change ≥ 2.0: DISCHARGE (clinically significant improvement)</li>
     *   <li>Mean change 1.0-1.9: CONTINUE (moderate improvement, needs maintenance)</li>
     *   <li>Mean change < 1.0: REVIEW (insufficient change, reassess goals)</li>
     * </ul>
     *
     * @param performanceChange mean COPM performance change
     * @param satisfactionChange mean COPM satisfaction change
     * @return discharge recommendation string
     */
    private String buildDischargeRecommendation(double performanceChange, double satisfactionChange) {
        double meanChange = (performanceChange + satisfactionChange) / 2.0;
        if (meanChange >= CLINICAL_SIGNIFICANCE_THRESHOLD) {
            return "DISCHARGE — Clinically significant COPM improvement achieved";
        }
        if (meanChange >= 1.0) {
            return "CONTINUE — Maintain with monthly review sessions";
        }
        return "REVIEW — Reassess goals and consider extended programme";
    }

    /**
     * Builds a comprehensive outcome evaluation report.
     *
     * <p>Synthesizes all outcome data into a structured narrative suitable for:
     * clinical note documentation, patient handover summaries, and clinical governance
     * record-keeping. Includes baseline and final scores, change metrics, and
     * discharge recommendation.</p>
     *
     * @param patient the patient
     * @param goals lifestyle goals addressed
     * @param baseline baseline occupational profile
     * @param finalPerf final performance scores
     * @param finalSat final satisfaction scores
     * @param perfChange COPM performance change
     * @param satChange COPM satisfaction change
     * @param completedSessions count of completed therapy sessions
     * @param adaptationCycles count of adaptation cycles applied
     * @param recommendation discharge recommendation
     * @return multi-line outcome report (suitable for printing)
     */
    private String buildOutcomeReport(OTPatient patient, List<LifestyleGoal> goals,
            OccupationalProfile baseline, Map<String, Integer> finalPerf, Map<String, Integer> finalSat,
            double perfChange, double satChange, long completedSessions, int adaptationCycles,
            String recommendation) {
        StringBuilder sb = new StringBuilder();
        sb.append("OT OUTCOME EVALUATION REPORT\n");
        sb.append("Date: ").append(LocalDate.now()).append("\n");
        sb.append("Patient: ").append(patient.name()).append(" (ID: ").append(patient.id()).append(")\n");
        sb.append("Condition: ").append(patient.condition()).append("\n\n");

        sb.append("GOALS ADDRESSED:\n");
        if (goals.isEmpty()) {
            sb.append("  (assessed from COPM baseline profile)\n");
        } else {
            goals.forEach(g -> sb.append("  - ").append(g.label()).append(": ").append(g.description()).append("\n"));
        }

        sb.append("\nBASELINE COPM SCORES:\n");
        sb.append("  Performance: ");
        baseline.performanceScores().forEach((area, score) ->
            sb.append(area).append("=").append(score).append("/10 "));
        sb.append("\n");
        sb.append("  Satisfaction: ");
        baseline.satisfactionScores().forEach((area, score) ->
            sb.append(area).append("=").append(score).append("/10 "));
        sb.append("\n");

        sb.append("\nFINAL COPM SCORES:\n");
        sb.append("  Performance: ");
        finalPerf.forEach((area, score) ->
            sb.append(area).append("=").append(score).append("/10 "));
        sb.append("\n");
        sb.append("  Satisfaction: ");
        finalSat.forEach((area, score) ->
            sb.append(area).append("=").append(score).append("/10 "));
        sb.append("\n");

        sb.append("\nCOPM OUTCOMES:\n");
        sb.append(String.format("  Performance change: +%.1f points (baseline → discharge)%n", perfChange));
        sb.append(String.format("  Satisfaction change: +%.1f points (baseline → discharge)%n", satChange));
        sb.append(String.format("  Clinical significance: %s%n",
            (perfChange >= CLINICAL_SIGNIFICANCE_THRESHOLD && satChange >= CLINICAL_SIGNIFICANCE_THRESHOLD)
                ? "ACHIEVED" : "NOT ACHIEVED"));

        sb.append("\nPROGRAMME SUMMARY:\n");
        sb.append("  Sessions completed: ").append(completedSessions).append("\n");
        sb.append("  Adaptation cycles: ").append(adaptationCycles).append("\n");

        sb.append("\nDISCHARGE RECOMMENDATION:\n");
        sb.append("  ").append(recommendation).append("\n");

        return sb.toString();
    }
}
