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

import java.time.Instant;

/**
 * Represents a single validation violation in the YAWL guard system.
 *
 * Captures all details needed to identify and fix a validation issue.
 */
public class GuardViolation {

    private String pattern;        // Pattern name (e.g., "SHACL_VALIDATION")
    private String severity;       // "FAIL" or "WARN"
    private String file;          // File path where violation occurred
    private int line;             // Line number (approximate)
    private String content;       // Exact code/content that violates
    private String fixGuidance;   // Instructions for fixing the violation
    private Instant timestamp;    // When the violation was detected

    public GuardViolation(String pattern, String severity, int line, String content) {
        this.pattern = pattern;
        this.severity = severity;
        this.line = line;
        this.content = content;
        this.timestamp = Instant.now();
        this.fixGuidance = generateDefaultFixGuidance(pattern);
    }

    public String getPattern() {
        return pattern;
    }

    public String getSeverity() {
        return severity;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
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

    public void setFixGuidance(String fixGuidance) {
        this.fixGuidance = fixGuidance;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Generates default fix guidance based on pattern type.
     */
    private String generateDefaultFixGuidance(String pattern) {
        return switch(pattern) {
            case "H_TODO" -> "Implement real logic or throw UnsupportedOperationException";
            case "H_MOCK" -> "Delete mock or implement real service";
            case "H_STUB" -> "Implement real method or throw exception";
            case "H_EMPTY" -> "Implement real logic or throw exception";
            case "H_FALLBACK" -> "Propagate exception instead of faking data";
            case "H_LIE" -> "Update code to match documentation";
            case "H_SILENT" -> "Throw exception instead of logging";
            case "SHACL_VALIDATION" -> "Fix the SHACL constraint violation. Refer to YAWL specification for details.";
            default -> "Fix the validation violation";
        };
    }
}