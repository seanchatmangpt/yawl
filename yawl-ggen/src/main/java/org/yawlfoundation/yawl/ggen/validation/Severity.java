/*
 * Copyright 2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yawlfoundation.yawl.ggen.validation;

/**
 * Defines severity levels for guard violations.
 *
 * <p>Severity levels determine the impact of guard violations on code generation:
 * <ul>
 *   <li>{@link #FAIL} - Violation blocks code generation and must be fixed</li>
 *   <li>{@link #WARN} - Violation generates warning but allows code generation</li>
 * </ul>
 *
 * <p>This enum is designed to be used with functional interfaces and pattern matching
 * in Java 25+ contexts.
 *
 * @since 1.0
 */
public enum Severity {

    /**
     * Critical violation that blocks code generation.
     *
     * <p>FAIL violations indicate the presence of forbidden patterns (TODO, mock, stub, etc.)
     * that violate YAWL's production standards. Code generation will halt until these
     * violations are resolved by either implementing real logic or throwing
     * UnsupportedOperationException.
     */
    FAIL,

    /**
     * Non-critical violation that allows code generation with warning.
     *
     * <p>WARN violations indicate patterns that should be addressed but don't block
     * code generation. These are typically used for less critical violations where
     * immediate blocking is not warranted but awareness is important.
     */
    WARN;

    /**
     * Returns whether this severity level blocks code generation.
     *
     * @return true if FAIL severity, false otherwise
     */
    public boolean isBlocking() {
        return this == FAIL;
    }

    /**
     * Returns the severity level from its string representation.
     *
     * @param severityStr the string representation of severity
     * @return the corresponding Severity enum value
     * @throws IllegalArgumentException if severityStr is not "FAIL" or "WARN"
     */
    public static Severity fromString(String severityStr) {
        return switch (severityStr.toUpperCase()) {
            case "FAIL" -> FAIL;
            case "WARN" -> WARN;
            default -> throw new IllegalArgumentException(
                "Invalid severity: " + severityStr + ". Expected FAIL or WARN"
            );
        };
    }
}