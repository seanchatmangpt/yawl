/*
 * Copyright 2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai;

import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.cache.InterventionSuggestionEngine;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.cache.ProgressPredictionService;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.cache.TherapyRecommendationCache;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ZAI-powered therapy recommendation service for OT interventions.
 */
public class TherapyRecommendationService {

    private final ZaiService zaiService;
    private final TherapyRecommendationCache cache;
    private final InterventionSuggestionEngine suggestionEngine;
    private final ProgressPredictionService predictionService;
    private final AdaptationRecommender adaptationRecommender;
    private final ExecutorService executorService;
    private final Map<String, RecommendationSession> activeSessions;

    public TherapyRecommendationService(ZaiService zaiService) {
        if (zaiService == null) {
            throw new IllegalArgumentException("ZaiService cannot be null");
        }
        this.zaiService = zaiService;
        this.cache = new TherapyRecommendationCache();
        this.suggestionEngine = new InterventionSuggestionEngine(zaiService);
        this.predictionService = new ProgressPredictionService(zaiService);
        this.adaptationRecommender = new AdaptationRecommender();
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.activeSessions = new ConcurrentHashMap<>();

        initializeTherapyPrompt();
    }

    private void initializeTherapyPrompt() {
        String systemPrompt = """
            You are an AI assistant for Occupational Therapy recommendations.
            You specialize in evidence-based interventions for rehabilitation.

            Guidelines:
            1. Always base recommendations on clinical evidence
            2. Consider patient's specific conditions and goals
            3. Suggest interventions that are practical and measurable
            4. Prioritize interventions that improve independence
            5. Provide clear, actionable recommendations with expected outcomes
            """;

        zaiService.setSystemPrompt(systemPrompt);
    }

    public TherapyRecommendations generateRecommendations(
            PatientProfile patientProfile,
            List<TherapyGoal> therapyGoals,
            TherapySessionContext sessionContext) {

        String cacheKey = generateCacheKey(patientProfile, therapyGoals, sessionContext);

        Optional<TherapyRecommendations> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        TherapyRecommendations recommendations = createRecommendations(
            patientProfile, therapyGoals, sessionContext);

        cache.put(cacheKey, recommendations);
        activeSessions.put(sessionContext.sessionId(),
            new RecommendationSession(sessionContext, recommendations));

        return recommendations;
    }

    public ProgressPrediction predictProgress(
            PatientProfile patientProfile,
            List<TherapyIntervention> currentTherapy) {

        return predictionService.predictProgress(patientProfile, currentTherapy);
    }

    public AdaptationRecommendations recommendAdaptations(
            PatientProfile patientProfile,
            List<TherapyIntervention> currentTherapy,
            ProgressData progressData) {

        return adaptationRecommender.recommendAdaptations(
            patientProfile, currentTherapy, progressData);
    }

    public void trackRecommendationEffectiveness(String sessionId, OutcomeData outcomeData) {
        RecommendationSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.trackOutcome(outcomeData);
            suggestionEngine.updateEffectiveness(session.getRecommendations(), outcomeData);
            cache.evictStaleEntries();
        }
    }

    public List<TherapyGoal> updateGoals(
            PatientProfile patientProfile,
            List<TherapyGoal> currentGoals,
            ProgressData progressData) {

        String prompt = buildGoalUpdatePrompt(patientProfile, currentGoals, progressData);
        String aiResponse = zaiService.chat(prompt);
        return parseUpdatedGoals(aiResponse);
    }

    private String generateCacheKey(
            PatientProfile patientProfile,
            List<TherapyGoal> therapyGoals,
            TherapySessionContext sessionContext) {

        String goalsStr = therapyGoals.stream()
            .map(g -> g.type())
            .collect(Collectors.joining(","));

        return String.format("%s-%s-%s-%d",
            patientProfile.patientId(),
            goalsStr,
            sessionContext.therapyArea(),
            sessionContext.sessionTimestamp());
    }

    private TherapyRecommendations createRecommendations(
            PatientProfile patientProfile,
            List<TherapyGoal> therapyGoals,
            TherapySessionContext sessionContext) {

        String prompt = buildRecommendationPrompt(patientProfile, therapyGoals, sessionContext);
        String aiResponse = zaiService.chat(prompt);

        List<InterventionSuggestion> suggestions = suggestionEngine.parseSuggestions(aiResponse);

        List<TherapyIntervention> interventions = suggestions.stream()
            .map(InterventionSuggestion::toTherapyIntervention)
            .collect(Collectors.toList());

        return new TherapyRecommendations(
            patientProfile.patientId(),
            therapyGoals,
            interventions,
            suggestions,
            Instant.now());
    }

    private String buildRecommendationPrompt(
            PatientProfile patientProfile,
            List<TherapyGoal> therapyGoals,
            TherapySessionContext sessionContext) {

        return String.format("""
            Patient Profile:
            - Age: %d
            - Gender: %s
            - Primary Condition: %s
            - Secondary Conditions: %s
            - Functional Level: %s
            - Mobility: %s
            - Cognitive Status: %s
            - Previous Therapy: %s

            Therapy Goals:
            %s

            Session Context:
            - Therapy Area: %s
            - Session Number: %d
            - Duration: %d minutes
            - Focus Areas: %s

            Please generate evidence-based therapy recommendations that address these goals.
            """,
            patientProfile.getAge(),
            patientProfile.gender().name(),
            patientProfile.primaryCondition(),
            String.join(", ", patientProfile.secondaryConditions()),
            patientProfile.functionalLevel().name(),
            patientProfile.mobilityLevel().name(),
            patientProfile.cognitiveStatus().name(),
            patientProfile.previousTherapy(),
            formatGoals(therapyGoals),
            sessionContext.therapyArea(),
            sessionContext.sessionNumber(),
            sessionContext.durationMinutes(),
            String.join(", ", sessionContext.focusAreas()));
    }

    private String buildGoalUpdatePrompt(
            PatientProfile patientProfile,
            List<TherapyGoal> currentGoals,
            ProgressData progressData) {

        return String.format("""
            Patient Profile:
            - Age: %d
            - Condition: %s

            Current Goals:
            %s

            Progress Data:
            - Sessions Completed: %d
            - Overall Progress: %.1f%%
            - Improvements: %s
            - Challenges: %s

            Based on this progress, suggest updates to the therapy goals.
            """,
            patientProfile.getAge(),
            patientProfile.primaryCondition(),
            formatGoals(currentGoals),
            progressData.sessionsCompleted(),
            progressData.overallProgress(),
            String.join(", ", progressData.improvements()),
            String.join(", ", progressData.challenges()));
    }

    private String formatGoals(List<TherapyGoal> goals) {
        return goals.stream()
            .map(goal -> String.format("  - %s: %s (Priority: %s)",
                goal.type(), goal.description(), goal.priority()))
            .collect(Collectors.joining("\n"));
    }

    private List<TherapyGoal> parseUpdatedGoals(String aiResponse) {
        List<TherapyGoal> updatedGoals = new ArrayList<>();

        if (aiResponse == null || aiResponse.isBlank()) {
            return updatedGoals;
        }

        String[] goalLines = aiResponse.split("\n");
        for (String line : goalLines) {
            if (line.contains("Goal:") || line.contains("- ")) {
                TherapyGoal goal = new TherapyGoal(
                    "Updated Goal",
                    line.trim(),
                    "MEDIUM");
                updatedGoals.add(goal);
            }
        }

        return updatedGoals;
    }

    public Map<String, RecommendationSession> getActiveSessions() {
        return new HashMap<>(activeSessions);
    }

    public void close() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
