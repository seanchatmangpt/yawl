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
 * Thrown by {@link AiValidationLoop} when the maximum iteration count is exhausted
 * without the Ollama model approving the generated YAWL specification.
 *
 * <p>Q invariant: never silently return a spec that failed validation.
 * If max iterations is reached and validation still fails, this exception is thrown
 * so the caller can take corrective action rather than receive invalid output.
 */
public class ValidationExhaustedException extends RuntimeException {

    private final int iterationsAttempted;
    private final List<String> lastIssues;

    /**
     * Constructs the exception with the number of iterations attempted and
     * the issues found in the final failing iteration.
     *
     * @param iterationsAttempted number of generate+validate cycles that were run
     * @param lastIssues          the validation issues found in the last iteration;
     *                            may be empty but must not be null
     */
    public ValidationExhaustedException(int iterationsAttempted, List<String> lastIssues) {
        super("AI validation exhausted after %d iteration(s). Last issues: %s"
                .formatted(iterationsAttempted, lastIssues));
        this.iterationsAttempted = iterationsAttempted;
        this.lastIssues = List.copyOf(lastIssues != null ? lastIssues : List.of());
    }

    /**
     * Returns the number of generate+validate iterations that were attempted.
     */
    public int getIterationsAttempted() {
        return iterationsAttempted;
    }

    /**
     * Returns the validation issues found in the last iteration (unmodifiable).
     */
    public List<String> getLastIssues() {
        return lastIssues;
    }
}
