/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.validation;

import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Pluggable interface for guard pattern detection.
 * Implementations detect specific guard violations (H_TODO, H_MOCK, etc.)
 * and return a list of violations found in a Java source file.
 */
public interface GuardChecker {

    /**
     * Severity level for guard violations.
     */
    enum Severity {
        WARN,  // Non-blocking warning
        FAIL   // Blocking failure
    }

    /**
     * Check a Java source file for guard violations.
     *
     * @param javaSource the path to the Java source file
     * @return a list of GuardViolation objects (empty if no violations found)
     * @throws IOException if the file cannot be read
     */
    List<GuardViolation> check(Path javaSource) throws IOException;

    /**
     * Get this checker's guard pattern name.
     *
     * @return pattern name (e.g., H_TODO, H_MOCK)
     */
    String patternName();

    /**
     * Get this checker's severity level.
     *
     * @return the Severity (WARN or FAIL)
     */
    Severity severity();
}
