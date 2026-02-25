/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.cache;

import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Engine for generating intervention suggestions based on patient profiles.
 *
 * @since 6.0.0
 */
public class InterventionSuggestionEngine {

    private final ZaiService zaiService;

    public InterventionSuggestionEngine() {
        this.zaiService = null;
    }

    public InterventionSuggestionEngine(ZaiService zaiService) {
        this.zaiService = zaiService;
    }

    /**
     * Generates intervention suggestions for a patient.
     */
    public List<String> generateSuggestions(String condition, int age, String goals) {
        if (zaiService == null) {
            return List.of("Standard therapeutic intervention recommended");
        }

        String prompt = String.format("""
            Generate 3-5 evidence-based occupational therapy intervention suggestions for:
            - Condition: %s
            - Age: %d
            - Goals: %s

            Format as a simple list, one suggestion per line.
            """, condition, age, goals);

        String response = zaiService.chat(prompt);
        return parseSuggestionsRaw(response);
    }

    /**
     * Generates interventions based on clinical profile.
     */
    public List<String> generateFromProfile(Map<String, Object> profile) {
        String condition = (String) profile.getOrDefault("condition", "general");
        int age = (int) profile.getOrDefault("age", 30);
        String goals = (String) profile.getOrDefault("goals", "improve function");
        return generateSuggestions(condition, age, goals);
    }

    /**
     * Parses AI response into structured intervention suggestions.
     *
     * @param aiResponse the raw AI response
     * @return list of intervention suggestions
     */
    public List<InterventionSuggestion> parseSuggestions(String aiResponse) {
        List<InterventionSuggestion> suggestions = new ArrayList<>();

        if (aiResponse == null || aiResponse.isBlank()) {
            suggestions.add(new InterventionSuggestion(
                "Standard therapeutic intervention",
                "Standard therapeutic intervention based on patient profile",
                "Evidence-based approach",
                "Improved functional independence"
            ));
            return suggestions;
        }

        String[] lines = aiResponse.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && (trimmed.startsWith("-") ||
                trimmed.startsWith("*") || trimmed.matches("^\\d+\\..*"))) {
                String content = trimmed.replaceAll("^[\\-\\*\\d\\.\\s]+", "").trim();
                if (!content.isEmpty()) {
                    suggestions.add(new InterventionSuggestion(
                        content,
                        content,
                        "Based on clinical guidelines",
                        "Improved patient outcomes"
                    ));
                }
            }
        }

        if (suggestions.isEmpty()) {
            suggestions.add(new InterventionSuggestion(
                "Standard therapeutic intervention",
                aiResponse.trim(),
                "Based on AI recommendation",
                "Improved functional independence"
            ));
        }

        return suggestions;
    }

    /**
     * Updates effectiveness tracking for recommendations.
     *
     * @param recommendations the therapy recommendations
     * @param outcomeData the outcome data
     */
    public void updateEffectiveness(TherapyRecommendations recommendations, OutcomeData outcomeData) {
        // Track effectiveness for future ML training
        // This is a placeholder for actual effectiveness tracking
    }

    private List<String> parseSuggestionsRaw(String response) {
        if (response == null || response.isBlank()) {
            return List.of("Standard therapeutic intervention recommended");
        }
        return response.lines()
            .filter(line -> !line.isBlank())
            .map(String::trim)
            .limit(5)
            .toList();
    }
}
