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
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.compliance.shacl;

import java.util.Map;

/**
 * Represents a single SHACL validation violation.
 *
 * <p>This record captures detailed information about a compliance violation
 * found during SHACL validation. It includes the severity, focus node,
 * constraint, and detailed information about the violation.</p>
 *
 * @param severity The severity level of the violation
 * @param focusNode The node in the YAWL specification that caused the violation
 * @param constraint The SHACL constraint that was violated
 * @param message Human-readable description of the violation
 * @param path Path within the YAWL specification where the violation occurred
 * @param value The actual value that violated the constraint
 * @param expectedValue The expected value according to the constraint
 * @param details Additional details about the violation
 */
public record ShaclViolation(
    ViolationSeverity severity,
    String focusNode,
    String constraint,
    String message,
    String path,
    String value,
    String expectedValue,
    Map<String, Object> details
) {

    /**
     * Creates a new SHACL violation with high severity.
     */
    public static ShaclViolation high(
        String focusNode,
        String constraint,
        String message,
        String path
    ) {
        return new ShaclViolation(
            ViolationSeverity.HIGH,
            focusNode,
            constraint,
            message,
            path,
            null,
            null,
            Map.of()
        );
    }

    /**
     * Creates a new SHACL violation with medium severity.
     */
    public static ShaclViolation medium(
        String focusNode,
        String constraint,
        String message,
        String path
    ) {
        return new ShaclViolation(
            ViolationSeverity.MEDIUM,
            focusNode,
            constraint,
            message,
            path,
            null,
            null,
            Map.of()
        );
    }

    /**
     * Creates a new SHACL violation with low severity.
     */
    public static ShaclViolation low(
        String focusNode,
        String constraint,
        String message,
        String path
    ) {
        return new ShaclViolation(
            ViolationSeverity.LOW,
            focusNode,
            constraint,
            message,
            path,
            null,
            null,
            Map.of()
        );
    }

    /**
     * Creates a new SHACL violation with value details.
     */
    public static ShaclViolation withValue(
        ViolationSeverity severity,
        String focusNode,
        String constraint,
        String message,
        String path,
        String value,
        String expectedValue
    ) {
        return new ShaclViolation(
            severity,
            focusNode,
            constraint,
            message,
            path,
            value,
            expectedValue,
            Map.of()
        );
    }

    /**
     * Creates a new SHACL violation with additional details.
     */
    public static ShaclViolation withDetails(
        ViolationSeverity severity,
        String focusNode,
        String constraint,
        String message,
        String path,
        Map<String, Object> details
    ) {
        return new ShaclViolation(
            severity,
            focusNode,
            constraint,
            message,
            path,
            null,
            null,
            Map.copyOf(details)
        );
    }

    /**
     * Converts to a human-readable string.
     */
    @Override
    public String toString() {
        return String.format(
            "[%s] %s - %s (at %s)",
            severity,
            focusNode,
            message,
            path
        );
    }
}