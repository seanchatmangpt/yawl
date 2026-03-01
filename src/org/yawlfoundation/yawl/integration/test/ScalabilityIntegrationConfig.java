/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Scalability Integration Test Configuration.
 *
 * This class provides configuration and coordination for all scalability
 * integration tests in the YAWL ecosystem:
 *
 * 1. Test Suite Coordination
 * 2. Integration Validation Rules
 * 3. Performance SLA Configuration
 * 4. Scale Level Definitions
 * 5. Event Monitoring Configuration
 * 6. Chaos Engineering Parameters
 *
 * Key Responsibilities:
 * - Centralize test configuration across all integration suites
 * - Define validation rules for integration points
 * - Configure performance SLAs at different scales
 * - Coordinate test execution order and dependencies
 * - Manage shared test resources
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ScalabilityIntegrationConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScalabilityIntegrationConfig.class);

    // Singleton instance
    private static volatile ScalabilityIntegrationConfig instance;

    // Test configuration
    private final Map<String, TestSuiteConfig> testSuiteConfigs;
    private final Map<String, IntegrationPointConfig> integrationPointConfigs;
    private final Map<String, SLAConfig> slaConfigs;
    private final Map<String, ScaleConfig> scaleConfigs;

    // Performance monitoring
    private final Map<String, Long> performanceMetrics;
    private final Map<String, Integer> testExecutionCount;
    private final List<String> validationLog;

    private ScalabilityIntegrationConfig() {
        this.testSuiteConfigs = new ConcurrentHashMap<>();
        this.integrationPointConfigs = new ConcurrentHashMap<>();
        this.slaConfigs = new ConcurrentHashMap<>();
        this.scaleConfigs = new ConcurrentHashMap<>();
        this.performanceMetrics = new ConcurrentHashMap<>();
        this.testExecutionCount = new ConcurrentHashMap<>();
        this.validationLog = Collections.synchronizedList(new ArrayList<>());

        initializeConfiguration();
    }

    /**
     * Get singleton instance
     */
    public static ScalabilityIntegrationConfig getInstance() {
        if (instance == null) {
            synchronized (ScalabilityIntegrationConfig.class) {
                if (instance == null) {
                    instance = new ScalabilityIntegrationConfig();
                }
            }
        }
        return instance;
    }

    // ========== INITIALIZATION ==========

    private void initializeConfiguration() {
        LOGGER.info("Initializing Scalability Integration Configuration");

        initializeTestSuiteConfigs();
        initializeIntegrationPointConfigs();
        initializeSLAConfigs();
        initializeScaleConfigs();

        LOGGER.info("Configuration initialized with {} test suites, {} integration points, {} SLAs, {} scales",
            testSuiteConfigs.size(), integrationPointConfigs.size(), slaConfigs.size(), scaleConfigs.size());
    }

    private void initializeTestSuiteConfigs() {
        // Core Integration Test Suite
        testSuiteConfigs.put("ScalabilityIntegrationTestSuite", new TestSuiteConfig(
            "Scalability Integration Test Suite",
            Arrays.asList("I1", "I2", "I3", "I4", "I5"),
            Duration.ofMinutes(240),
            true
        ));

        // Workflow Invariant Compatibility Test
        testSuiteConfigs.put("WorkflowInvariantCompatibilityTest", new TestSuiteConfig(
            "Workflow Invariant Compatibility Test",
            Arrays.asList("I1", "I2", "I3", "I4", "I5", "I6", "I7"),
            Duration.ofMinutes(180),
            true
        ));

        // YStatelessEngine Scale Integration Test
        testSuiteConfigs.put("YStatelessEngineScaleIntegrationTest", new TestSuiteConfig(
            "YStatelessEngine Scale Integration Test",
            Arrays.asList("I1", "I2", "I3", "I4", "I5", "I6", "I7"),
            Duration.ofMinutes(240),
            true
        ));

        // Fortune Five Scale Test
        testSuiteConfigs.put("FortuneFiveScaleTest", new TestSuiteConfig(
            "Fortune Five Scale Test",
            Arrays.asList("baseline", "medium", "full"),
            Duration.ofMinutes(300),
            true
        ));

        // Cross-ART Coordination Test
        testSuiteConfigs.put("CrossARTCoordinationTest", new TestSuiteConfig(
            "Cross-ART Coordination Test",
            Arrays.asList("C1", "C2", "C3", "C4", "C5"),
            Duration.ofMinutes(180),
            true
        ));

        // Comprehensive Scalability Integration Test
        testSuiteConfigs.put("ComprehensiveScalabilityIntegrationTest", new TestSuiteConfig(
            "Comprehensive Scalability Integration Test",
            Arrays.asList("I1", "I2", "I3", "I4", "I5"),
            Duration.ofMinutes(300),
            true
        ));

        // Self-Play Integration Test
        testSuiteConfigs.put("SelfPlayTest", new TestSuiteConfig(
            "Self-Play Integration Test",
            Arrays.asList("zai_connection", "basic_chat", "workflow_decision", "data_transformation", "function_calling", "mcp_client", "a2a_client", "multi_agent", "end_to_end"),
            Duration.ofMinutes(60),
            true
        ));
    }

    private void initializeIntegrationPointConfigs() {
        // YEngine ↔ YStatelessEngine integration
        integrationPointConfigs.put("StatefulStatelessIntegration", new IntegrationPointConfig(
            "YEngine ↔ YStatelessEngine",
            "Validates equivalence between stateful and stateless execution",
            95.0,  // minimum success rate
            Arrays.asList("StatefulEngine", "StatelessEngine", "WorkItem", "Case")
        ));

        // Scale Testing ↔ Invariant Properties integration
        integrationPointConfigs.put("ScaleInvariantIntegration", new IntegrationPointConfig(
            "Scale Testing ↔ Invariant Properties",
            "Validates that scale tests maintain workflow invariants",
            98.0,
            Arrays.asList("FortuneScaleOrchestrator", "WorkflowInvariantPropertyTest", "PerformanceMetrics")
        ));

        // Cross-ART ↔ Event Monitoring integration
        integrationPointConfigs.put("CrossARTMonitoringIntegration", new IntegrationPointConfig(
            "Cross-ART ↔ Event Monitoring",
            "Validates cross-ART coordination with event monitoring",
            96.0,
            Arrays.asList("CrossARTCoordinationTest", "EventMonitor", "MessageBus")
        ));

        // Performance ↔ Consistency integration
        integrationPointConfigs.put("PerformanceConsistencyIntegration", new IntegrationPointConfig(
            "Performance ↔ Consistency",
            "Validates performance SLA while maintaining data consistency",
            99.0,
            Arrays.asList("Throughput", "Latency", "ConsistencyScore")
        ));

        // Stateful ↔ Stateless Scaling integration
        integrationPointConfigs.put("HybridScalingIntegration", new IntegrationPointConfig(
            "Stateful ↔ Stateless Scaling",
            "Validates hybrid scaling between stateful and stateless engines",
            97.0,
            Arrays.asList("StatefulEngine", "StatelessEngine", "ScalingStrategy")
        ));

        // Event Sourcing ↔ Real-time Validation integration
        integrationPointConfigs.put("EventSourcingValidationIntegration", new IntegrationPointConfig(
            "Event Sourcing ↔ Real-time Validation",
            "Validates real-time event processing and validation",
            99.0,
            Arrays.asList("EventStore", "RealtimeValidator", "ConsistencyChecker")
        ));
    }

    private void initializeSLAConfigs() {
        // Single instance SLAs
        slaConfigs.put("single_instance", new SLAConfig(
            "Single Instance SLA",
            100,    // max cases per second
            50,     // max avg response time (ms)
            99.9,   // min availability (%)
            0       // max error rate (%)
        ));

        // Medium scale SLAs
        slaConfigs.put("medium_scale", new SLAConfig(
            "Medium Scale SLA",
            1000,   // max cases per second
            100,    // max avg response time (ms)
            99.5,   // min availability (%)
            0.1     // max error rate (%)
        ));

        // Large scale SLAs
        slaConfigs.put("large_scale", new SLAConfig(
            "Large Scale SLA",
            10000,  // max cases per second
            200,    // max avg response time (ms)
            99.0,   // min availability (%)
            0.5     // max error rate (%)
        ));

        // Enterprise scale SLAs
        slaConfigs.put("enterprise_scale", new SLAConfig(
            "Enterprise Scale SLA",
            50000,  // max cases per second
            500,    // max avg response time (ms)
            95.0,   // min availability (%)
            1.0     // max error rate (%)
        ));
    }

    private void initializeScaleConfigs() {
        // Single instance scaling
        scaleConfigs.put("single_instance", new ScaleConfig(
            "Single Instance",
            1,
            100,
            new String[]{"single_threaded", "thread_pool"}
        ));

        // Medium scaling
        scaleConfigs.put("medium_scale", new ScaleConfig(
            "Medium Scale",
            100,
            1000,
            new String[]{"fixed_thread_pool", "virtual_threads"}
        ));

        // Large scaling
        scaleConfigs.put("large_scale", new ScaleConfig(
            "Large Scale",
            1000,
            10000,
            new String[]{"virtual_threads", "structured_concurrency"}
        ));

        // Enterprise scaling
        scaleConfigs.put("enterprise_scale", new ScaleConfig(
            "Enterprise Scale",
            10000,
            100000,
            new String[]{"structured_concurrency", "distributed"}
        ));
    }

    // ========== CONFIGURATION ACCESSORS ==========

    /**
     * Get test suite configuration
     */
    public TestSuiteConfig getTestSuiteConfig(String suiteName) {
        return testSuiteConfigs.get(suiteName);
    }

    /**
     * Get integration point configuration
     */
    public IntegrationPointConfig getIntegrationPointConfig(String pointName) {
        return integrationPointConfigs.get(pointName);
    }

    /**
     * Get SLA configuration for scale level
     */
    public SLAConfig getSLAConfig(String scaleLevel) {
        return slaConfigs.get(scaleLevel);
    }

    /**
     * Get scale configuration
     */
    public ScaleConfig getScaleConfig(String scaleLevel) {
        return scaleConfigs.get(scaleLevel);
    }

    /**
     * Get all test suite configurations
     */
    public Map<String, TestSuiteConfig> getAllTestSuiteConfigs() {
        return Collections.unmodifiableMap(testSuiteConfigs);
    }

    /**
     * Get all integration point configurations
     */
    public Map<String, IntegrationPointConfig> getAllIntegrationPointConfigs() {
        return Collections.unmodifiableMap(integrationPointConfigs);
    }

    /**
     * Get performance metric
     */
    public Long getPerformanceMetric(String metricName) {
        return performanceMetrics.get(metricName);
    }

    /**
     * Set performance metric
     */
    public void setPerformanceMetric(String metricName, Long value) {
        performanceMetrics.put(metricName, value);
    }

    /**
     * Increment test execution count
     */
    public void incrementTestExecution(String testName) {
        testExecutionCount.merge(testName, 1, Integer::sum);
    }

    /**
     * Get test execution count
     */
    public Integer getTestExecutionCount(String testName) {
        return testExecutionCount.getOrDefault(testName, 0);
    }

    /**
     * Add validation log entry
     */
    public void addValidationLog(String entry) {
        validationLog.add(entry);
    }

    /**
     * Get validation log
     */
    public List<String> getValidationLog() {
        return Collections.unmodifiableList(validationLog);
    }

    // ========== VALIDATION METHODS ==========

    /**
     * Validate that all test suites are properly configured
     */
    public boolean validateTestSuiteConfigurations() {
        boolean allValid = testSuiteConfigs.values().stream()
            .allMatch(TestSuiteConfig::isValid);

        if (allValid) {
            addValidationLog("All test suite configurations are valid");
        } else {
            addValidationLog("Some test suite configurations are invalid");
        }

        return allValid;
    }

    /**
     * Validate that all integration points are properly configured
     */
    public boolean validateIntegrationPointConfigurations() {
        boolean allValid = integrationPointConfigs.values().stream()
            .allMatch(IntegrationPointConfig::isValid);

        if (allValid) {
            addValidationLog("All integration point configurations are valid");
        } else {
            addValidationLog("Some integration point configurations are invalid");
        }

        return allValid;
    }

    /**
     * Validate SLA compliance for scale level
     */
    public boolean validateSLACompliance(String scaleLevel, double availability, double errorRate) {
        SLAConfig sla = getSLAConfig(scaleLevel);
        if (sla == null) {
            addValidationLog("No SLA configuration found for scale level: " + scaleLevel);
            return false;
        }

        boolean compliant = sla.getMinAvailability() <= availability &&
                          sla.getMaxErrorRate() >= errorRate;

        if (compliant) {
            addValidationLog(String.format("SLA compliance validated for %s: availability=%.2f%%, errorRate=%.2f%%",
                scaleLevel, availability * 100, errorRate * 100));
        } else {
            addValidationLog(String.format("SLA violation for %s: availability=%.2f%% < %.2f%%, errorRate=%.2f%% > %.2f%%",
                scaleLevel, availability * 100, sla.getMinAvailability() * 100,
                errorRate * 100, sla.getMaxErrorRate() * 100));
        }

        return compliant;
    }

    /**
     * Generate comprehensive configuration report
     */
    public String generateConfigurationReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== SCALABILITY INTEGRATION CONFIGURATION REPORT ===\n");
        report.append("Generated: ").append(new Date()).append("\n\n");

        // Test suite configurations
        report.append("Test Suite Configurations:\n");
        testSuiteConfigs.forEach((name, config) -> {
            report.append(String.format("  %s: %s\n", name, config));
        });

        // Integration point configurations
        report.append("\nIntegration Point Configurations:\n");
        integrationPointConfigs.forEach((name, config) -> {
            report.append(String.format("  %s: %s\n", name, config));
        });

        // SLA configurations
        report.append("\nSLA Configurations:\n");
        slaConfigs.forEach((name, config) -> {
            report.append(String.format("  %s: %s\n", name, config));
        });

        // Scale configurations
        report.append("\nScale Configurations:\n");
        scaleConfigs.forEach((name, config) -> {
            report.append(String.format("  %s: %s\n", name, config));
        });

        // Performance metrics
        report.append("\nPerformance Metrics:\n");
        performanceMetrics.forEach((name, value) -> {
            report.append(String.format("  %s: %d ms\n", name, value));
        });

        // Test execution counts
        report.append("\nTest Execution Counts:\n");
        testExecutionCount.forEach((name, count) -> {
            report.append(String.format("  %s: %d executions\n", name, count));
        });

        // Validation log
        report.append("\nValidation Log:\n");
        validationLog.forEach(entry -> {
            report.append("  - ").append(entry).append("\n");
        });

        return report.toString();
    }

    // ========== CONFIGURATION CLASSES ==========

    /**
     * Test Suite Configuration
     */
    public static class TestSuiteConfig {
        private final String name;
        private final List<String> testMethods;
        private final Duration timeout;
        private final boolean enabled;
        private boolean isValid;

        public TestSuiteConfig(String name, List<String> testMethods, Duration timeout, boolean enabled) {
            this.name = name;
            this.testMethods = new ArrayList<>(testMethods);
            this.timeout = timeout;
            this.enabled = enabled;
            this.isValid = validate();
        }

        private boolean validate() {
            return name != null && !name.isEmpty() &&
                   testMethods != null && !testMethods.isEmpty() &&
                   timeout != null && timeout.toMillis() > 0;
        }

        public String getName() { return name; }
        public List<String> getTestMethods() { return Collections.unmodifiableList(testMethods); }
        public Duration getTimeout() { return timeout; }
        public boolean isEnabled() { return enabled; }
        public boolean isValid() { return isValid; }

        @Override
        public String toString() {
            return String.format("%s [methods=%d, timeout=%d min, enabled=%b]",
                name, testMethods.size(), timeout.toMinutes(), enabled);
        }
    }

    /**
     * Integration Point Configuration
     */
    public static class IntegrationPointConfig {
        private final String name;
        private final String description;
        private final double minimumSuccessRate;
        private final List<String> involvedComponents;
        private boolean isValid;

        public IntegrationPointConfig(String name, String description, double minimumSuccessRate,
                                    List<String> involvedComponents) {
            this.name = name;
            this.description = description;
            this.minimumSuccessRate = minimumSuccessRate;
            this.involvedComponents = new ArrayList<>(involvedComponents);
            this.isValid = validate();
        }

        private boolean validate() {
            return name != null && !name.isEmpty() &&
                   description != null && !description.isEmpty() &&
                   minimumSuccessRate >= 0.0 && minimumSuccessRate <= 100.0 &&
                   involvedComponents != null && !involvedComponents.isEmpty();
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public double getMinimumSuccessRate() { return minimumSuccessRate; }
        public List<String> getInvolvedComponents() { return Collections.unmodifiableList(involvedComponents); }
        public boolean isValid() { return isValid; }

        @Override
        public String toString() {
            return String.format("%s [rate=%.1f%%, components=%d]",
                name, minimumSuccessRate, involvedComponents.size());
        }
    }

    /**
     * SLA Configuration
     */
    public static class SLAConfig {
        private final String name;
        private final int maxCasesPerSecond;
        private final int maxAvgResponseTimeMs;
        private final double minAvailability;
        private final double maxErrorRate;

        public SLAConfig(String name, int maxCasesPerSecond, int maxAvgResponseTimeMs,
                       double minAvailability, double maxErrorRate) {
            this.name = name;
            this.maxCasesPerSecond = maxCasesPerSecond;
            this.maxAvgResponseTimeMs = maxAvgResponseTimeMs;
            this.minAvailability = minAvailability;
            this.maxErrorRate = maxErrorRate;
        }

        public String getName() { return name; }
        public int getMaxCasesPerSecond() { return maxCasesPerSecond; }
        public int getMaxAvgResponseTimeMs() { return maxAvgResponseTimeMs; }
        public double getMinAvailability() { return minAvailability; }
        public double getMaxErrorRate() { return maxErrorRate; }

        @Override
        public String toString() {
            return String.format("%s [throughput=%d/s, latency=%dms, availability=%.1f%%, errorRate=%.1f%%]",
                name, maxCasesPerSecond, maxAvgResponseTimeMs, minAvailability, maxErrorRate);
        }
    }

    /**
     * Scale Configuration
     */
    public static class ScaleConfig {
        private final String name;
        private final int minCases;
        private final int maxCases;
        private final List<String> supportedConcurrencyModels;

        public ScaleConfig(String name, int minCases, int maxCases, List<String> supportedConcurrencyModels) {
            this.name = name;
            this.minCases = minCases;
            this.maxCases = maxCases;
            this.supportedConcurrencyModels = new ArrayList<>(supportedConcurrencyModels);
        }

        public String getName() { return name; }
        public int getMinCases() { return minCases; }
        public int getMaxCases() { return maxCases; }
        public List<String> getSupportedConcurrencyModels() { return Collections.unmodifiableList(supportedConcurrencyModels); }

        @Override
        public String toString() {
            return String.format("%s [%d-%d cases, models=%s]",
                name, minCases, maxCases, supportedConcurrencyModels);
        }
    }
}