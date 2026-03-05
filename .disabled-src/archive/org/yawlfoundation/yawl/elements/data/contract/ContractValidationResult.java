/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.elements.data.contract;

import java.util.*;

/**
 * Result of validating a workflow's variables/parameters against an ODCS data contract.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ContractValidationResult {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /**
     * Adds an error to this result.
     * @param error error message
     */
    public void addError(String error) {
        Objects.requireNonNull(error, "error required");
        errors.add(error);
    }

    /**
     * Adds a warning to this result.
     * @param warning warning message
     */
    public void addWarning(String warning) {
        Objects.requireNonNull(warning, "warning required");
        warnings.add(warning);
    }

    /**
     * Checks if validation passed (no errors).
     * @return true if no errors, false otherwise
     */
    public boolean isPassed() {
        return errors.isEmpty();
    }

    /**
     * Gets all errors.
     * @return immutable list of error messages
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Gets all warnings.
     * @return immutable list of warning messages
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * Gets error count.
     * @return number of errors
     */
    public int getErrorCount() {
        return errors.size();
    }

    /**
     * Gets warning count.
     * @return number of warnings
     */
    public int getWarningCount() {
        return warnings.size();
    }

    /**
     * Gets a summary message.
     * @return summary (e.g., "PASSED" or "FAILED: 2 errors, 1 warning")
     */
    public String getSummary() {
        if (isPassed()) {
            return "PASSED";
        }
        StringBuilder sb = new StringBuilder("FAILED: ");
        if (!errors.isEmpty()) {
            sb.append(errors.size()).append(" error").append(errors.size() > 1 ? "s" : "");
        }
        if (!warnings.isEmpty()) {
            if (!errors.isEmpty()) sb.append(", ");
            sb.append(warnings.size()).append(" warning").append(warnings.size() > 1 ? "s" : "");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getSummary()).append("\n");
        for (String error : errors) {
            sb.append("  ERROR: ").append(error).append("\n");
        }
        for (String warning : warnings) {
            sb.append("  WARNING: ").append(warning).append("\n");
        }
        return sb.toString();
    }
}
