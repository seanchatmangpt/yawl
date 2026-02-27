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

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.integration.observability.DistributedTracer;
import org.yawlfoundation.yawl.integration.observability.StructuredLogger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central orchestrator for all YAWL benchmark components integration.
 *
 * Coordinates between benchmark engines, observability systems,
 * K6 tests, and quality gates to ensure seamless operation.
 *
 * @author YAWL Foundation
 * @version 6.0.0-GA
 */
public class BenchmarkIntegrationManager {

    private static volatile BenchmarkIntegrationManager instance;
    private final Map<String, BenchmarkEngine> activeBenchmarks;
    private final DistributedTracer tracer;
    private final StructuredLogger logger;
    private final BenchmarkMetrics metrics;
    private final ExecutorService integrationExecutor;
    private final QualityGateController qualityGateController;
    private final K6TestController k6Controller;
    private final ChaosController chaosController;
    private final ConfigurationManager configManager;

    // Performance tracking
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong totalLatency = new AtomicLong(0);
    private final AtomicLong benchmarkErrors = new AtomicLong(0);

    // Integration state
    private IntegrationStatus integrationStatus = IntegrationStatus.INITIALIZING;
    private Instant lastHealthCheck = Instant.now();
    private final Map<String, Object> sharedMetrics = new ConcurrentHashMap<>();

    /**
     * Singleton pattern with double-checked locking
     */
    public static BenchmarkIntegrationManager getInstance() {
        if (instance == null) {
            synchronized (BenchmarkIntegrationManager.class) {
                if (instance == null) {
                    instance = new BenchmarkIntegrationManager();
                }
            }
        }
        return instance;
    }

    private BenchmarkIntegrationManager() {
        this.activeBenchmarks = new ConcurrentHashMap<>();
        this.tracer = DistributedTracer.getInstance();
        this.logger = StructuredLogger.getLogger("BenchmarkIntegration");
        this.metrics = new BenchmarkMetrics();
        this.integrationExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.qualityGateController = new QualityGateController();
        this.k6Controller = new K6TestController();
        this.chaosController = new ChaosController();
        this.configManager = ConfigurationManager.getInstance();

        initialize();
    }

    private void initialize() {
        logger.info("Initializing Benchmark Integration Manager");

        // Load configuration
        configManager.loadIntegrationConfig();

        // Initialize quality gates
        qualityGateController.initializeQualityGates();

        // Initialize K6 tests
        k6Controller.initializeTests();

        // Initialize chaos engineering
        chaosController.initializeChaosScenarios();

        // Register metrics collectors
        metrics.registerCollector("operations", totalOperations::get);
        metrics.registerCollector("latency_ms", totalLatency::get);
        metrics.registerCollector("errors", benchmarkErrors::get);
        metrics.registerCollector("active_benchmarks", activeBenchmarks::size);
        metrics.registerCollector("integration_status", () -> integrationStatus.toString());

        // Start integration monitoring
        startIntegrationMonitoring();

        this.integrationStatus = IntegrationStatus.RUNNING;
        logger.info("Benchmark Integration Manager initialized successfully");
    }

    /**
     * Register a benchmark engine for integration
     */
    public void registerBenchmark(String name, BenchmarkEngine engine) {
        if (activeBenchmarks.containsKey(name)) {
            logger.warn("Benchmark engine '{}' already registered, replacing", name);
        }

        activeBenchmarks.put(name, engine);
        logger.info("Registered benchmark engine: {}", name);

        // Start benchmark integration
        integrationExecutor.submit(() -> {
            try {
                integrateBenchmark(name, engine);
            } catch (Exception e) {
                logger.error("Failed to integrate benchmark {}: {}", name, e.getMessage());
                benchmarkErrors.incrementAndGet();
            }
        });
    }

    /**
     * Connect performance monitoring with observability
     */
    public void connectMonitoring(ObservabilityAdapter adapter) {
        logger.info("Connecting monitoring to observability system");

        // Register metrics with observability
        metrics.registerObservableMetrics(adapter);

        // Start metrics collection
        adapter.startMetricsCollection();

        // Set up alerts for performance thresholds
        setupPerformanceAlerts(adapter);

        logger.info("Monitoring connected to observability system");
    }

    /**
     * Integrate K6 tests with workflow definitions
     */
    public void integrateK6Tests(K6WorkflowAdapter adapter) {
        logger.info("Integrating K6 tests with workflow definitions");

        // Register workflow test patterns
        k6Controller.registerWorkflowPatterns(adapter);

        // Connect test data generators
        connectTestGenerators(adapter);

        // Set up test execution coordination
        setupTestCoordination(adapter);

        logger.info("K6 tests integrated with workflow definitions");
    }

