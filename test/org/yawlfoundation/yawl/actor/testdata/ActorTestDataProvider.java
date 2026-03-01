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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Test Data Provider for Actor Pattern Validation
 *
 * Provides comprehensive test data generators, mock implementations,
 * and assertion utilities for actor system testing.
 */
public class ActorTestDataProvider {

    // Specification templates
    private final Map<String, String> specTemplates = new HashMap<>();
    // Workload patterns
    private final Map<String, WorkloadPattern> workloadPatterns = new HashMap<>();
    // Test data generators
    private final Map<String, TestDataGenerator> generators = new HashMap<>();

    public ActorTestDataProvider() {
        initializeSpecTemplates();
        initializeWorkloadPatterns();
        initializeGenerators();
    }

    /**
     * Initialize YAWL specification templates for testing
     */
    private void initializeSpecTemplates() {
        // Simple actor specification
        specTemplates.put("simple_actor", """
            <?xml version="1.0" encoding="UTF-8"?>
            <specification xmlns="http://www.yawlfoundation.org/yawlschema">
              <header>
                <name>SimpleActor</name>
                <version>1.0</version>
                <description>Simple actor for testing</description>
              </header>
              <nets>
                <net id="Net">
                  <inputCondition id="i"/>
                  <tasks>
                    <task id="Task1">
                      <flowsInto id="i"/>
                      <flowsInto id="Task2"/>
                    </task>
                    <task id="Task2">
                      <flowsInto id="o"/>
                    </task>
                  </tasks>
                  <outputCondition id="o">
                    <flowsInto id="Task2"/>
                  </outputCondition>
                </net>
              </nets>
            </specification>
            """);

        // Complex actor specification with multiple actors
        specTemplates.put("multi_actor", """
            <?xml version="1.0" encoding="UTF-8"?>
            <specification xmlns="http://www.yawlfoundation.org/yawlschema">
              <header>
                <name>MultiActor</name>
                <version>1.0</version>
                <description>Multi-actor workflow with communication</description>
              </header>
              <nets>
                <net id="Net">
                  <inputCondition id="i"/>
                  <tasks>
                    <task id="Start">
                      <flowsInto id="Split"/>
                    </task>
                    <task id="Split">
                      <flowsInto id="Actor1"/>
                      <flowsInto id="Actor2"/>
                      <flowsInto id="Actor3"/>
                    </task>
                    <task id="Actor1">
                      <externalEventHandler>handler1</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Actor2">
                      <externalEventHandler>handler2</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Actor3">
                      <externalEventHandler>handler3</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Join">
                      <flowsInto id="End"/>
                    </task>
                    <task id="End">
                      <flowsInto id="o"/>
                    </task>
                  </tasks>
                  <outputCondition id="o">
                    <flowsInto id="End"/>
                  </outputCondition>
                </net>
              </nets>
            </specification>
            """);

        // High throughput specification
        specTemplates.put("high_throughput", """
            <?xml version="1.0" encoding="UTF-8"?>
            <specification xmlns="http://www.yawlfoundation.org/yawlschema">
              <header>
                <name>HighThroughput</name>
                <version>1.0</version>
                <description>Specification for throughput testing</description>
              </header>
              <nets>
                <net id="Net">
                  <inputCondition id="i"/>
                  <tasks>
                    <task id="Process">
                      <flowsInto id="Parallel"/>
                      <externalEventHandler>fast_handler</externalEventHandler>
                    </task>
                    <task id="Parallel">
                      <flowsInto id="Branch1"/>
                      <flowsInto id="Branch2"/>
                      <flowsInto id="Branch3"/>
                      <flowsInto id="Branch4"/>
                    </task>
                    <task id="Branch1">
                      <flowsInto id="Merge"/>
                    </task>
                    <task id="Branch2">
                      <flowsInto id="Merge"/>
                    </task>
                    <task id="Branch3">
                      <flowsInto id="Merge"/>
                    </task>
                    <task id="Branch4">
                      <flowsInto id="Merge"/>
                    </task>
                    <task id="Merge">
                      <flowsInto id="Complete"/>
                    </task>
                    <task id="Complete">
                      <flowsInto id="o"/>
                    </task>
                  </tasks>
                  <outputCondition id="o">
                    <flowsInto id="Complete"/>
                  </outputCondition>
                </net>
              </nets>
            </specification>
            """);

        // Stress test specification
        specTemplates.put("stress_test", """
            <?xml version="1.0" encoding="UTF-8"?>
            <specification xmlns="http://www.yawlfoundation.org/yawlschema">
              <header>
                <name>StressTest</name>
                <version>1.0</version>
                <description>Specification for stress testing</description>
              </header>
              <nets>
                <net id="Net">
                  <inputCondition id="i"/>
                  <tasks>
                    <task id="StressStart">
                      <flowsInto id="Multiple"/>
                    </task>
                    <task id="Multiple">
                      <flowsInto id="Stress1"/>
                      <flowsInto id="Stress2"/>
                      <flowsInto id="Stress3"/>
                      <flowsInto id="Stress4"/>
                      <flowsInto id="Stress5"/>
                      <flowsInto id="Stress6"/>
                      <flowsInto id="Stress7"/>
                      <flowsInto id="Stress8"/>
                      <flowsInto id="Stress9"/>
                      <flowsInto id="Stress10"/>
                    </task>
                    <task id="Stress1">
                      <externalEventHandler>stress_handler</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Stress2">
                      <externalEventHandler>stress_handler</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Stress3">
                      <externalEventHandler>stress_handler</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Stress4">
                      <externalEventHandler>stress_handler</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Stress5">
                      <externalEventHandler>stress_handler</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Stress6">
                      <externalEventHandler>stress_handler</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Stress7">
                      <externalEventHandler>stress_handler</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Stress8">
                      <externalEventHandler>stress_handler</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Stress9">
                      <externalEventHandler>stress_handler</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Stress10">
                      <externalEventHandler>stress_handler</externalEventHandler>
                      <flowsInto id="Join"/>
                    </task>
                    <task id="Join">
                      <flowsInto id="StressEnd"/>
                    </task>
                    <task id="StressEnd">
                      <flowsInto id="o"/>
                    </task>
                  </tasks>
                  <outputCondition id="o">
                    <flowsInto id="StressEnd"/>
                  </outputCondition>
                </net>
              </nets>
            </specification>
            """);
    }

