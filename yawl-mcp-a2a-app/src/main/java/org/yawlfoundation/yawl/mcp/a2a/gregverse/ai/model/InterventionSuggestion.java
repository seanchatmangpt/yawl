/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

import java.time.Duration;
import java.util.UUID;

/**
 * Intervention suggestion from AI recommendations.
 *
 * @param title suggestion title
 * @param description detailed description
 * @param rationale rationale for this intervention
 * @param expectedOutcome expected outcome
 */
public record InterventionSuggestion(
    String title,
    String description,
    String rationale,
    String expectedOutcome
) {
    public TherapyIntervention toTherapyIntervention() {
        return new TherapyIntervention(
            UUID.randomUUID().toString(),
            title,
            description,
            "3x per week",
            Duration.ofMinutes(30),
            "MODERATE"
        );
    }
}
