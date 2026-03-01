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

package org.yawlfoundation.yawl.schema;

/**
 * A single field-level schema contract violation.
 *
 * <p>Violations are collected during {@link SchemaContractValidator#validate} and
 * aggregated into a {@link TaskSchemaViolationException} if any are found.</p>
 *
 * @param field    the field name as declared in the ODCS contract (for MISSING_FIELD)
 *                 or as found in the task data (for UNEXPECTED_FIELD)
 * @param expected a human-readable description of what was expected
 *                 (e.g. {@code "present"} for a required field)
 * @param actual   a human-readable description of what was found
 *                 (e.g. {@code "missing"} when the field is absent)
 * @param type     the violation classification
 * @since 6.0.0
 */
public record SchemaViolation(
        String field,
        String expected,
        String actual,
        ViolationType type
) {

    /**
     * Convenience factory for a missing required field.
     *
     * @param fieldName the field declared in the contract but absent from actual data
     */
    public static SchemaViolation missingField(String fieldName) {
        return new SchemaViolation(fieldName, "present", "missing", ViolationType.MISSING_FIELD);
    }

    /**
     * Convenience factory for an unexpected field.
     *
     * @param fieldName the field found in actual data but not in the contract
     */
    public static SchemaViolation unexpectedField(String fieldName) {
        return new SchemaViolation(fieldName, "absent", "present", ViolationType.UNEXPECTED_FIELD);
    }

    /**
     * Human-readable single-line description for use in exception messages.
     *
     * @return formatted violation description
     */
    public String describe() {
        return type + ": '" + field + "' (expected: " + expected + ", actual: " + actual + ")";
    }
}