    /**
     * Initialize workload patterns for testing
     */
    private void initializeWorkloadPatterns() {
        // Normal workload pattern
        workloadPatterns.put("normal", new WorkloadPattern(
            "Normal workload with steady rate",
            100, // messages per second
            Arrays.asList("normal_message")
        ));

        // Burst workload pattern
        workloadPatterns.put("burst", new WorkloadPattern(
            "Burst workload with spikes",
            10, // base rate
            Arrays.asList("normal_message"),
            Arrays.asList(
                new BurstPattern(0.1, 50, 1000) // 10% chance of 50 messages at 1x rate
            )
        ));

        // Flood workload pattern
        workloadPatterns.put("flood", new WorkloadPattern(
            "Flood workload with high rate",
            2000, // messages per second
            Arrays.asList("flood_message")
        ));

        // Mixed workload pattern
        workloadPatterns.put("mixed", new WorkloadPattern(
            "Mixed workload with different types",
            50, // messages per second
            Arrays.asList("normal", "priority", "large"),
            Arrays.asList(0.7, 0.2, 0.1) // distribution
        ));
    }

    /**
     * Initialize test data generators
     */
    private void initializeGenerators() {
        // Message generator
        generators.put("message", new TestDataGenerator<>() {
            @Override
            public Map<String, Object> generate(int id) {
                Map<String, Object> message = new HashMap<>();
                message.put("message_id", id);
                message.put("timestamp", System.currentTimeMillis());
                message.put("priority", "normal");
                message.put("data", generateTestData(id));
                return message;
            }

            private Object generateTestData(int id) {
                // Generate test data based on ID
                if (id % 10 == 0) {
                    // Large message (10KB)
                    return "x".repeat(10240);
                } else if (id % 5 == 0) {
                    // Medium message (1KB)
                    return "test_data_" + id;
                } else {
                    // Small message
                    return id;
                }
            }
        });

        // Workitem generator
        generators.put("workitem", new TestDataGenerator<>() {
            @Override
            public YWorkItem generate(int id) throws Exception {
                // Create mock workitem for testing
                YWorkItem workItem = new YWorkItem();
                workItem.setID("workitem_" + id);
                workItem.setName("TestWorkItem_" + id);
                workItem.setStatus(StatusStatus.Fired);
                return workItem;
            }
        });

        // Case generator
        generators.put("case", new TestDataGenerator<>() {
            @Override
            public String generate(int id) {
                return "case_" + id + "_" + UUID.randomUUID();
            }
        });

        // Actor generator
        generators.put("actor", new TestDataGenerator<>() {
            @Override
            public String generate(int id) {
                return "actor_" + id + "_yawl";
            }
        });
    }

    // Public methods for test data generation

