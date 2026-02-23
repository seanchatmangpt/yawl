/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

/**
 * Cognitive status enumeration for patient assessments.
 */
public enum CognitiveStatus {
    INTACT("Cognitively intact"),
    MILD_IMPAIRMENT("Mild cognitive impairment"),
    MODERATE_IMPAIRMENT("Moderate cognitive impairment"),
    SEVERE_IMPAIRMENT("Severe cognitive impairment");

    private final String description;

    CognitiveStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
