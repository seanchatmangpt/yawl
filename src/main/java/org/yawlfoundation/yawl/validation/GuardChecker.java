/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.validation;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for validation checkers in the YAWL validation pipeline.
 *
 * Each checker implements specific validation rules and returns violations
 * in the standardized format.
 */
public interface GuardChecker {

    /**
     * Checks the given file for guard violations.
     *
     * @param path Path to the file to validate
     * @return List of guard violations found (empty if valid)
     */
    List<GuardViolation> check(Path path);

    /**
     * Gets the name of this validation pattern.
     *
     * @return Pattern name (e.g., "SHACL_VALIDATION", "H_TODO")
     */
    String patternName();

    /**
     * Gets the severity level for violations from this checker.
     *
     * @return Severity level ("FAIL", "WARN")
     */
    String severity();
}