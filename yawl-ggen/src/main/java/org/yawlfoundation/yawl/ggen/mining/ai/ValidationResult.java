/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.mining.ai;

import java.util.List;

/**
 * The result of one Ollama validation pass on generated YAWL XML.
 *
 * <p>Immutable record produced by {@link OllamaValidationClient#validate(String, int)}.
 * When {@code valid} is {@code true}, the issues list is empty.
 * When {@code valid} is {@code false}, the issues list contains at least one entry.
 *
 * @param valid       true if the model assessed the spec as structurally sound
 *                    with no H-Guard violations
 * @param issues      specific issues identified (empty when valid is true);
 *                    each entry corresponds to one "ISSUE:" line in the model response
 * @param rawResponse the full raw model response for audit purposes
 * @param iteration   which validation iteration produced this result (1-indexed)
 */
public record ValidationResult(
        boolean valid,
        List<String> issues,
        String rawResponse,
        int iteration) {

    /**
     * Compact constructor: defensive copy of issues list and argument validation.
     */
    public ValidationResult {
        if (rawResponse == null) {
            throw new IllegalArgumentException("rawResponse must not be null");
        }
        if (iteration < 1) {
            throw new IllegalArgumentException("iteration must be >= 1, got: " + iteration);
        }
        issues = List.copyOf(issues != null ? issues : List.of());
    }
}
