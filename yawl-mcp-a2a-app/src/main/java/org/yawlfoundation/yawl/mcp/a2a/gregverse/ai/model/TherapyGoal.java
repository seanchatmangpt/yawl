/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

import java.util.Objects;

/**
 * Therapy goal representing a patient's therapy objective.
 *
 * @param type goal type/category
 * @param description detailed description
 * @param priority priority level (HIGH, MEDIUM, LOW)
 */
public record TherapyGoal(
    String type,
    String description,
    String priority
) {
    public TherapyGoal {
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(description, "Description cannot be null");
        if (priority == null || priority.isBlank()) {
            priority = "MEDIUM";
        }
    }
}
