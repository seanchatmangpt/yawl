/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

/**
 * Mobility level enumeration for patient assessments.
 */
public enum MobilityLevel {
    FULLY_MOBILE("No mobility limitations"),
    MILD_LIMITATION("Mild mobility limitations"),
    MODERATE_LIMITATION("Moderate mobility limitations"),
    SEVERE_LIMITATION("Severe mobility limitations"),
    NON_AMBULATORY("Non-ambulatory");

    private final String description;

    MobilityLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
