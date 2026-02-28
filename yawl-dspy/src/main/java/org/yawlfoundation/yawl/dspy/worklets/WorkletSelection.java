/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.dspy.worklets;

import java.util.Objects;

/**
 * Immutable result of DSPy worklet selection.
 *
 * <p>Represents the output of the {@link DspyWorkletSelector}, including:
 * <ul>
 *   <li><strong>selectedWorkletId</strong> — The chosen worklet ID</li>
 *   <li><strong>confidence</strong> — DSPy model confidence (0.0 to 1.0)</li>
 *   <li><strong>rationale</strong> — Human-readable explanation of the selection</li>
 * </ul>
 *
 * <p>If confidence falls below the threshold (0.7), the selector will fall back to the
 * RDR evaluator instead of using this selection.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record WorkletSelection(
    String selectedWorkletId,
    double confidence,
    String rationale
) {

    /**
     * Constructs a WorkletSelection with validation.
     *
     * @param selectedWorkletId the chosen worklet ID; must not be null or blank
     * @param confidence the model confidence; must be between 0.0 and 1.0 (inclusive)
     * @param rationale the explanation; must not be null or blank
     * @throws IllegalArgumentException if any field is invalid
     */
    public WorkletSelection {
        Objects.requireNonNull(selectedWorkletId, "selectedWorkletId must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");

        if (selectedWorkletId.isBlank()) {
            throw new IllegalArgumentException("selectedWorkletId must not be blank");
        }
        if (rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                "confidence must be between 0.0 and 1.0, got: " + confidence);
        }
    }
}
