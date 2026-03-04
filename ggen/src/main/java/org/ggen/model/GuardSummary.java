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

package org.ggen.model;

import java.util.List;

/**
 * Summary of guard violations by pattern type.
 *
 * <p>This class provides statistics about guard violations found during
 * validation, categorized by the specific guard pattern type (H_TODO,
 * H_MOCK, H_STUB, etc.) and includes totals.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * GuardSummary summary = new GuardSummary(violations);
 * int todoCount = summary.getH_todo_count();
 * int totalCount = summary.getTotal_violations();
 * }</pre>
 *
 * @since 1.0
 */
public final class GuardSummary {

    private int h_todo_count;
    private int h_mock_count;
    private int h_stub_count;
    private int h_empty_count;
    private int h_fallback_count;
    private int h_lie_count;
    private int h_silent_count;
    private int total_violations;

    /**
     * Creates a new GuardSummary by analyzing a list of violations.
     *
     * @param violations list of violations to analyze (must not be null)
     * @throws NullPointerException if violations is null
     */
    public GuardSummary(List<GuardViolation> violations) {
        if (violations == null) {
            throw new NullPointerException("Violations list cannot be null");
        }

        // Initialize all counts to zero
        this.h_todo_count = 0;
        this.h_mock_count = 0;
        this.h_stub_count = 0;
        this.h_empty_count = 0;
        this.h_fallback_count = 0;
        this.h_lie_count = 0;
        this.h_silent_count = 0;
        this.total_violations = 0;

        // Count violations by pattern
        for (GuardViolation violation : violations) {
            switch (violation.getPattern()) {
                case "H_TODO":
                    this.h_todo_count++;
                    break;
                case "H_MOCK":
                    this.h_mock_count++;
                    break;
                case "H_STUB":
                    this.h_stub_count++;
                    break;
                case "H_EMPTY":
                    this.h_empty_count++;
                    break;
                case "H_FALLBACK":
                    this.h_fallback_count++;
                    break;
                case "H_LIE":
                    this.h_lie_count++;
                    break;
                case "H_SILENT":
                    this.h_silent_count++;
                    break;
            }
        }

        // Calculate total
        this.total_violations = calculateTotalViolations();
    }

    /**
     * Calculates the total number of violations.
     *
     * @return sum of all individual pattern counts
     */
    private int calculateTotalViolations() {
        return h_todo_count + h_mock_count + h_stub_count +
               h_empty_count + h_fallback_count + h_lie_count +
               h_silent_count;
    }

    // Getters

    public int getH_todo_count() {
        return h_todo_count;
    }

    public int getH_mock_count() {
        return h_mock_count;
    }

    public int getH_stub_count() {
        return h_stub_count;
    }

    public int getH_empty_count() {
        return h_empty_count;
    }

    public int getH_fallback_count() {
        return h_fallback_count;
    }

    public int getH_lie_count() {
        return h_lie_count;
    }

    public int getH_silent_count() {
        return h_silent_count;
    }

    public int getTotal_violations() {
        return total_violations;
    }

    /**
     * Returns the total number of violations.
     *
     * @return total violation count
     */
    public int getTotalViolations() {
        return total_violations;
    }

    // Setters (if needed for deserialization or updates)

    public void setH_todo_count(int h_todo_count) {
        this.h_todo_count = h_todo_count;
        this.total_violations = calculateTotalViolations();
    }

    public void setH_mock_count(int h_mock_count) {
        this.h_mock_count = h_mock_count;
        this.total_violations = calculateTotalViolations();
    }

    public void setH_stub_count(int h_stub_count) {
        this.h_stub_count = h_stub_count;
        this.total_violations = calculateTotalViolations();
    }

    public void setH_empty_count(int h_empty_count) {
        this.h_empty_count = h_empty_count;
        this.total_violations = calculateTotalViolations();
    }

    public void setH_fallback_count(int h_fallback_count) {
        this.h_fallback_count = h_fallback_count;
        this.total_violations = calculateTotalViolations();
    }

    public void setH_lie_count(int h_lie_count) {
        this.h_lie_count = h_lie_count;
        this.total_violations = calculateTotalViolations();
    }

    public void setH_silent_count(int h_silent_count) {
        this.h_silent_count = h_silent_count;
        this.total_violations = calculateTotalViolations();
    }

    /**
     * Determines if there are any violations of any type.
     *
     * @return true if total violations > 0, false otherwise
     */
    public boolean hasViolations() {
        return total_violations > 0;
    }

    /**
     * Determines if there are violations of a specific pattern type.
     *
     * @param pattern the pattern name to check (e.g., "H_TODO")
     * @return true if violations of this type exist, false otherwise
     * @throws IllegalArgumentException if pattern is not recognized
     */
    public boolean hasViolationsOfType(String pattern) {
        return switch (pattern) {
            case "H_TODO" -> h_todo_count > 0;
            case "H_MOCK" -> h_mock_count > 0;
            case "H_STUB" -> h_stub_count > 0;
            case "H_EMPTY" -> h_empty_count > 0;
            case "H_FALLBACK" -> h_fallback_count > 0;
            case "H_LIE" -> h_lie_count > 0;
            case "H_SILENT" -> h_silent_count > 0;
            default -> throw new IllegalArgumentException("Unknown pattern: " + pattern);
        };
    }

    /**
     * Returns a formatted summary string for logging.
     *
     * @return formatted summary of violations by type
     */
    public String toSummaryString() {
        return String.format("Guard Summary: %d total violations (TODO: %d, MOCK: %d, STUB: %d, EMPTY: %d, FALLBACK: %d, LIE: %d, SILENT: %d)",
                total_violations, h_todo_count, h_mock_count, h_stub_count,
                h_empty_count, h_fallback_count, h_lie_count, h_silent_count);
    }
}