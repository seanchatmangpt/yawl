/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.yawlfoundation.yawl.graalpy.validation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of a validation operation
 * 
 * Contains validation status, errors, warnings, and metrics
 */
public class ValidationResult {
    
    private String name;
    private boolean passed;
    private final List<String> errors;
    private final List<String> warnings;
    private final Map<String, Long> metrics;
    private Instant timestamp;
    
    /**
     * Default constructor
     */
    public ValidationResult() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.metrics = new HashMap<>();
        this.timestamp = Instant.now();
    }
    
    /**
     * Constructor with name
     */
    public ValidationResult(String name) {
        this();
        this.name = name;
    }
    
    /**
     * Merges another ValidationResult into this one
     * @param other The ValidationResult to merge
     */
    public void merge(ValidationResult other) {
        if (other == null) {
            return;
        }
        
        // Errors are critical - if any validation fails, the overall result fails
        if (!other.isPassed()) {
            this.passed = false;
        }
        
        // Add all errors and warnings
        this.errors.addAll(other.getErrors());
        this.warnings.addAll(other.getWarnings());
        
        // Merge metrics (use the maximum values for conflicting metrics)
        for (Map.Entry<String, Long> metric : other.getMetrics().entrySet()) {
            String key = metric.getKey();
            Long value = metric.getValue();
            
            // Only add if metric doesn't exist or if it's a maximum value
            if (!this.metrics.containsKey(key) || value > this.metrics.get(key)) {
                this.metrics.put(key, value);
            }
        }
    }
    
    /**
     * Adds an error message
     * @param error The error message
     */
    public void addError(String error) {
        this.errors.add(error);
        this.passed = false;
    }
    
    /**
     * Adds a warning message
     * @param warning The warning message
     */
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
    
    /**
     * Adds a metric value
     * @param name The metric name
     * @param value The metric value
     */
    public void addMetric(String name, long value) {
        this.metrics.put(name, value);
    }
    
    /**
     * Adds a metric with unit
     * @param name The metric name
     * @param value The metric value
     * @param unit The unit of measurement
     */
    public void addMetric(String name, long value, String unit) {
        this.metrics.put(name, value);
        // Note: Unit information is stored separately in the PatternValidator
    }
    
    /**
     * Gets metric value by name
     * @param name The metric name
     * @return The metric value, or 0 if not found
     */
    public long getMetric(String name) {
        return metrics.getOrDefault(name, 0L);
    }
    
    // Getters and Setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public boolean isPassed() {
        return passed;
    }
    
    public void setPassed(boolean passed) {
        this.passed = passed;
    }
    
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
    
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }
    
    public Map<String, Long> getMetrics() {
        return new HashMap<>(metrics);
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * Returns true if there are no errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    /**
     * Returns true if there are warnings
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    /**
     * Returns true if this validation has metrics
     */
    public boolean hasMetrics() {
        return !metrics.isEmpty();
    }
    
    /**
     * Creates a summary of the validation result
     * @return String summary
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Validation: %s%n", name));
        summary.append(String.format("Status: %s%n", passed ? "PASS" : "FAIL"));
        summary.append(String.format("Timestamp: %s%n", timestamp));
        
        if (hasErrors()) {
            summary.append(String.format("Errors: %d%n", errors.size()));
            for (String error : errors) {
                summary.append(String.format("  - %s%n", error));
            }
        }
        
        if (hasWarnings()) {
            summary.append(String.format("Warnings: %d%n", warnings.size()));
            for (String warning : warnings) {
                summary.append(String.format("  - %s%n", warning));
            }
        }
        
        if (hasMetrics()) {
            summary.append("Metrics:\n");
            for (Map.Entry<String, Long> metric : metrics.entrySet()) {
                summary.append(String.format("  %s: %d%n", metric.getKey(), metric.getValue()));
            }
        }
        
        return summary.toString();
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
}
