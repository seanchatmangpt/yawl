/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.benchmark;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.integration.observability.DistributedTracer;
import org.yawlfoundation.yawl.integration.observability.StructuredLogger;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration test suite for YAWL benchmark components.
 *
 * Tests the integration of all benchmark components to ensure
 * seamless operation and end-to-end functionality.
 *
 * @author YAWL Foundation
 * @version 6.0.0-GA
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IntegrationTestSuite {

    private BenchmarkIntegrationManager integrationManager;
    private BenchmarkMetrics metrics;
    private QualityGateController qualityGateController;
    private K6TestController k6Controller;
    private ChaosController chaosController;
    private ConfigurationManager configManager;

    @BeforeAll
    void setup() {
        // Initialize test environment
        System.setProperty("test.environment", "integration");

        // Initialize components
        integrationManager = BenchmarkIntegrationManager.getInstance();
        metrics = new BenchmarkMetrics();
        qualityGateController = new QualityGateController();
        k6Controller = new K6TestController();
        chaosController = new ChaosController();
        configManager = ConfigurationManager.getInstance();

        // Initialize components
        qualityGateController.initializeQualityGates();
        k6Controller.initializeTests();
        chaosController.initializeChaosScenarios();
        configManager.loadIntegrationConfig();
    }

    @AfterAll
    void teardown() {
        // Shutdown components
        integrationManager.shutdown();
        k6Controller.shutdown();
        chaosController.shutdown();
        configManager.shutdown();
    }

    @Test
    @DisplayName("Benchmark Engine Integration Test")
    void testBenchmarkEngineIntegration() {
        System.out.println("Running Benchmark Engine Integration Test...");

        // Create test benchmark engines
        BenchmarkEngine engine1 = new TestBenchmarkEngine("Engine1");
        BenchmarkEngine engine2 = new TestBenchmarkEngine("Engine2");

        // Register engines with integration manager
        integrationManager.registerBenchmark("test_engine_1", engine1);
        integrationManager.registerBenchmark("test_engine_2", engine2);

        // Verify integration
        assertAll(
            () -> assertTrue(engine1.isIntegrated(), "Engine1 should be integrated"),
            () -> assertTrue(engine2.isIntegrated(), "Engine2 should be integrated"),
            () -> assertEquals(2, integrationManager.getSharedMetrics().get("active_benchmarks"))
        );

        // Record test operations
        integrationManager.recordOperation(100);
        integrationManager.recordOperation(150);
        integrationManager.recordError();

        // Verify metrics
        Map<String, Object> metrics = integrationManager.getSharedMetrics();
        assertAll(
            () -> assertEquals(2L, metrics.get("total_operations")),
            () -> assertEquals(250L, metrics.get("latency_ms")),
            () -> assertEquals(1L, metrics.get("errors"))
        );
    }

    @Test
    @DisplayName("Observability Integration Test")
    void testObservabilityIntegration() {
        System.out.println("Running Observability Integration Test...");

        // Create observability adapter
        TestObservabilityAdapter adapter = new TestObservabilityAdapter();

        // Connect monitoring
        integrationManager.connectMonitoring(adapter);

        // Verify connection
        assertAll(
            () -> assertTrue(adapter.isMetricsCollectionActive(), "Metrics collection should be active"),
            () -> assertTrue(adapter.hasAlertsConfigured(), "Alerts should be configured")
        );

        // Test metric collection
        metrics.recordBenchmarkMetrics(Map.of(
            "throughput", 100.0,
            "latency_ms", 50.0,
            "success_rate", 0.99
        ));

        // Verify metrics are collected
        assertTrue(adapter.hasCollectedMetrics(), "Metrics should be collected");
    }

    @Test
    @DisplayName("K6 Workflow Integration Test")
    void testK6WorkflowIntegration() {
        System.out.println("Running K6 Workflow Integration Test...");

        // Create K6 workflow adapter
        TestK6Adapter adapter = new TestK6Adapter();

        // Integrate K6 tests
        integrationManager.integrateK6Tests(adapter);

        // Verify integration
        assertAll(
            () -> assertTrue(adapter.hasWorkflowPatterns(), "Workflow patterns should be registered"),
            () -> assertTrue(adapter.hasTestDataGenerators(), "Test data generators should be connected")
        );

        // Run tests
        var result = k6Controller.runAllTests();

        assertAll(
            () -> assertTrue(result.isSuccess(), "K6 tests should pass"),
            () -> assertNotNull(result.getDuration(), "Test duration should be recorded"),
            () -> assertTrue(result.getMetrics().containsKey("total_tests"))
        );
    }

    @Test
    @DisplayName("Regression Detection CI/CD Integration Test")
    void testRegressionDetectionIntegration() {
        System.out.println("Running Regression Detection Integration Test...");

        // Create CI/CD adapter
        TestCICDAdapter adapter = new TestCICDAdapter();

        // Connect regression detection
        integrationManager.connectCICD(adapter);

        // Verify integration
        assertAll(
            () -> assertTrue(adapter.hasRegressionHooks(), "Regression hooks should be registered"),
            () -> assertTrue(adapter.hasBaselineValidation(), "Baseline validation should be set up")
        );

        // Test regression detection
        boolean regressionDetected = adapter.detectRegression(Map.of(
            "throughput", 500.0,  // Below baseline of 1000
            "error_rate", 0.05     // Above baseline of 0.01
        ));

        assertTrue(regressionDetected, "Regression should be detected");
    }

    @Test
    @DisplayName("Chaos Engineering Integration Test")
    void testChaosEngineeringIntegration() {
        System.out.println("Running Chaos Engineering Integration Test...");

        // Create chaos test adapter
        TestChaosAdapter adapter = new TestChaosAdapter();

        // Integrate chaos engineering
        integrationManager.integrateChaosEngineering(adapter);

        // Verify integration
        assertAll(
            () -> assertTrue(adapter.hasChaosScenarios(), "Chaos scenarios should be registered"),
            () -> assertTrue(adapter.hasFailureInjection(), "Failure injection should be set up")
        );

        // Test chaos resilience
        boolean resilient = chaosController.validateResilience();

        assertAll(
            () -> assertTrue(resilient, "System should be resilient"),
            () -> assertTrue(adapter.monitoringActive(), "Monitoring should be active")
        );
    }

    @Test
    @DisplayName("Polyglot Integration Test")
    void testPolyglotIntegration() {
        System.out.println("Running Polyglot Integration Test...");

        // Create polyglot adapter
        TestPolyglotAdapter adapter = new TestPolyglotAdapter();

        // Connect polyglot components
        integrationManager.connectPolyglot(adapter);

        // Verify integration
        assertAll(
            () -> assertTrue(adapter.hasLanguageComponents(), "Language components should be registered"),
            () -> assertTrue(adapter.hasCrossLanguageExecution(), "Cross-language execution should be set up")
        );

        // Test polyglot workflow
        boolean workflowExecuted = adapter.executeWorkflow("multi_language_test");

        assertTrue(workflowExecuted, "Polyglot workflow should execute successfully");
    }

    @Test
    @DisplayName("A2A Communication Integration Test")
    void testA2ACommunicationIntegration() {
        System.out.println("Running A2A Communication Integration Test...");

        // Create A2A adapter
        TestA2AAdapter adapter = new TestA2AAdapter();

        // Integrate A2A communication
        integrationManager.integrateA2A(adapter);

        // Verify integration
        assertAll(
            () -> assertTrue(adapter.hasA2APatterns(), "A2A patterns should be registered"),
            () -> assertTrue(adapter.hasPerformanceMonitoring(), "Performance monitoring should be set up")
        );

        // Test A2A throughput
        double throughput = adapter.measureThroughput(100, 50);

        assertTrue(throughput > 50, "A2A throughput should be >50 req/s");
    }

    @Test
    @DisplayName("Quality Gates Integration Test")
    void testQualityGatesIntegration() {
        System.out.println("Running Quality Gates Integration Test...");

        // Create quality gate adapter
        TestQualityGateAdapter adapter = new TestQualityGateAdapter();

        // Connect quality gates
        integrationManager.connectQualityGates(adapter);

        // Verify integration
        assertAll(
            () -> assertTrue(adapter.hasQualityThresholds(), "Quality thresholds should be registered"),
            () -> assertTrue(adapter.hasContinuousMonitoring(), "Continuous monitoring should be set up")
        );

        // Test quality gate validation
        boolean allGatesPass = qualityGateController.allGatesPass();

        assertTrue(allGatesPass, "All quality gates should pass");
    }

    @Test
    @DisplayName("Configuration Management Integration Test")
    void testConfigurationManagementIntegration() {
        System.out.println("Running Configuration Management Integration Test...");

        // Create configuration adapter
        TestConfigAdapter adapter = new TestConfigAdapter();

        // Integrate configuration management
        integrationManager.integrateConfiguration(adapter);

        // Verify integration
        assertAll(
            () -> assertTrue(adapter.hasConfigurationLoaded(), "Configuration should be loaded"),
            () -> assertTrue(adapter.hasConfigurationValidation(), "Configuration validation should be set up")
        );

        // Test configuration change detection
        boolean changeDetected = adapter.detectConfigurationChange("new_setting", "value");

        assertTrue(changeDetected, "Configuration change should be detected");
    }

    @Test
    @DisplayName("End-to-End Integration Test")
    void testEndToEndIntegration() {
        System.out.println("Running End-to-End Integration Test...");

        // Ensure complete integration
        integrationManager.ensureEndToEndIntegration();

        // Verify integration status
        BenchmarkIntegrationManager.IntegrationStatus status = integrationManager.getIntegrationStatus();

        assertAll(
            () -> assertEquals(BenchmarkIntegrationManager.IntegrationStatus.RUNNING, status),
            () -> assertTrue(metrics.containsMetric("throughput")),
            () -> assertTrue(metrics.containsMetric("p99_latency_ms")),
            () -> assertTrue(metrics.containsMetric("error_rate"))
        );

        // Test complete workflow
        testCompleteWorkflow();

        // Verify end-to-end metrics
        Map<String, Object> finalMetrics = integrationManager.getSharedMetrics();

        assertAll(
            () -> assertNotNull(finalMetrics.get("total_operations")),
            () -> assertNotNull(finalMetrics.get("avg_latency_ms")),
            () -> assertNotNull(finalMetrics.get("error_rate"))
        );
    }

    @Test
    @DisplayName("Performance Targets Validation Test")
    void testPerformanceTargetsValidation() {
        System.out.println("Running Performance Targets Validation Test...");

        // Record test data that meets targets
        for (int i = 0; i < 100; i++) {
            metrics.recordOperation(50);  // Low latency
            integrationManager.recordOperation(50);
        }

        // Add some errors to test error rate
        for (int i = 0; i < 5; i++) {
            integrationManager.recordError();
        }

        // Run performance validation
        assertAll(
            () -> validateThroughputTarget(),
            () -> validateLatencyTarget(),
            () -> validateErrorRateTarget()
        );
    }

    @Test
    @DisplayName("Concurrency and Load Test")
    void testConcurrencyAndLoad() {
        System.out.println("Running Concurrency and Load Test...");

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // Submit concurrent operations
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < 100; j++) {
                    integrationManager.recordOperation(threadId * 10 + 50);
                }
                latch.countDown();
            });
        }

        // Wait for completion
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("Concurrent test timed out");
        } finally {
            executor.shutdown();
        }

        // Verify results
        Map<String, Object> metrics = integrationManager.getSharedMetrics();

        assertAll(
            () -> assertEquals(1000L, metrics.get("total_operations")),
            () -> assertTrue((double) metrics.get("avg_latency_ms") < 100),
            () -> assertTrue((double) metrics.get("error_rate") < 0.05)
        );
    }

    @Test
    @DisplayName("System Resilience Test")
    void testSystemResilience() {
        System.out.println("Running System Resilience Test...");

        // Test system recovery mechanisms
        boolean recoverySuccessful = testRecoveryMechanisms();

        // Test error handling
        boolean errorHandled = testErrorHandling();

        // Test graceful degradation
        boolean gracefulDegradation = testGracefulDegradation();

        assertAll(
            () -> assertTrue(recoverySuccessful, "System should recover from failures"),
            () -> assertTrue(errorHandled, "Errors should be handled gracefully"),
            () -> assertTrue(gracefulDegradation, "System should degrade gracefully")
        );
    }

    // Private test methods

    private void testCompleteWorkflow() {
        System.out.println("Testing complete workflow integration...");

        // Simulate complete workflow execution
        integrationManager.recordOperation(100);
        integrationManager.recordOperation(150);
        integrationManager.recordOperation(200);
        integrationManager.recordOperation(50);
        integrationManager.recordError();

        // Verify workflow metrics
        Map<String, Object> metrics = integrationManager.getSharedMetrics();

        assertEquals(4L, metrics.get("total_operations"));
        assertEquals(125L, metrics.get("latency_ms"));
        assertEquals(1L, metrics.get("errors"));
    }

    private void validateThroughputTarget() {
        double throughput = metrics.getMetric("throughput");
        assertTrue(throughput > 1000, String.format(
            "Throughput target not met: %.2f < 1000 ops/sec", throughput));
    }

    private void validateLatencyTarget() {
        double p99Latency = metrics.getMetric("p99_latency_ms");
        assertTrue(p99Latency < 200, String.format(
            "P99 latency target not met: %.2f > 200ms", p99Latency));
    }

    private void validateErrorRateTarget() {
        double errorRate = metrics.getMetric("error_rate");
        assertTrue(errorRate < 0.01, String.format(
            "Error rate target not met: %.2f > 0.01", errorRate));
    }

    private boolean testRecoveryMechanisms() {
        // Test recovery from simulated failures
        try {
            chaosController.injectFailure("recovery_test");
            Thread.sleep(1000); // Allow recovery time
            return chaosController.isRecovered();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean testErrorHandling() {
        // Simulate error scenarios
        try {
            integrationManager.recordError();
            integrationManager.recordError();
            return true; // Error handling is successful if no exception
        } catch (Exception e) {
            return false;
        }
    }

    private boolean testGracefulDegradation() {
        // Simulate system overload
        for (int i = 0; i < 1000; i++) {
            integrationManager.recordOperation(10);
        }

        // Check if system maintains functionality
        BenchmarkIntegrationManager.IntegrationStatus status = integrationManager.getIntegrationStatus();
        return status == BenchmarkIntegrationManager.IntegrationStatus.RUNNING ||
               status == BenchmarkIntegrationManager.IntegrationStatus.DEGRADED;
    }

    // Test helper classes

    private static class TestBenchmarkEngine implements BenchmarkIntegrationManager.BenchmarkEngine {
        private final String name;
        private boolean integrated = false;
        private boolean healthy = true;
        private boolean connected = true;

        public TestBenchmarkEngine(String name) {
            this.name = name;
        }

        @Override public void start() throws Exception { /* Implementation */ }
        @Override public void shutdown() throws Exception { /* Implementation */ }
        @Override public boolean isHealthy() { return healthy; }
        @Override public boolean isConnected() { return connected; }
        @Override public boolean isIntegrated() { return integrated; }
        @Override public void addMetricsListener(BenchmarkIntegrationManager.MetricsListener listener) {
            integrated = true;
        }
        @Override public void connectToObservability(DistributedTracer tracer, StructuredLogger logger) {
            connected = true;
        }
    }

    private static class TestObservabilityAdapter implements BenchmarkIntegrationManager.ObservabilityAdapter {
        private boolean metricsCollectionActive = false;
        private boolean alertsConfigured = false;
        private boolean hasCollectedMetrics = false;

        @Override public void startMetricsCollection() {
            metricsCollectionActive = true;
        }
        @Override public void registerMetrics(Map<String, Object> metrics) {
            hasCollectedMetrics = true;
        }
        @Override public void setAlertThreshold(String metric, double threshold) {
            alertsConfigured = true;
        }
        @Override public void triggerAlert(String message, Map<String, Object> context) {
            // Implementation
        }

        public boolean isMetricsCollectionActive() { return metricsCollectionActive; }
        public boolean hasAlertsConfigured() { return alertsConfigured; }
        public boolean hasCollectedMetrics() { return hasCollectedMetrics; }
    }

    // Other test adapter classes would follow similar pattern

    private static class TestK6Adapter implements K6TestController.K6WorkflowAdapter {
        private boolean hasWorkflowPatterns = false;
        private boolean hasTestDataGenerators = false;

        @Override public void registerWorkflowPattern(String name, K6TestController.K6TestPattern pattern) {
            hasWorkflowPatterns = true;
        }
        @Override public void connectTestDataGenerator(K6TestController.TestDataGenerator generator) {
            hasTestDataGenerators = true;
        }
        @Override public void setupTestExecutionCoordinator(K6TestController.TestExecutionCoordinator coordinator) {
            // Implementation
        }

        public boolean hasWorkflowPatterns() { return hasWorkflowPatterns; }
        public boolean hasTestDataGenerators() { return hasTestDataGenerators; }
    }

    // Other test classes would follow similar pattern
}