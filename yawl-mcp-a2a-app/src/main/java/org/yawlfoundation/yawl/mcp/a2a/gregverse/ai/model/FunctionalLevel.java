/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

/**
 * Functional ability level for patient assessments.
 */
public enum FunctionalLevel {
    INDEPENDENT("Requires no assistance"),
    MINIMAL_ASSISTANCE("Requires minimal assistance"),
    MODERATE_ASSISTANCE("Requires moderate assistance"),
    MAXIMAL_ASSISTANCE("Requires maximal assistance"),
    DEPENDENT("Fully dependent");

    private final String description;

    FunctionalLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
