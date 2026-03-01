/*
 * YAWL - Yet Another Workflow Language
 * Copyright (C) 2003-2006, 2008-2011, 2014-2019 National University of Ireland, Galway
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.yawlfoundation.yawl.actor.testdata;

import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.engine.*;
import org.yawlfoundation.yawl.engine.YWorkItem.StatusStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Test Implementations for actor system validation
 *
 * Provides real implementations of YAWL components for testing purposes
 * using actual YAWL engine and avoiding all mock/stub patterns.
 * This class throws UnsupportedOperationException for any operations that
 * are not fully implemented according to Fortune 5 standards.
 */
public class TestImplementations {

    /**
     * Test Engine Wrapper - Provides real YAWL engine functionality
     * with proper error handling and validation.
     */
    public static class TestEngineWrapper {
        private final YEngine engine;
        private final AtomicLong operationCounter = new AtomicLong(0);
        private final Map<String, Long> operationLatencies = new ConcurrentHashMap<>();

        public TestEngineWrapper() {
            this.engine = YEngine.getInstance();
            if (engine == null) {
                throw new IllegalStateException("YAWL Engine must be initialized before creating TestEngineWrapper");
            }
            validateEngineInitialization();
        }

        private void validateEngineInitialization() {
            try {
                engine.initialise();
            } catch (Exception e) {
                throw new UnsupportedOperationException("Failed to initialize YAWL engine: " + e.getMessage(), e);
            }
        }

        /**
         * Import specification with proper validation
         */
        public YSpecificationID importSpecification(String xml) throws UnsupportedOperationException {
            long startTime = System.nanoTime();
            validateXMLInput(xml);

            try {
                YSpecificationID specID = engine.importSpecification(xml);
                recordOperation("importSpecification", startTime);

                if (specID == null) {
                    throw new UnsupportedOperationException("Specification import returned null");
                }

                return specID;
            } catch (Exception e) {
                throw new UnsupportedOperationException("Failed to import specification: " + e.getMessage(), e);
            }
        }

        /**
         * Create case with proper validation
         */
        public String createCase(YSpecificationID specID) throws UnsupportedOperationException {
            long startTime = System.nanoTime();
            validateSpecificationID(specID);

            try {
                String caseID = engine.createCase(specID);
                recordOperation("createCase", startTime);

                if (caseID == null || caseID.isEmpty()) {
                    throw new UnsupportedOperationException("Case creation returned null or empty ID");
                }

                return caseID;
            } catch (Exception e) {
                throw new UnsupportedOperationException("Failed to create case: " + e.getMessage(), e);
            }
        }

        /**
         * Get work items with proper validation
         */
        public List<YWorkItem> getWorkItems(String caseID, StatusStatus status) throws UnsupportedOperationException {
            long startTime = System.nanoTime();
            validateCaseID(caseID);

            try {
                List<YWorkItem> workItems = engine.getWorkItems(caseID, status);
                recordOperation("getWorkItems", startTime);

                if (workItems == null) {
                    throw new UnsupportedOperationException("getWorkItems returned null");
                }

                return Collections.unmodifiableList(workItems);
            } catch (Exception e) {
                throw new UnsupportedOperationException("Failed to get work items: " + e.getMessage(), e);
            }
        }

        /**
         * Fire external event with proper validation
         */
        public boolean fireExternalEvent(String caseID, String eventName,
                                        Map<String, Object> eventData, String participantID) throws UnsupportedOperationException {
            long startTime = System.nanoTime();
            validateEventInputs(caseID, eventName, participantID);

            try {
                boolean success = engine.fireExternalEvent(caseID, eventName, eventData, participantID);
                recordOperation("fireExternalEvent", startTime);

                return success;
            } catch (Exception e) {
                throw new UnsupportedOperationException("Failed to fire external event: " + e.getMessage(), e);
            }
        }

        /**
         * Complete case with proper validation
         */
        public boolean completeCase(String caseID) throws UnsupportedOperationException {
            long startTime = System.nanoTime();
            validateCaseID(caseID);

            try {
                boolean success = engine.completeCase(caseID);
                recordOperation("completeCase", startTime);
                return success;
            } catch (Exception e) {
                throw new UnsupportedOperationException("Failed to complete case: " + e.getMessage(), e);
            }
        }

        /**
         * Get active participants with proper validation
         */
        public List<String> getActiveParticipants() throws UnsupportedOperationException {
            long startTime = System.nanoTime();

            try {
                List<String> participants = engine.getActiveParticipants();
                recordOperation("getActiveParticipants", startTime);

                if (participants == null) {
                    return Collections.emptyList();
                }

                return Collections.unmodifiableList(participants);
            } catch (Exception e) {
                throw new UnsupportedOperationException("Failed to get active participants: " + e.getMessage(), e);
            }
        }

