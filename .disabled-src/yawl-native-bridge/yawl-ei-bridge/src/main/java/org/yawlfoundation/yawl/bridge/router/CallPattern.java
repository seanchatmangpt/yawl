/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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
package org.yawlfoundation.yawl.bridge.router;

/**
 * Enumeration of supported call patterns for native bridge routing.
 * Defines the execution domain for each NativeCall triple.
 */
public enum CallPattern {
    /**
     * JVM execution domain: Runs in-process via QLeverEngine.
     * Sub-10ns latency, no fault isolation needed.
     */
    JVM("jvm"),

    /**
     * BEAM execution domain: Executes via Erlang interface bridge.
     * ~5-20µs latency over Unix domain socket transport.
     */
    BEAM("beam"),

    /**
     * Direct execution: Currently blocked for security reasons.
     * Throws UnsupportedOperationException to prevent accidental usage.
     */
    DIRECT("direct");

    private final String pattern;

    CallPattern(String pattern) {
        this.pattern = pattern;
    }

    /**
     * Returns the string representation of the call pattern.
     *
     * @return pattern string ("jvm", "beam", or "direct")
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * Parses a string into a CallPattern enum value.
     * Case-insensitive matching with validation.
     *
     * @param pattern string representation
     * @return corresponding CallPattern enum value
     * @throws IllegalArgumentException if pattern is not recognized
     */
    public static CallPattern fromString(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("CallPattern cannot be null");
        }

        return switch (pattern.toLowerCase()) {
            case "jvm" -> JVM;
            case "beam" -> BEAM;
            case "direct" -> DIRECT;
            default -> throw new IllegalArgumentException(
                "Unsupported call pattern: '" + pattern +
                "'. Supported patterns: jvm, beam, direct"
            );
        };
    }

    /**
     * Validates that this pattern is executable (not blocked).
     *
     * @return true if pattern is executable, false if blocked
     */
    public boolean isExecutable() {
        return this != DIRECT;
    }

    /**
     * Gets a descriptive explanation of the execution domain.
     *
     * @return human-readable description of the pattern
     */
    public String getDescription() {
        return switch (this) {
            case JVM -> "JVM execution domain (in-process QLeverEngine)";
            case BEAM -> "BEAM execution domain (Erlang interface bridge)";
            case DIRECT -> "Direct execution domain (currently blocked)";
        };
    }
}