    /**
     * Generate YAWL specification for testing
     */
    public YSpecification createSpecification(String templateName, String specName) throws Exception {
        String template = specTemplates.get(templateName);
        if (template == null) {
            throw new IllegalArgumentException("Unknown template: " + templateName);
        }

        // Import specification using template
        YEngine engine = YEngine.getInstance();
        String xml = template.replace("name=\"SimpleActor\"", "name=\"" + specName + "\"");
        YSpecificationID specID = engine.importSpecification(xml);
        return engine.getSpecification(specID);
    }

    /**
     * Generate messages for testing
     */
    public List<Map<String, Object>> generateMessages(int count, String type) {
        TestDataGenerator<Map<String, Object>> generator = generators.get("message");
        List<Map<String, Object>> messages = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Map<String, Object> message = generator.generate(i);
            if (type != null) {
                message.put("type", type);
            }
            messages.add(message);
        }

        return messages;
    }

    /**
     * Generate workload pattern
     */
    public WorkloadPattern getWorkloadPattern(String patternName) {
        WorkloadPattern pattern = workloadPatterns.get(patternName);
        if (pattern == null) {
            throw new IllegalArgumentException("Unknown pattern: " + patternName);
        }
        return pattern;
    }

    /**
     * Generate actor IDs for testing
     */
    public List<String> generateActorIDs(int count) {
        TestDataGenerator<String> generator = generators.get("actor");
        List<String> actorIDs = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            actorIDs.add(generator.generate(i));
        }

        return actorIDs;
    }

    /**
     * Generate test cases with specified pattern
     */
    public List<TestScenario> generateTestScenarios(int scenarioCount, String workloadPattern) {
        List<TestScenario> scenarios = new ArrayList<>();
        WorkloadPattern pattern = getWorkloadPattern(workloadPattern);

        for (int i = 0; i < scenarioCount; i++) {
            TestScenario scenario = new TestScenario(
                "Scenario_" + i,
                generateActorIDs(3 + ThreadLocalRandom.current().nextInt(5)), // 3-7 actors
                pattern,
                generateMessages(100 + ThreadLocalRandom.current().nextInt(400), "test") // 100-500 messages
            );
            scenarios.add(scenario);
        }

        return scenarios;
    }

    /**
     * Save test data to files
     */
    public void saveTestDataToFile(String filename, Object data) throws IOException {
        Path path = Paths.get("test/actor/testdata", filename);
        Files.createDirectories(path.getParent());

        String content;
        if (data instanceof String) {
            content = (String) data;
        } else if (data instanceof Collection) {
            content = ((Collection<?>) data).stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
        } else {
            content = data.toString();
        }

        Files.writeString(path, content);
    }

    /**
     * Load test data from files
     */
    public String loadTestDataFromFile(String filename) throws IOException {
        Path path = Paths.get("test/actor/testdata", filename);
        return Files.readString(path);
    }

    // Helper classes

    /**
     * Workload pattern definition
     */
    public static class WorkloadPattern {
        private final String description;
        private final int baseRate;
        private final List<String> messageTypes;
        private final List<Double> messageDistribution;
        private final List<BurstPattern> burstPatterns;

        public WorkloadPattern(String description, int baseRate, List<String> messageTypes) {
            this(description, baseRate, messageTypes, null, null);
        }

        public WorkloadPattern(String description, int baseRate, List<String> messageTypes,
                              List<Double> messageDistribution) {
            this(description, baseRate, messageTypes, messageDistribution, null);
        }

        public WorkloadPattern(String description, int baseRate, List<String> messageTypes,
                              List<Double> messageDistribution, List<BurstPattern> burstPatterns) {
            this.description = description;
            this.baseRate = baseRate;
            this.messageTypes = messageTypes;
            this.messageDistribution = messageDistribution;
            this.burstPatterns = burstPatterns;
        }

        // Getters
        public String getDescription() { return description; }
        public int getBaseRate() { return baseRate; }
        public List<String> getMessageTypes() { return messageTypes; }
        public List<Double> getMessageDistribution() { return messageDistribution; }
        public List<BurstPattern> getBurstPatterns() { return burstPatterns; }
    }

    /**
     * Burst pattern definition
     */
    public static class BurstPattern {
        private final double probability;
        private final int multiplier;
        private final int durationMillis;

        public BurstPattern(double probability, int multiplier, int durationMillis) {
            this.probability = probability;
            this.multiplier = multiplier;
            this.durationMillis = durationMillis;
        }

        // Getters
        public double getProbability() { return probability; }
        public int getMultiplier() { return multiplier; }
        public int getDurationMillis() { return durationMillis; }
    }

    /**
     * Test scenario definition
     */
    public static class TestScenario {
        private final String name;
        private final List<String> actorIDs;
        private final WorkloadPattern workloadPattern;
        private final List<Map<String, Object>> testMessages;
        private final long startTime;
        private final Map<String, Object> metrics;

        public TestScenario(String name, List<String> actorIDs, WorkloadPattern workloadPattern,
                          List<Map<String, Object>> testMessages) {
            this.name = name;
            this.actorIDs = actorIDs;
            this.workloadPattern = workloadPattern;
            this.testMessages = testMessages;
            this.startTime = System.currentTimeMillis();
            this.metrics = new HashMap<>();
        }

        // Getters and setters
        public String getName() { return name; }
        public List<String> getActorIDs() { return actorIDs; }
        public WorkloadPattern getWorkloadPattern() { return workloadPattern; }
        public List<Map<String, Object>> getTestMessages() { return testMessages; }
        public long getStartTime() { return startTime; }
        public Map<String, Object> getMetrics() { return metrics; }
        public void addMetric(String key, Object value) { metrics.put(key, value); }
    }

    /**
     * Generic test data generator interface
     */
    @FunctionalInterface
    public interface TestDataGenerator<T> {
        T generate(int id) throws Exception;
    }

    /**
     * Assertion utilities for actor testing
     */
    public static class ActorAssertions {
        /**
         * Assert message delivery rate
         */
        public static void assertMessageDeliveryRate(double actualRate, double minRate, String message) {
            assertTrue(actualRate >= minRate,
                String.format("%s: Actual rate %.2f must be >= %.2f", message, actualRate, minRate));
        }

        /**
         * Assert latency bounds
         */
        public static void assertLatencyBounds(double avgLatency, double maxLatency,
                                            double maxBound, String context) {
            assertTrue(avgLatency <= maxBound * 0.1,
                String.format("%s: Avg latency %.2f ms exceeds 10%% of max %.2f",
                             context, avgLatency, maxBound));
            assertTrue(maxLatency <= maxBound,
                String.format("%s: Max latency %.2f ms exceeds max %.2f",
                             context, maxLatency, maxBound));
        }

        /**
         * assert memory usage bounds
         */
        public static void assertMemoryBounds(long actualMemory, long maxMemory, String context) {
            assertTrue(actualMemory <= maxMemory,
                String.format("%s: Memory %d bytes exceeds limit %d bytes",
                             context, actualMemory, maxMemory));
        }

        /**
         * Assert success rate
         */
        public static void assertSuccessRate(double actualRate, double minRate, String context) {
            assertTrue(actualRate >= minRate,
                String.format("%s: Success rate %.2f%% must be >= %.2f%%",
                             context, actualRate * 100, minRate * 100));
        }

        /**
         * Assert scaling efficiency
         */
        public static void assertScalingEfficiency(double actualEfficiency, double minEfficiency, String context) {
            assertTrue(actualEfficiency >= minEfficiency,
                String.format("%s: Scaling efficiency %.2f must be >= %.2f",
                             context, actualEfficiency, minEfficiency));
        }
    }

    /**
     * Performance metrics collector
     */
    public static class PerformanceMetricsCollector {
        private final Map<String, List<Long>> metrics = new ConcurrentHashMap<>();
        private final String testId;

        public PerformanceMetricsCollector(String testId) {
            this.testId = testId;
        }

        public void recordMetric(String name, long value) {
            metrics.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()).add(value);
        }

        public double getAverage(String name) {
            List<Long> values = metrics.get(name);
            if (values == null || values.isEmpty()) return 0;
            return values.stream().mapToLong(Long::longValue).average().orElse(0);
        }

        public double getPercentile(String name, double percentile) {
            List<Long> values = metrics.get(name);
            if (values == null || values.isEmpty()) return 0;
            values.sort(Long::compare);
            int index = (int) (values.size() * percentile);
            return values.get(index);
        }

        public Map<String, Double> getSummary() {
            Map<String, Double> summary = new HashMap<>();
            metrics.forEach((name, values) -> {
                if (!values.isEmpty()) {
                    summary.put(name + "_avg", values.stream().mapToLong(Long::longValue).average().orElse(0));
                    summary.put(name + "_max", values.stream().mapToLong(Long::longValue).max().orElse(0));
                    summary.put(name + "_p99", getPercentile(name, 0.99));
                }
            });
            return summary;
        }

        public void generateReport(String filename) throws IOException {
            Map<String, Double> summary = getSummary();
            StringBuilder report = new StringBuilder();
            report.append("Performance Metrics Report - ").append(testId).append("\n");
            report.append("=============================================\n\n");

            summary.forEach((key, value) -> {
                report.append(String.format("%s: %.2f\n", key, value));
            });

            ActorTestDataProvider provider = new ActorTestDataProvider();
            provider.saveTestDataToFile(filename, report.toString());
        }
    }
}