        // Private validation methods
        private void validateXMLInput(String xml) {
            if (xml == null || xml.trim().isEmpty()) {
                throw new UnsupportedOperationException("XML input cannot be null or empty");
            }
            if (!xml.startsWith("<?xml")) {
                throw new UnsupportedOperationException("XML input must start with XML declaration");
            }
        }

        private void validateSpecificationID(YSpecificationID specID) {
            if (specID == null) {
                throw new UnsupportedOperationException("Specification ID cannot be null");
            }
            if (specID.getName() == null || specID.getName().trim().isEmpty()) {
                throw new UnsupportedOperationException("Specification name cannot be null or empty");
            }
        }

        private void validateCaseID(String caseID) {
            if (caseID == null || caseID.trim().isEmpty()) {
                throw new UnsupportedOperationException("Case ID cannot be null or empty");
            }
        }

        private void validateEventInputs(String caseID, String eventName, String participantID) {
            validateCaseID(caseID);
            if (eventName == null || eventName.trim().isEmpty()) {
                throw new UnsupportedOperationException("Event name cannot be null or empty");
            }
            if (participantID == null || participantID.trim().isEmpty()) {
                throw new UnsupportedOperationException("Participant ID cannot be null or empty");
            }
        }

        private void recordOperation(String operationName, long startTime) {
            long duration = System.nanoTime() - startTime;
            operationCounter.incrementAndGet();
            operationLatencies.put(operationName + "_" + operationCounter.get(), duration);
        }

        public Map<String, Long> getOperationLatencies() {
            return Collections.unmodifiableMap(operationLatencies);
        }
    }

    /**
     * Test Data Generator - Provides real test data generation
     * without using any mock patterns.
     */
    public static class TestDataGenerator {
        private final Random random = new Random();
        private final AtomicInteger idCounter = new AtomicInteger(0);

        /**
         * Generate valid YAWL XML specification
         */
        public String generateSpecificationXML(String name, int actorCount) {
            if (name == null || name.trim().isEmpty()) {
                throw new UnsupportedOperationException("Specification name cannot be null or empty");
            }

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<specification xmlns=\"http://www.yawlfoundation.org/yawlschema\">\n");
            xml.append("  <header>\n");
            xml.append("    <name>").append(name).append("</name>\n");
            xml.append("    <version>1.0</version>\n");
            xml.append("    <description>Generated specification for testing</description>\n");
            xml.append("  </header>\n");
            xml.append("  <nets>\n");
            xml.append("    <net id=\"Net\">\n");
            xml.append("      <inputCondition id=\"i\"/>\n");

            // Add actor tasks
            for (int i = 0; i < actorCount; i++) {
                xml.append("      <tasks>\n");
                xml.append("        <task id=\"Actor").append(i).append("\">\n");
                xml.append("          <flowsInto id=\"i\"/>\n");
                xml.append("          <flowsInto id=\"o\"/>\n");
                xml.append("          <externalEventHandler>handler_").append(i).append("</externalEventHandler>\n");
                xml.append("        </task>\n");
                xml.append("      </tasks>\n");
            }

            xml.append("      <outputCondition id=\"o\">\n");
            for (int i = 0; i < actorCount; i++) {
                xml.append("        <flowsInto id=\"Actor").append(i).append("\"/>\n");
            }
            xml.append("      </outputCondition>\n");
            xml.append("    </net>\n");
            xml.append("  </nets>\n");
            xml.append("</specification>");

            return xml.toString();
        }

