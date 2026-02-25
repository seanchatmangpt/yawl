/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Therapy intervention representing a specific therapeutic activity.
 *
 * @param id intervention identifier
 * @param name intervention name
 * @param description detailed description
 * @param frequency recommended frequency
 * @param duration recommended duration
 * @param evidenceLevel evidence level (HIGH, MODERATE, LOW)
 */
public record TherapyIntervention(
    String id,
    String name,
    String description,
    String frequency,
    Duration duration,
    String evidenceLevel
) {
    public TherapyIntervention {
        Objects.requireNonNull(id, "ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        if (description == null) {
            description = "";
        }
        if (frequency == null) {
            frequency = "As needed";
        }
        if (evidenceLevel == null) {
            evidenceLevel = "MODERATE";
        }
    }
}
