/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.dspy.worklets;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable context for DSPy worklet selection.
 *
 * <p>Captures all information needed to make a worklet selection decision:
 * <ul>
 *   <li><strong>taskName</strong> — Workflow task identifier (e.g., "ApproveRequest")</li>
 *   <li><strong>caseData</strong> — Case attributes relevant to selection (e.g., amount, priority)</li>
 *   <li><strong>availableWorklets</strong> — Candidate worklet IDs to choose from</li>
 *   <li><strong>historicalSelections</strong> — Count of past selections per worklet (training signal)</li>
 * </ul>
 *
 * <p>This record is marshalled to Python for DSPy classification and returned with a
 * {@link WorkletSelection} result.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record WorkletSelectionContext(
    String taskName,
    Map<String, Object> caseData,
    List<String> availableWorklets,
    Map<String, Integer> historicalSelections
) {

    /**
     * Constructs a WorkletSelectionContext with validation.
     *
     * @param taskName the workflow task name; must not be null or blank
     * @param caseData case attributes; must not be null
     * @param availableWorklets candidate worklets; must not be null
     * @param historicalSelections worklet selection counts; must not be null
     * @throws IllegalArgumentException if any required field is null or invalid
     */
    public WorkletSelectionContext {
        Objects.requireNonNull(taskName, "taskName must not be null");
        Objects.requireNonNull(caseData, "caseData must not be null");
        Objects.requireNonNull(availableWorklets, "availableWorklets must not be null");
        Objects.requireNonNull(historicalSelections, "historicalSelections must not be null");

        if (taskName.isBlank()) {
            throw new IllegalArgumentException("taskName must not be blank");
        }
        if (availableWorklets.isEmpty()) {
            throw new IllegalArgumentException("availableWorklets must not be empty");
        }
    }
}
