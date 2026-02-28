/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.validation.model;

import java.util.Objects;

/**
 * Represents a single guard violation detected during code validation.
 * Contains pattern name, severity, file location, violating code, and fix guidance.
 */
public class GuardViolation {
    private final String pattern;      // H_TODO, H_MOCK, H_STUB, etc.
    private final String severity;     // FAIL or WARN
    private final int line;
    private final String content;      // Exact code that violates
    private final String fixGuidance;
    private String file;               // Set during validation

    /**
     * Create a new guard violation.
     *
     * @param pattern the guard pattern name (e.g., H_TODO)
     * @param severity the severity level (FAIL or WARN)
     * @param line the line number where violation occurs (1-indexed)
     * @param content the exact code content that violates the guard
     */
    public GuardViolation(String pattern, String severity, int line, String content) {
        this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.line = line;
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.fixGuidance = getFixGuidanceFor(pattern);
    }

    /**
     * Determine fix guidance based on the violation pattern.
     */
    private static String getFixGuidanceFor(String pattern) {
        return switch (pattern) {
            case "H_TODO" ->
                "Implement real logic or throw UnsupportedOperationException";
            case "H_MOCK" ->
                "Delete mock class or implement real service";
            case "H_STUB" ->
                "Implement real method logic or throw exception";
            case "H_EMPTY" ->
                "Implement real logic or throw exception";
            case "H_FALLBACK" ->
                "Propagate exception instead of returning fake data";
            case "H_LIE" ->
                "Update code to match documentation or vice versa";
            case "H_SILENT" ->
                "Throw exception instead of logging";
            default ->
                "Fix guard violation according to pattern definition";
        };
    }

    // Getters

    public String getPattern() {
        return pattern;
    }

    public String getSeverity() {
        return severity;
    }

    public int getLine() {
        return line;
    }

    public String getContent() {
        return content;
    }

    public String getFixGuidance() {
        return fixGuidance;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuardViolation that = (GuardViolation) o;
        return line == that.line &&
               Objects.equals(pattern, that.pattern) &&
               Objects.equals(file, that.file) &&
               Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, file, line, content);
    }

    @Override
    public String toString() {
        return String.format(
            "GuardViolation{pattern=%s, severity=%s, file=%s, line=%d, content=%s}",
            pattern, severity, file, line, content
        );
    }
}
