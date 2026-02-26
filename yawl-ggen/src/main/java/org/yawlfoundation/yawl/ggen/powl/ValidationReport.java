/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.powl;

import java.util.List;
import java.util.Objects;

/**
 * A validation report indicating whether a POWL model is structurally sound.
 * Collects all detected violations as a list of human-readable messages.
 *
 * @param valid       true if the model is valid (no violations), false otherwise
 * @param violations  immutable list of violation messages (never null, empty if valid)
 */
public record ValidationReport(boolean valid, List<String> violations) {

    /**
     * Compact constructor making a defensive copy of the violations list.
     */
    public ValidationReport {
        Objects.requireNonNull(violations, "violations must not be null");
        violations = List.copyOf(violations);
    }

    /**
     * Factory method for a clean (no violations) validation report.
     *
     * @return a ValidationReport with valid=true and empty violations list
     */
    public static ValidationReport clean() {
        return new ValidationReport(true, List.of());
    }
}
