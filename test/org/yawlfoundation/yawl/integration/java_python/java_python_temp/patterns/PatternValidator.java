/*
 * Copyright (c) 2024-2025 YAWL Foundation
 *
 * This file is part of YAWL v6.0.0-GA.
 *
 * YAWL v6.0.0-GA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL v6.0.0-GA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL v6.0.0-GA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.integration.java_python.patterns;

import org.yawlfoundation.yawl.integration.java_python.ValidationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Core pattern validator for YAWL workflow patterns.
 * Validates soundness, correctness, and performance of all YAWL patterns.
 *
 * @author YAWL Foundation
 * @since v6.0.0-GA
 */
public abstract class PatternValidator extends ValidationTestBase {

    protected Map<String, PatternResult> validationResults = new HashMap<>();
    protected int timeoutSeconds = 30;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(graalpyAvailable, "GraalPy required for pattern validation");
    }

    /**
     * Validate pattern soundness (deadlock-free, proper termination)
     */
    public abstract PatternResult validateSoundness(String patternName, String pythonCode) throws Exception;

    /**
     * Validate pattern correctness against expected behavior
     */
    public abstract PatternResult validateCorrectness(String patternName, String pythonCode) throws Exception;

    /**
     * Validate pattern performance characteristics
     */
    public abstract PatternResult validatePerformance(String patternName, String pythonCode) throws Exception;

    /**
     * Validate pattern error handling and recovery
     */
    public abstract PatternResult validateErrorHandling(String patternName, String pythonCode) throws Exception;

    /**
     * Comprehensive pattern validation
     */
    public PatternResult validatePattern(String patternName, String pythonCode) {
        PatternResult result = new PatternResult(patternName);

        try {
            // Validate soundness
            PatternResult soundnessResult = validateSoundness(patternName, pythonCode);
            result.addValidation("soundness", soundnessResult);

            // Validate correctness
            PatternResult correctnessResult = validateCorrectness(patternName, pythonCode);
            result.addValidation("correctness", correctnessResult);

            // Validate performance
            PatternResult performanceResult = validatePerformance(patternName, pythonCode);
            result.addValidation("performance", performanceResult);

            // Validate error handling
            PatternResult errorHandlingResult = validateErrorHandling(patternName, pythonCode);
            result.addValidation("error_handling", errorHandlingResult);

            // Determine overall status
            if (soundnessResult.isPassed() && correctnessResult.isPassed() &&
                performanceResult.isPassed() && errorHandlingResult.isPassed()) {
                result.setPassed(true);
                result.setSummary("Pattern validation passed");
            } else {
                result.setPassed(false);
                result.setSummary("Pattern validation failed in one or more aspects");
            }

        } catch (Exception e) {
            result.setPassed(false);
            result.addError("Validation failed: " + e.getMessage());
            result.setSummary("Pattern validation failed due to exception");
        }

        validationResults.put(patternName, result);
        return result;
    }

    /**
     * Execute pattern with timeout
     */
    protected Object executePatternWithTimeout(String patternCode, long timeoutMs) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Object> future = executor.submit(() -> executePythonCode(patternCode));

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new PatternTimeoutException("Pattern execution timed out after " + timeoutMs + "ms");
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Check for potential deadlocks in the pattern
     */
    protected boolean checkForDeadlocks(String patternCode) throws Exception {
        try {
            // Execute pattern multiple times to detect deadlocks
            for (int i = 0; i < 10; i++) {
                executePatternWithTimeout(patternCode, 5000); // 5 second timeout
            }
            return false; // No deadlocks detected
        } catch (PatternTimeoutException e) {
            return true; // Timeout indicates potential deadlock
        }
    }

    /**
     * Check for proper termination
     */
    protected boolean checkTermination(String patternCode) throws Exception {
        try {
            executePatternWithTimeout(patternCode, timeoutSeconds * 1000);
            return true;
        } catch (PatternTimeoutException e) {
            return false; // Pattern did not terminate
        }
    }

    /**
     * Measure pattern execution performance
     */
    protected PatternMetrics measurePerformance(String patternCode, int iterations) throws Exception {
        long totalExecutionTime = 0;
        int successfulExecutions = 0;
        List<Long> executionTimes = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            try {
                long startTime = System.nanoTime();
                executePatternWithTimeout(patternCode, timeoutSeconds * 1000);
                long endTime = System.nanoTime();
                long duration = TimeUnit.MILLISECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS);

                totalExecutionTime += duration;
                executionTimes.add(duration);
                successfulExecutions++;
            } catch (Exception e) {
                // Continue with next iteration
            }
        }

        if (successfulExecutions == 0) {
            throw new PatternValidationException("Pattern failed to execute successfully");
        }

        double averageTime = (double) totalExecutionTime / successfulExecutions;
        double p95Time = calculateP95(executionTimes);
        double throughput = (double) successfulExecutions / (totalExecutionTime / 1000.0);

        return new PatternMetrics(
            averageTime,
            p95Time,
            throughput,
            successfulExecutions,
            iterations - successfulExecutions
        );
    }

    /**
     * Check if pattern behaves correctly under error conditions
     */
    protected ErrorHandlingResult checkErrorHandling(String patternCode, List<ErrorScenario> scenarios) throws Exception {
        Map<String, Boolean> scenarioResults = new HashMap<>();
        List<String> errorMessages = new ArrayList<>();

        for (ErrorScenario scenario : scenarios) {
            try {
                executePythonCode(scenario.setupCode());
                executePatternWithTimeout(patternCode, 5000);
                scenarioResults.put(scenario.name(), true);
            } catch (Exception e) {
                scenarioResults.put(scenario.name(), false);
                errorMessages.add(scenario.name() + ": " + e.getMessage());
            }
        }

        return new ErrorHandlingResult(scenarioResults, errorMessages);
    }

    private double calculateP95(List<Long> values) {
        if (values.isEmpty()) return 0;

        Collections.sort(values);
        int index = (int) (values.size() * 0.95);
        return values.get(index);
    }

    /**
     * Get overall validation summary
     */
    public ValidationSummary getValidationSummary() {
        ValidationSummary summary = new ValidationSummary();

        for (PatternResult result : validationResults.values()) {
            if (result.isPassed()) {
                summary.passedPatterns++;
            } else {
                summary.failedPatterns++;
            }
            summary.totalPatterns++;

            // Add performance metrics
            if (result.getPerformanceMetrics() != null) {
                summary.totalExecutionTime += result.getPerformanceMetrics().averageTime();
                summary.totalThroughput += result.getPerformanceMetrics().throughput();
            }
        }

        summary.successRate = (double) summary.passedPatterns / summary.totalPatterns * 100;
        summary.averageExecutionTime = summary.totalExecutionTime / summary.totalPatterns;
        summary.averageThroughput = summary.totalThroughput / summary.totalPatterns;

        return summary;
    }

    /**
     * Exception for pattern timeouts
     */
    public static class PatternTimeoutException extends Exception {
        public PatternTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * Exception for pattern validation failures
     */
    public static class PatternValidationException extends Exception {
        public PatternValidationException(String message) {
            super(message);
        }
    }

    /**
     * Data classes for validation results
     */
    public static class PatternResult {
        private final String patternName;
        private final Map<String, Object> validations = new HashMap<>();
        private boolean passed;
        private String summary;
        private List<String> errors = new ArrayList<>();

        public PatternResult(String patternName) {
            this.patternName = patternName;
            this.passed = false;
        }

        public void addValidation(String type, PatternResult result) {
            validations.put(type, result);
        }

        public void addError(String error) {
            errors.add(error);
        }

        // Getters and setters
        public String getPatternName() { return patternName; }
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public List<String> getErrors() { return errors; }
        public Map<String, Object> getValidations() { return validations; }
        public PatternMetrics getPerformanceMetrics() {
            Object perf = validations.get("performance");
            return perf instanceof PatternMetrics ? (PatternMetrics) perf : null;
        }
    }

    public static class PatternMetrics {
        private final double averageTime;
        private final double p95Time;
        private final double throughput;
        private final int successfulExecutions;
        private final int failedExecutions;

        public PatternMetrics(double averageTime, double p95Time, double throughput,
                            int successfulExecutions, int failedExecutions) {
            this.averageTime = averageTime;
            this.p95Time = p95Time;
            this.throughput = throughput;
            this.successfulExecutions = successfulExecutions;
            this.failedExecutions = failedExecutions;
        }

        // Getters
        public double averageTime() { return averageTime; }
        public double p95Time() { return p95Time; }
        public double throughput() { return throughput; }
        public int successfulExecutions() { return successfulExecutions; }
        public int failedExecutions() { return failedExecutions; }
    }

    public static class ErrorHandlingResult {
        private final Map<String, Boolean> scenarioResults;
        private final List<String> errorMessages;

        public ErrorHandlingResult(Map<String, Boolean> scenarioResults, List<String> errorMessages) {
            this.scenarioResults = scenarioResults;
            this.errorMessages = errorMessages;
        }

        public boolean allPassed() {
            return scenarioResults.values().stream().allMatch(b -> b);
        }

        // Getters
        public Map<String, Boolean> getScenarioResults() { return scenarioResults; }
        public List<String> getErrorMessages() { return errorMessages; }
    }

    public static class ErrorScenario {
        private final String name;
        private final String setupCode;

        public ErrorScenario(String name, String setupCode) {
            this.name = name;
            this.setupCode = setupCode;
        }

        public String name() { return name; }
        public String setupCode() { return setupCode; }
    }

    public static class ValidationSummary {
        public int totalPatterns;
        public int passedPatterns;
        public int failedPatterns;
        public double successRate;
        public double averageExecutionTime;
        public double averageThroughput;

        public void printSummary() {
            System.out.println("=== Validation Summary ===");
            System.out.println("Total Patterns: " + totalPatterns);
            System.out.println("Passed: " + passedPatterns);
            System.out.println("Failed: " + failedPatterns);
            System.out.println("Success Rate: " + String.format("%.2f%%", successRate));
            System.out.println("Average Execution Time: " + String.format("%.2fms", averageExecutionTime));
            System.out.println("Average Throughput: " + String.format("%.2f patterns/sec", averageThroughput));
        }
    }
}