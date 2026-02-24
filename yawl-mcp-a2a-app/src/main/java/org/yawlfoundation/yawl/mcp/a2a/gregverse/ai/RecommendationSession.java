/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai;

import org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Session tracking for therapy recommendations.
 */
public class RecommendationSession {

    private final TherapySessionContext context;
    private final TherapyRecommendations recommendations;
    private final List<OutcomeData> outcomes;
    private Instant lastUpdated;

    public RecommendationSession(
            TherapySessionContext context,
            TherapyRecommendations recommendations) {
        this.context = Objects.requireNonNull(context);
        this.recommendations = Objects.requireNonNull(recommendations);
        this.outcomes = new ArrayList<>();
        this.lastUpdated = Instant.now();
    }

    public TherapySessionContext getContext() {
        return context;
    }

    public TherapyRecommendations getRecommendations() {
        return recommendations;
    }

    public List<OutcomeData> getOutcomes() {
        return new ArrayList<>(outcomes);
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void trackOutcome(OutcomeData outcome) {
        Objects.requireNonNull(outcome, "Outcome cannot be null");
        outcomes.add(outcome);
        lastUpdated = Instant.now();
    }

    public boolean hasOutcomes() {
        return !outcomes.isEmpty();
    }

    public double getAverageSatisfaction() {
        if (outcomes.isEmpty()) {
            return 0.0;
        }
        return outcomes.stream()
            .mapToInt(OutcomeData::patientSatisfaction)
            .average()
            .orElse(0.0);
    }
}
