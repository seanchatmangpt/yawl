/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.pi.bridge;

import java.time.Instant;
import java.util.List;

/**
 * Immutable record representing the results of event data validation.
 *
 * <p>Reports whether event data is valid according to a schema,
 * including any errors and warnings encountered during validation.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public record ValidationReport(
    boolean valid,
    List<String> errors,
    List<String> warnings,
    int rowsChecked,
    Instant validatedAt
) {

    /**
     * Create a successful validation report.
     *
     * @param rows number of data rows validated
     * @return validation report indicating success
     */
    public static ValidationReport success(int rows) {
        return new ValidationReport(true, List.of(), List.of(), rows, Instant.now());
    }

    /**
     * Create a failed validation report with errors.
     *
     * @param errors list of validation error messages
     * @return validation report indicating failure
     */
    public static ValidationReport failure(List<String> errors) {
        return new ValidationReport(false, errors, List.of(), 0, Instant.now());
    }

    /**
     * Create a validation report with warnings.
     *
     * @param rows number of data rows validated
     * @param warnings list of warning messages
     * @return validation report indicating success with warnings
     */
    public static ValidationReport successWithWarnings(int rows, List<String> warnings) {
        return new ValidationReport(true, List.of(), warnings, rows, Instant.now());
    }
}
