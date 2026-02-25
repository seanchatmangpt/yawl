/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.cache;

import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for predicting therapy progress and outcomes.
 *
 * @since 6.0.0
 */
public class ProgressPredictionService {

    private final ZaiService zaiService;

    public ProgressPredictionService() {
        this.zaiService = null;
    }

    public ProgressPredictionService(ZaiService zaiService) {
        this.zaiService = zaiService;
    }

    /**
     * Predicts progress for a patient based on their profile and intervention plan.
     *
     * @return a prediction score from 0.0 to 1.0
     */
    public double predictProgress(String condition, int age, String goals, int sessionsPlanned) {
        if (zaiService == null) {
            return 0.65; // Default prediction
        }

        String prompt = String.format("""
            Predict the likelihood of achieving therapy goals (respond with ONLY a number 0.0-1.0):
            - Condition: %s
            - Age: %d
            - Goals: %s
            - Planned sessions: %d
            """, condition, age, goals, sessionsPlanned);

        String response = zaiService.chat(prompt);
        return parseScore(response);
    }

    /**
     * Predicts progress from a clinical profile map.
     */
    public double predictFromProfile(Map<String, Object> profile) {
        String condition = (String) profile.getOrDefault("condition", "general");
        int age = (int) profile.getOrDefault("age", 30);
        String goals = (String) profile.getOrDefault("goals", "improve function");
        int sessions = (int) profile.getOrDefault("sessionsPlanned", 12);
        return predictProgress(condition, age, goals, sessions);
    }

    /**
     * Predicts progress for a patient with current therapy interventions.
     *
     * @param patientProfile the patient's profile
     * @param currentTherapy the current therapy interventions
     * @return progress prediction with confidence scores
     */
    public ProgressPrediction predictProgress(
            PatientProfile patientProfile,
            List<TherapyIntervention> currentTherapy) {

        double baseScore;
        if (zaiService != null) {
            String goals = currentTherapy.stream()
                .map(TherapyIntervention::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("improve function");
            baseScore = predictProgress(
                patientProfile.primaryCondition(),
                patientProfile.getAge(),
                goals,
                currentTherapy.size() * 4
            );
        } else {
            baseScore = 0.65;
        }

        // Adjust based on functional level
        double modifier = switch (patientProfile.functionalLevel()) {
            case INDEPENDENT -> 0.1;
            case MINIMAL_ASSISTANCE -> 0.05;
            case MODERATE_ASSISTANCE -> 0.0;
            case MAXIMAL_ASSISTANCE -> -0.05;
            case DEPENDENT -> -0.1;
        };

        double predictedProgress = Math.max(0.0, Math.min(1.0, baseScore + modifier));

        Map<String, Double> milestonePredictions = new HashMap<>();
        milestonePredictions.put("Short-term goals", predictedProgress * 1.1);
        milestonePredictions.put("Medium-term goals", predictedProgress * 0.95);
        milestonePredictions.put("Long-term goals", predictedProgress * 0.8);

        Map<String, String> riskFactors = new HashMap<>();
        if (patientProfile.hasComorbidities()) {
            riskFactors.put("Comorbidities", "May slow progress");
        }
        if (patientProfile.functionalLevel().ordinal() >= 3) {
            riskFactors.put("Functional level", "Requires intensive support");
        }

        return new ProgressPrediction(
            patientProfile.patientId(),
            predictedProgress,
            0.75,
            milestonePredictions,
            riskFactors,
            Instant.now()
        );
    }

    /**
     * Generates a detailed progress prediction report.
     */
    public String generateProgressReport(String patientId, Map<String, Object> profile) {
        double score = predictFromProfile(profile);
        String likelihood = getLikelihoodDescription(score);

        return String.format("""
            Progress Prediction Report
            =========================
            Patient ID: %s
            Predicted Success: %.1f%%
            Likelihood: %s

            Recommendation: %s
            """,
            patientId,
            score * 100,
            likelihood,
            getRecommendation(score)
        );
    }

    private double parseScore(String response) {
        if (response == null || response.isBlank()) {
            return 0.5; // Default to 50% if no valid response
        }
        try {
            String cleaned = response.trim().replaceAll("[^0-9.]", "");
            if (cleaned.isEmpty()) {
                return 0.5;
            }
            double score = Double.parseDouble(cleaned);
            return Math.max(0.0, Math.min(1.0, score));
        } catch (NumberFormatException e) {
            return 0.5;
        }
    }

    private String getLikelihoodDescription(double score) {
        if (score >= 0.8) return "Very High";
        if (score >= 0.6) return "High";
        if (score >= 0.4) return "Moderate";
        if (score >= 0.2) return "Low";
        return "Very Low";
    }

    private String getRecommendation(double score) {
        if (score >= 0.7) {
            return "Proceed with planned intervention. Monitor progress at regular intervals.";
        } else if (score >= 0.4) {
            return "Consider adjusting intervention plan. May need additional sessions or modified goals.";
        } else {
            return "Recommend reassessment. Consider alternative approaches or specialist referral.";
        }
    }
}