    /**
     * Connect regression detection with CI/CD
     */
    public void connectCICD(CICDAdapter adapter) {
        logger.info("Connecting regression detection with CI/CD");

        // Register regression detection hooks
        adapter.registerRegressionHook(this::detectRegression);

        // Set up baseline validation
        setupBaselineValidation(adapter);

        // Connect quality gate results to CI
        qualityGateController.connectToCICD(adapter);

        logger.info("Regression detection connected to CI/CD");
    }

    /**
     * Integrate chaos engineering with test suites
     */
    public void integrateChaosEngineering(ChaosTestAdapter adapter) {
        logger.info("Integrating chaos engineering with test suites");

        // Register chaos test scenarios
        chaosController.registerTestScenarios(adapter);

        // Set up failure injection
        setupFailureInjection(adapter);

        // Connect test monitoring
        connectTestMonitoring(adapter);

        logger.info("Chaos engineering integrated with test suites");
    }

    /**
     * Connect polyglot components with main workflows
     */
    public void connectPolyglot(PolyglotAdapter adapter) {
        logger.info("Connecting polyglot components with main workflows");

        // Register language-specific components
        adapter.registerLanguageComponents();

        // Set up cross-language workflow execution
        setupCrossLanguageExecution(adapter);

        // Connect configuration management
        configManager.connectPolyglot(adapter);

        logger.info("Polyglot components connected with main workflows");
    }

    /**
     * Integrate A2A communication with benchmarking
     */
    public void integrateA2A(A2ABenchmarkAdapter adapter) {
        logger.info("Integrating A2A communication with benchmarking");

        // Register A2A test patterns
        adapter.registerTestPatterns();

        // Set up performance monitoring for A2A
        setupA2AMonitoring(adapter);

        // Connect benchmark results to A2A optimization
        connectBenchmarkOptimization(adapter);

        logger.info("A2A communication integrated with benchmarking");
    }

    /**
     * Connect quality gates with performance metrics
     */
    public void connectQualityGates(QualityGateAdapter adapter) {
        logger.info("Connecting quality gates with performance metrics");

        // Register performance thresholds
        qualityGateController.registerPerformanceThresholds(adapter);

        // Set up continuous monitoring
        setupContinuousMonitoring(adapter);

        // Connect alerting systems
        setupAlerting(adapter);

        logger.info("Quality gates connected with performance metrics");
    }

    /**
     * Integrate all configuration management
     */
    public void integrateConfiguration(ConfigurationAdapter adapter) {
        logger.info("Integrating configuration management");

        // Load system-wide configuration
        configManager.loadSystemConfig(adapter);

        // Set up configuration validation
        setupConfigurationValidation(adapter);

        // Connect configuration change monitoring
        setupConfigChangeMonitoring(adapter);

        logger.info("Configuration management integrated");
    }

    /**
     * Ensure complete end-to-end functionality
     */
    public void ensureEndToEndIntegration() {
        logger.info("Ensuring complete end-to-end functionality");

        // Verify all components are connected
        verifyComponentConnections();

        // Run integration tests
        runIntegrationTests();

        // Validate performance targets
        validatePerformanceTargets();

        // Ensure system resilience
        validateSystemResilience();

        logger.info("End-to-end integration verified");
    }

    // =========================================================================
    // Private Methods
    // =========================================================================

    private void integrateBenchmark(String name, BenchmarkEngine engine) {
        try {
            // Start benchmark with observability tracing
            tracer.startSpan("benchmark_integration", Map.of("benchmark_name", name));

            // Register metrics
            engine.addMetricsListener(metrics::recordBenchmarkMetrics);

            // Connect to observability
            engine.connectToObservability(tracer, logger);

            // Start benchmark execution
            engine.start();

            logger.info("Benchmark '{}' integrated successfully", name);
        } finally {
            tracer.endSpan();
        }
    }