        /**
         * Generate test messages
         */
        public List<Map<String, Object>> generateMessages(int count, String messageType) {
            if (count < 0) {
                throw new UnsupportedOperationException("Message count cannot be negative");
            }

            List<Map<String, Object>> messages = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Map<String, Object> message = new HashMap<>();
                message.put("message_id", generateMessageID());
                message.put("timestamp", System.currentTimeMillis());
                message.put("type", messageType != null ? messageType : "normal");
                message.put("priority", random.nextDouble() < 0.1 ? "high" : "normal");
                message.put("data", generateTestData());
                message.put("sequence_number", i);
                message.put("correlation_id", generateCorrelationID());
                messages.add(message);
            }
            return Collections.unmodifiableList(messages);
        }

        /**
         * Generate test actors
         */
        public List<String> generateActorIDs(int count) {
            if (count < 0) {
                throw new UnsupportedOperationException("Actor count cannot be negative");
            }

            List<String> actorIDs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                actorIDs.add("actor_" + UUID.randomUUID().toString().substring(0, 8));
            }
            return Collections.unmodifiableList(actorIDs);
        }

        /**
         * Generate test scenarios
         */
        public List<TestScenario> generateTestScenarios(int scenarioCount) {
            if (scenarioCount < 0) {
                throw new UnsupportedOperationException("Scenario count cannot be negative");
            }

            List<TestScenario> scenarios = new ArrayList<>();
            for (int i = 0; i < scenarioCount; i++) {
                int actorCount = 3 + random.nextInt(7); // 3-9 actors
                List<String> actorIDs = generateActorIDs(actorCount);
                List<Map<String, Object>> messages = generateMessages(
                    100 + random.nextInt(400), "test_" + i
                );

                TestScenario scenario = new TestScenario(
                    "Scenario_" + i,
                    actorIDs,
                    messages
                );
                scenarios.add(scenario);
            }
            return Collections.unmodifiableList(scenarios);
        }

        // Private helper methods
        private String generateMessageID() {
            return "msg_" + System.currentTimeMillis() + "_" + idCounter.incrementAndGet();
        }

        private Object generateTestData() {
            switch (random.nextInt(4)) {
                case 0:
                    return generateRandomString(50);
                case 1:
                    return generateRandomString(1024); // 1KB
                case 2:
                    return generateRandomString(10240); // 10KB
                default:
                    return random.nextInt(1000);
            }
        }

        private String generateCorrelationID() {
            return "corr_" + UUID.randomUUID().toString();
        }

        private String generateRandomString(int length) {
            if (length <= 0) {
                throw new UnsupportedOperationException("String length must be positive");
            }
            String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < length; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            return sb.toString();
        }
    }

    /**
     * Test Scenario - Immutable data class for test scenarios
     */
    public static final class TestScenario {
        private final String name;
        private final List<String> actorIDs;
        private final List<Map<String, Object>> testMessages;
        private final long creationTimestamp;
        private final Map<String, Object> metadata;

        public TestScenario(String name, List<String> actorIDs, List<Map<String, Object>> testMessages) {
            this.name = validateString(name, "Scenario name");
            this.actorIDs = validateList(actorIDs, "Actor IDs");
            this.testMessages = validateList(testMessages, "Test messages");
            this.creationTimestamp = System.currentTimeMillis();
            this.metadata = new ConcurrentHashMap<>();
        }

        public TestScenario addMetadata(String key, Object value) {
            if (key == null || key.trim().isEmpty()) {
                throw new UnsupportedOperationException("Metadata key cannot be null or empty");
            }
            metadata.put(key, value);
            return this;
        }

        // Getters
        public String getName() { return name; }
        public List<String> getActorIDs() { return Collections.unmodifiableList(actorIDs); }
        public List<Map<String, Object>> getTestMessages() { return Collections.unmodifiableList(testMessages); }
        public long getCreationTimestamp() { return creationTimestamp; }
        public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }

        // Validation helpers
        private String validateString(String value, String fieldName) {
            if (value == null || value.trim().isEmpty()) {
                throw new UnsupportedOperationException(fieldName + " cannot be null or empty");
            }
            return value;
        }

        private <T> List<T> validateList(List<T> list, String fieldName) {
            if (list == null) {
                throw new UnsupportedOperationException(fieldName + " cannot be null");
            }
            return Collections.unmodifiableList(list);
        }
    }

    /**
     * Performance Metrics Collector - Collects real performance metrics
     */
    public static class PerformanceMetricsCollector {
        private final Map<String, List<Long>> metrics = new ConcurrentHashMap<>();
        private final String testId;
        private final long startTime;

        public PerformanceMetricsCollector(String testId) {
            if (testId == null || testId.trim().isEmpty()) {
                throw new UnsupportedOperationException("Test ID cannot be null or empty");
            }
            this.testId = testId;
            this.startTime = System.currentTimeMillis();
        }

        /**
         * Record a performance metric
         */
        public void recordMetric(String name, long value) {
            if (name == null || name.trim().isEmpty()) {
                throw new UnsupportedOperationException("Metric name cannot be null or empty");
            }
            if (value < 0) {
                throw new UnsupportedOperationException("Metric value cannot be negative");
            }

            metrics.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()).add(value);
        }

        /**
         * Calculate average for a metric
         */
        public double getAverage(String name) {
            List<Long> values = metrics.get(name);
            if (values == null || values.isEmpty()) return 0;
            return values.stream().mapToLong(Long::longValue).average().orElse(0);
        }

        /**
         * Calculate maximum for a metric
         */
        public double getMaximum(String name) {
            List<Long> values = metrics.get(name);
            if (values == null || values.isEmpty()) return 0;
            return values.stream().mapToLong(Long::longValue).max().orElse(0);
        }

        /**
         * Calculate percentile for a metric
         */
        public double getPercentile(String name, double percentile) {
            List<Long> values = metrics.get(name);
            if (values == null || values.isEmpty()) return 0;
            values.sort(Long::compare);
            int index = (int) (values.size() * Math.max(0, Math.min(1, percentile)));
            return values.get(index);
        }

        /**
         * Get summary statistics for all metrics
         */
        public Map<String, Map<String, Double>> getSummaryStatistics() {
            Map<String, Map<String, Double>> summary = new HashMap<>();
            metrics.forEach((name, values) -> {
                if (!values.isEmpty()) {
                    Map<String, Double> stats = new HashMap<>();
                    stats.put("avg", values.stream().mapToLong(Long::longValue).average().orElse(0));
                    stats.put("max", values.stream().mapToLong(Long::longValue).max().orElse(0));
                    stats.put("min", values.stream().mapToLong(Long::longValue).min().orElse(0));
                    stats.put("p95", getPercentile(name, 0.95));
                    stats.put("p99", getPercentile(name, 0.99));
                    stats.put("count", (double) values.size());
                    summary.put(name, stats);
                }
            });
            return Collections.unmodifiableMap(summary);
        }

        /**
         * Generate performance report
         */
        public String generateReport() {
            StringBuilder report = new StringBuilder();
            report.append("Performance Metrics Report\n");
            report.append("==========================\n");
            report.append("Test ID: ").append(testId).append("\n");
            report.append("Duration: ").append(System.currentTimeMillis() - startTime).append(" ms\n\n");

            Map<String, Map<String, Double>> summary = getSummaryStatistics();
            summary.forEach((name, stats) -> {
                report.append(name).append(":\n");
                stats.forEach((key, value) -> {
                    report.append("  ").append(key).append(": ").append(String.format("%.3f", value)).append("\n");
                });
                report.append("\n");
            });

            return report.toString();
        }

        /**
         * Save report to file
         */
        public void saveReport(String filename) throws IOException {
            String report = generateReport();
            Path path = Paths.get("test/actor/reports", filename);
            Files.createDirectories(path.getParent());
            Files.writeString(path, report);
        }
    }

    /**
     * Test Validator - Provides validation utilities for actor testing
     */
    public static class TestValidator {
        /**
         * Validate actor system requirements
         */
        public static ValidationResult validateActorSystemRequirements(
            int expectedActorCount,
            int expectedMessageCount,
            double maxLatencyMs,
            double minSuccessRate
        ) {
            ValidationResult result = new ValidationResult();

            // Validate actor count
            if (expectedActorCount <= 0) {
                result.addError("Actor count must be positive");
            }

            // Validate message count
            if (expectedMessageCount < 0) {
                result.addError("Message count cannot be negative");
            }

            // Validate latency threshold
            if (maxLatencyMs <= 0) {
                result.addError("Maximum latency must be positive");
            }

            // Validate success rate
            if (minSuccessRate < 0 || minSuccessRate > 1) {
                result.addError("Success rate must be between 0 and 1");
            }

            result.setValid(result.getErrors().isEmpty());
            return result;
        }

        /**
         * Validate test results against expectations
         */
        public static ValidationResult validateTestResults(
            double actualThroughput,
            double actualLatency,
            double actualSuccessRate,
            double expectedThroughput,
            double expectedLatency,
            double expectedSuccessRate
        ) {
            ValidationResult result = new ValidationResult();

            // Validate throughput
            if (actualThroughput < expectedThroughput * 0.8) {
                result.addError(String.format(
                    "Throughput %.2f msg/s is below expected %.2f msg/s (80%% threshold)",
                    actualThroughput, expectedThroughput
                ));
            }

            // Validate latency
            if (actualLatency > expectedLatency * 1.5) {
                result.addError(String.format(
                    "Latency %.2f ms exceeds expected %.2f ms (150%% threshold)",
                    actualLatency, expectedLatency
                ));
            }

            // Validate success rate
            if (actualSuccessRate < expectedSuccessRate) {
                result.addError(String.format(
                    "Success rate %.2f%% is below expected %.2f%%",
                    actualSuccessRate * 100, expectedSuccessRate * 100
                ));
            }

            result.setValid(result.getErrors().isEmpty());
            return result;
        }
    }

    /**
     * Validation Result - Immutable result class for validation
     */
    public static final class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public ValidationResult() {
            this.valid = true;
            this.errors = Collections.emptyList();
        }

        private ValidationResult(List<String> errors) {
            this.valid = false;
            this.errors = Collections.unmodifiableList(errors);
        }

        public void addError(String error) {
            throw new UnsupportedOperationException("Cannot modify immutable ValidationResult");
        }

        public ValidationResult withError(String error) {
            List<String> newErrors = new ArrayList<>(errors);
            newErrors.add(error);
            return new ValidationResult(newErrors);
        }

        // Getters
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public boolean hasErrors() { return !errors.isEmpty(); }
    }
}