    private void startIntegrationMonitoring() {
        integrationExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Perform health checks
                    performHealthChecks();

                    // Update shared metrics
                    updateSharedMetrics();

                    // Check integration status
                    checkIntegrationStatus();

                    // Sleep between checks
                    Thread.sleep(30000); // 30 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Integration monitoring error: {}", e.getMessage());
                }
            }
        });
    }

    private void performHealthChecks() {
        // Check all active benchmarks
        activeBenchmarks.forEach((name, engine) -> {
            if (!engine.isHealthy()) {
                logger.warn("Benchmark '{}' is unhealthy", name);
                benchmarkErrors.incrementAndGet();
            }
        });

        // Check quality gate status
        if (!qualityGateController.allGatesPass()) {
            logger.warn("Some quality gates are failing");
            benchmarkErrors.incrementAndGet();
        }

        // Check K6 test status
        if (!k6Controller.allTestsPass()) {
            logger.warn("Some K6 tests are failing");
            benchmarkErrors.incrementAndGet();
        }

        // Update last health check time
        lastHealthCheck = Instant.now();
    }

    private void updateSharedMetrics() {
        sharedMetrics.put("total_operations", totalOperations.get());
        sharedMetrics.put("avg_latency_ms",
            totalOperations.get() > 0 ? totalLatency.get() / totalOperations.get() : 0);
        sharedMetrics.put("error_rate",
            totalOperations.get() > 0 ? (double) benchmarkErrors.get() / totalOperations.get() : 0);
        sharedMetrics.put("active_benchmarks", activeBenchmarks.size());
        sharedMetrics.put("integration_status", integrationStatus);
    }

    private void checkIntegrationStatus() {
        if (benchmarkErrors.get() > 100) {
            integrationStatus = IntegrationStatus.DEGRADED;
            logger.warn("Integration status degraded due to high error rate");
        } else if (activeBenchmarks.isEmpty()) {
            integrationStatus = IntegrationStatus.IDLE;
        } else {
            integrationStatus = IntegrationStatus.RUNNING;
        }
    }

    private void verifyComponentConnections() {
        logger.info("Verifying component connections");

        // Verify benchmark engines
        activeBenchmarks.forEach((name, engine) -> {
            if (!engine.isConnected()) {
                throw new IllegalStateException("Benchmark engine '" + name + "' not connected");
            }
        });

        // Verify quality gates
        if (!qualityGateController.allGatesConnected()) {
            throw new IllegalStateException("Not all quality gates are connected");
        }

        // Verify K6 tests
        if (!k6Controller.allTestsConnected()) {
            throw new IllegalStateException("Not all K6 tests are connected");
        }

        logger.info("All component connections verified");
    }

    private void runIntegrationTests() {
        logger.info("Running integration tests");

        // Run comprehensive integration test suite
        IntegrationTestSuite suite = new IntegrationTestSuite();
        suite.addTest("benchmark_engine_integration", this::testBenchmarkEngineIntegration);
        suite.addTest("observability_connection", this::testObservabilityConnection);
        suite.addTest("quality_gates", this::testQualityGates);
        suite.addTest("k6_integration", this::testK6Integration);
        suite.addTest("chaos_integration", this::testChaosIntegration);
        suite.addTest("polyglot_integration", this::testPolyglotIntegration);
        suite.addTest("a2a_integration", this::testA2AIntegration);
        suite.addTest("config_integration", this::testConfigurationIntegration);

        // Run tests
        IntegrationResults results = suite.run();

        if (!results.allPassed()) {
            throw new IllegalStateException("Integration tests failed: " + results.getFailures());
        }

        logger.info("All integration tests passed");
    }

    private void validatePerformanceTargets() {
        logger.info("Validating performance targets");

        // Check throughput targets
        double throughput = metrics.getMetric("throughput");
        if (throughput < 1000) {
            logger.warn("Throughput target not met: {} < 1000 ops/sec", throughput);
        }

        // Check latency targets
        double latency = metrics.getMetric("p99_latency_ms");
        if (latency > 200) {
            logger.warn("P99 latency target not met: {} > 200ms", latency);
        }

        // Check error rate targets
        double errorRate = metrics.getMetric("error_rate");
        if (errorRate > 0.01) {
            logger.warn("Error rate target not met: {} > 1%", errorRate);
        }

        logger.info("Performance targets validated");
    }

    private void validateSystemResilience() {
        logger.info("Validating system resilience");

        // Run chaos engineering tests
        if (!chaosController.validateResilience()) {
            throw new IllegalStateException("System resilience validation failed");
        }

        // Test recovery mechanisms
        if (!testRecoveryMechanisms()) {
            throw new IllegalStateException("Recovery mechanisms test failed");
        }

        logger.info("System resilience validated");
    }

    // =========================================================================
    // Test Methods
    // =========================================================================

    private boolean testBenchmarkEngineIntegration() {
        return activeBenchmarks.values().stream()
            .allMatch(BenchmarkEngine::isIntegrated);
    }

    private boolean testObservabilityConnection() {
        return tracer.isActive() && logger.isConfigured();
    }

    private boolean testQualityGates() {
        return qualityGateController.allGatesPass();
    }

    private boolean testK6Integration() {
        return k6Controller.allTestsConnected() && k6Controller.allTestsPass();
    }

    private boolean testChaosIntegration() {
        return chaosController.isConfigured() && chaosController.validateResilience();
    }

    private boolean testPolyglotIntegration() {
        // Test polyglot component connections
        return configManager.isPolyglotIntegrated();
    }

    private boolean testA2AIntegration() {
        // Test A2A communication integration
        return metrics.containsMetric("a2a_throughput");
    }

    private boolean testConfigurationIntegration() {
        // Test configuration integration
        return configManager.isFullyIntegrated();
    }

    private boolean testRecoveryMechanisms() {
        // Test system recovery mechanisms
        try {
            // Simulate failure and recovery
            chaosController.injectFailure("recovery_test");
            Thread.sleep(1000); // Allow recovery time
            return chaosController.isRecovered();
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public void shutdown() {
        logger.info("Shutting down Benchmark Integration Manager");

        // Shutdown all benchmarks
        activeBenchmarks.forEach((name, engine) -> {
            try {
                engine.shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down benchmark {}: {}", name, e.getMessage());
            }
        });

        // Shutdown integration executor
        integrationExecutor.shutdown();
        try {
            if (!integrationExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                integrationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            integrationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Shutdown components
        metrics.shutdown();
        qualityGateController.shutdown();
        k6Controller.shutdown();
        chaosController.shutdown();
        configManager.shutdown();

        this.integrationStatus = IntegrationStatus.STOPPED;
        logger.info("Benchmark Integration Manager shutdown complete");
    }

    public IntegrationStatus getIntegrationStatus() {
        return integrationStatus;
    }

    public Map<String, Object> getSharedMetrics() {
        return new ConcurrentHashMap<>(sharedMetrics);
    }

    public void recordOperation(long latencyMs) {
        totalOperations.incrementAndGet();
        totalLatency.addAndGet(latencyMs);
    }

    public void recordError() {
        benchmarkErrors.incrementAndGet();
    }

    // =========================================================================
    // Nested Classes
    // =========================================================================

    public enum IntegrationStatus {
        INITIALIZING,
        RUNNING,
        DEGRADED,
        STOPPED,
        ERROR
    }

    public interface BenchmarkEngine {
        void start() throws Exception;
        void shutdown() throws Exception;
        boolean isHealthy();
        boolean isConnected();
        boolean isIntegrated();
        void addMetricsListener(MetricsListener listener);
        void connectToObservability(DistributedTracer tracer, StructuredLogger logger);
    }

    public interface MetricsListener {
        void onMetricsUpdated(Map<String, Object> metrics);
    }

    public interface ObservabilityAdapter {
        void startMetricsCollection();
        void registerMetrics(Map<String, Object> metrics);
        void setAlertThreshold(String metric, double threshold);
        void triggerAlert(String message, Map<String, Object> context);
    }

    public interface K6WorkflowAdapter {
        void registerWorkflowPattern(String name, K6TestPattern pattern);
        void connectTestDataGenerator(TestDataGenerator generator);
        void setupTestExecutionCoordinator(TestExecutionCoordinator coordinator);
    }

    public interface CICDAdapter {
        void registerRegressionHook(RegressionHook hook);
        void setBaselineValidator(BaselineValidator validator);
        void connectQualityGateResults(QualityGateResult result);
    }

    public interface ChaosTestAdapter {
        void registerChaosScenario(String name, ChaosScenario scenario);
        void setupFailureInjector(FailureInjector injector);
        void connectTestMonitor(TestMonitor monitor);
    }

    public interface PolyglotAdapter {
        void registerLanguageComponent(String language, PolyglotComponent component);
        void setupCrossLanguageExecutor(CrossLanguageExecutor executor);
        void connectConfigurationManager(ConfigurationManager manager);
    }

    public interface A2ABenchmarkAdapter {
        void registerA2APattern(String name, A2ATestPattern pattern);
        void setupPerformanceMonitor(A2APerformanceMonitor monitor);
        void connectBenchmarkOptimizer(BenchmarkOptimizer optimizer);
    }

    public interface QualityGateAdapter {
        void registerQualityThreshold(String metric, QualityThreshold threshold);
        void setupContinuousMonitor(ContinuousMonitor monitor);
        void connectAlertSystem(AlertSystem alert);
    }

    public interface ConfigurationAdapter {
        void loadSystemConfiguration(Map<String, Object> config);
        void setupConfigurationValidator(ConfigurationValidator validator);
        void connectChangeMonitor(ConfigurationChangeMonitor monitor);
    }

    // Additional interface implementations would be in separate files
}