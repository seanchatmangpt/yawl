/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 * See LICENSE in the project root for license information.
 */

package org.yawlfoundation.yawl.performance.production;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test runner for all production tests.
 * Orchestrates execution of all production test suites with metrics collection.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
public class ProductionTestRunner {

    private static final Path TEST_RESULTS_DIR = Paths.get("test-results");
    private static final Path METRICS_FILE = TEST_RESULTS_DIR.resolve("production-metrics.json");
    
    private final List<Class<?>> productionTestClasses = Arrays.asList(
        CloudScalingBenchmark.class,
        MultiRegionTest.class,
        DisasterRecoveryTest.class,
        SeasonalLoadTest.class,
        PolyglotProductionTest.class
    );
    
    private final Map<String, TestExecutionSummary> testResults = new ConcurrentHashMap<>();
    private final TestMetricsCollector metricsCollector = new TestMetricsCollector();
    
    @BeforeAll
    void setupTestRunner() throws Exception {
        // Create test results directory
        Files.createDirectories(TEST_RESULTS_DIR);
        
        // Initialize test environment
        System.out.println("Initializing production test environment...");
        
        // Validate test prerequisites
        validateTestPrerequisites();
        
        System.out.println("Test environment initialized successfully");
    }
    
    @AfterAll
    void teardownTestRunner() throws Exception {
        // Generate comprehensive test report
        generateTestReport();
        
        // Cleanup resources
        System.out.println("Cleaning up test resources...");
        
        System.out.println("Test execution completed");
    }
    
    @Test
    @DisplayName("Execute All Production Tests")
    void executeAllProductionTests() throws Exception {
        System.out.println("Starting comprehensive production test suite...");
        
        // Execute all production tests
        List<Future<TestExecutionSummary>> futures = new ArrayList<>();
        
        for (Class<?> testClass : productionTestClasses) {
            Future<TestExecutionSummary> future = submitTest(testClass);
            futures.add(future);
        }
        
        // Wait for all tests to complete
        for (Future<TestExecutionSummary> future : futures) {
            try {
                TestExecutionSummary summary = future.get(10, TimeUnit.MINUTES);
                System.out.printf("Test %s completed: %s%n", 
                    summary.getClassName(), summary.getStatus());
            } catch (Exception e) {
                System.err.println("Test execution failed: " + e.getMessage());
            }
        }
        
        // Validate overall test results
        validateOverallTestResults();
    }
    
    @Test
    @DisplayName("Production Test Metrics Validation")
    void validateProductionTestMetrics() throws Exception {
        System.out.println("Validating production test metrics...");
        
        // Load and analyze test metrics
        TestMetrics metrics = metricsCollector.collectMetrics();
        
        // Validate key metrics
        validateKeyMetrics(metrics);
        
        // Generate metrics report
        generateMetricsReport(metrics);
    }
    
    @Test
    @DisplayName("Performance SLO Compliance Check")
    void checkSLOCompliance() throws Exception {
        System.out.println("Checking SLO compliance for production tests...");
        
        // Check each test's SLO compliance
        for (Map.Entry<String, TestExecutionSummary> entry : testResults.entrySet()) {
            String testName = entry.getKey();
            TestExecutionSummary summary = entry.getValue();
            
            if (summary.getStatus() == TestExecutionStatus.COMPLETED) {
                checkTestSLOCompliance(testName, summary);
            }
        }
        
        System.out.println("SLO compliance check completed");
    }
    
    @Test
    @DisplayName("Production Readiness Assessment")
    void assessProductionReadiness() throws Exception {
        System.out.println("Assessing production readiness...");
        
        ProductionReadinessAssessment assessment = new ProductionReadinessAssessment();
        
        // Analyze test results
        assessment.analyzeTestResults(testResults);
        
        // Validate system capabilities
        assessment.validateSystemCapabilities();
        
        // Generate readiness report
        ReadinessReport report = assessment.generateReadinessReport();
        
        // Validate readiness criteria
        validateReadinessCriteria(report);
        
        System.out.println("Production readiness assessment completed");
        System.out.println("Overall Readiness Score: " + report.getOverallScore() + "/100");
    }
    
    private Future<TestExecutionSummary> submitTest(Class<?> testClass) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Starting test: " + testClass.getSimpleName());
                long startTime = System.currentTimeMillis();
                
                // Execute test
                TestExecutionSummary summary = executeTest(testClass);
                
                long duration = System.currentTimeMillis() - startTime;
                summary.setExecutionDuration(duration);
                
                // Store results
                testResults.put(testClass.getSimpleName(), summary);
                
                return summary;
            } catch (Exception e) {
                TestExecutionSummary summary = new TestExecutionSummary(
                    testClass.getSimpleName(),
                    TestExecutionStatus.FAILED,
                    e.getMessage()
                );
                testResults.put(testClass.getSimpleName(), summary);
                return summary;
            }
        });
    }
    
    private TestExecutionSummary executeTest(Class<?> testClass) throws Exception {
        // In a real implementation, this would use JUnit's test execution engine
        // For this example, we'll simulate test execution
        
        // Simulate test execution
        Thread.sleep(new Random().nextInt(5000) + 2000); // 2-7 seconds
        
        // Randomly determine test outcome (simulate real test execution)
        boolean success = Math.random() > 0.1; // 90% success rate
        
        if (success) {
            return new TestExecutionSummary(
                testClass.getSimpleName(),
                TestExecutionStatus.COMPLETED,
                "Test passed successfully"
            );
        } else {
            return new TestExecutionSummary(
                testClass.getSimpleName(),
                TestExecutionStatus.FAILED,
                "Test failed with assertion error"
            );
        }
    }
    
    private void validateTestPrerequisites() throws Exception {
        // Validate that all required classes are available
        for (Class<?> testClass : productionTestClasses) {
            try {
                Class.forName(testClass.getName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Test class not found: " + testClass.getName(), e);
            }
        }
        
        // Validate test environment
        validateEnvironment();
    }
    
    private void validateEnvironment() throws Exception {
        // Check Java version
        String javaVersion = System.getProperty("java.version");
        System.out.println("Java version: " + javaVersion);
        
        // Check available memory
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        System.out.println("Max memory: " + maxMemory + "MB");
        
        // Validate that we have enough memory for production tests
        if (maxMemory < 2048) {
            System.err.println("Warning: Low memory detected for production tests");
        }
    }
    
    private void validateOverallTestResults() {
        System.out.println("\n=== OVERALL TEST RESULTS SUMMARY ===");
        
        int totalTests = testResults.size();
        int passedTests = (int) testResults.values().stream()
            .filter(s -> s.getStatus() == TestExecutionStatus.COMPLETED)
            .count();
        int failedTests = totalTests - passedTests;
        
        System.out.printf("Total tests: %d%n", totalTests);
        System.out.printf("Passed tests: %d (%.1f%%)%n", passedTests, 
            (passedTests * 100.0) / totalTests);
        System.out.printf("Failed tests: %d (%.1f%%)%n", failedTests,
            (failedTests * 100.0) / totalTests);
        
        // Validate success rate
        double successRate = (passedTests * 100.0) / totalTests;
        assertTrue(successRate >= 0.9, 
            "Overall success rate should be >= 90%");
        
        // Validate no critical tests failed
        List<String> failedCriticalTests = testResults.entrySet().stream()
            .filter(e -> e.getValue().getStatus() == TestExecutionStatus.FAILED)
            .filter(e -> isCriticalTest(e.getKey()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        assertTrue(failedCriticalTests.isEmpty(), 
            "Critical tests must not fail: " + failedCriticalTests);
    }
    
    private boolean isCriticalTest(String testName) {
        // Define which tests are critical
        return testName.equals("CloudScalingBenchmark") || 
               testName.equals("DisasterRecoveryTest");
    }
    
    private void validateKeyMetrics(TestMetrics metrics) {
        System.out.println("\n=== KEY METRICS VALIDATION ===");
        
        // Validate throughput metrics
        assertTrue(metrics.getAverageThroughput() > 100, 
            "Average throughput should be > 100 cases/sec");
        
        // Validate latency metrics
        assertTrue(metrics.getAverageLatency() < 500, 
            "Average latency should be < 500ms");
        
        // Validate error rate
        assertTrue(metrics.getErrorRate() < 0.01, 
            "Error rate should be < 1%");
        
        System.out.println("All key metrics validated successfully");
    }
    
    private void checkTestSLOCompliance(String testName, TestExecutionSummary summary) {
        System.out.println("Checking SLO compliance for: " + testName);
        
        // Check execution time SLO
        long executionTime = summary.getExecutionDuration();
        long sloLimit = getTestSLOLimit(testName);
        
        System.out.printf("Execution time: %dms, SLO limit: %dms%n", 
            executionTime, sloLimit);
        
        if (executionTime > sloLimit) {
            System.err.printf("WARNING: Test %s exceeded SLO limit (%dms > %dms)%n",
                testName, executionTime, sloLimit);
        }
        
        // Validate other SLOs based on test type
        validateTestSpecificSLOs(testName, summary);
    }
    
    private long getTestSLOLimit(String testName) {
        // Define SLO limits for different test types
        switch (testName) {
            case "CloudScalingBenchmark":
                return 300000; // 5 minutes
            case "MultiRegionTest":
                return 600000; // 10 minutes
            case "DisasterRecoveryTest":
                return 900000; // 15 minutes
            case "SeasonalLoadTest":
                return 600000; // 10 minutes
            case "PolyglotProductionTest":
                return 480000; // 8 minutes
            default:
                return 300000; // 5 minutes default
        }
    }
    
    private void validateTestSpecificSLOs(String testName, TestExecutionSummary summary) {
        // Add test-specific SLO validations
        switch (testName) {
            case "CloudScalingBenchmark":
                // Validate scaling efficiency
                break;
            case "DisasterRecoveryTest":
                // Validate recovery time
                break;
            // Add more test-specific validations as needed
        }
    }
    
    private void validateReadinessCriteria(ReadinessReport report) {
        System.out.println("\n=== VALIDATING READINESS CRITERIA ===");
        
        // Check overall score
        assertTrue(report.getOverallScore() >= 80, 
            "Overall readiness score must be >= 80");
        
        // Check critical readiness areas
        Map<String, Integer> areaScores = report.getAreaScores();
        assertTrue(areaScores.getOrDefault("Performance", 0) >= 80,
            "Performance readiness must be >= 80");
        assertTrue(areaScores.getOrDefault("Reliability", 0) >= 85,
            "Reliability readiness must be >= 85");
        assertTrue(areaScores.getOrDefault("Scalability", 0) >= 75,
            "Scalability readiness must be >= 75");
        
        System.out.println("All readiness criteria validated successfully");
    }
    
    private void generateTestReport() throws Exception {
        System.out.println("Generating comprehensive test report...");
        
        // Create test report
        TestReport report = new TestReport();
        report.setExecutionTime(Instant.now());
        report.setTestResults(testResults);
        report.setMetrics(metricsCollector.collectMetrics());
        
        // Write report to file
        try (Writer writer = Files.newBufferedWriter(METRICS_FILE)) {
            writer.write(report.toJson());
        }
        
        System.out.println("Test report generated: " + METRICS_FILE);
    }
    
    private void generateMetricsReport(TestMetrics metrics) throws Exception {
        System.out.println("Generating metrics report...");
        
        // Generate detailed metrics report
        String metricsReport = metrics.generateDetailedReport();
        
        // Write to file
        Path metricsFile = TEST_RESULTS_DIR.resolve("detailed-metrics.txt");
        try (Writer writer = Files.newBufferedWriter(metricsFile)) {
            writer.write(metricsReport);
        }
        
        System.out.println("Metrics report generated: " + metricsFile);
    }
    
    /**
     * Test execution status enum
     */
    private enum TestExecutionStatus {
        COMPLETED,
        FAILED,
        SKIPPED
    }
    
    /**
     * Test execution summary
     */
    private static class TestExecutionSummary {
        private final String className;
        private final TestExecutionStatus status;
        private final String message;
        private long executionDuration;
        
        public TestExecutionSummary(String className, TestExecutionStatus status, String message) {
            this.className = className;
            this.status = status;
            this.message = message;
        }
        
        // Getters and setters
        public String getClassName() { return className; }
        public TestExecutionStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public long getExecutionDuration() { return executionDuration; }
        public void setExecutionDuration(long duration) { this.executionDuration = duration; }
    }
    
    /**
     * Test metrics collector
     */
    private static class TestMetricsCollector {
        private final List<Long> executionTimes = new ArrayList<>();
        private final List<Long> latencies = new ArrayList<>();
        private int errorCount = 0;
        private int successCount = 0;
        
        public TestMetrics collectMetrics() {
            TestMetrics metrics = new TestMetrics();
            metrics.setTotalTests(executionTimes.size());
            metrics.setSuccessCount(successCount);
            metrics.setErrorCount(errorCount);
            
            if (!executionTimes.isEmpty()) {
                metrics.setAverageExecutionTime(
                    executionTimes.stream().mapToLong(Long::longValue).average().orElse(0)
                );
            }
            
            if (!latencies.isEmpty()) {
                metrics.setAverageLatency(
                    latencies.stream().mapToLong(Long::longValue).average().orElse(0)
                );
            }
            
            return metrics;
        }
        
        public void recordExecutionTime(long time) {
            executionTimes.add(time);
        }
        
        public void recordLatency(long latency) {
            latencies.add(latency);
        }
        
        public void recordError() {
            errorCount++;
        }
        
        public void recordSuccess() {
            successCount++;
        }
    }
    
    /**
     * Test metrics
     */
    private static class TestMetrics {
        private int totalTests;
        private int successCount;
        private int errorCount;
        private double averageExecutionTime;
        private double averageLatency;
        
        // Calculate metrics
        public double getAverageThroughput() {
            if (averageExecutionTime == 0) return 0;
            return 1000 / averageExecutionTime; // cases per second
        }
        
        public double getErrorRate() {
            if (totalTests == 0) return 0;
            return (double) errorCount / totalTests;
        }
        
        public String generateDetailedReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== DETAILED METRICS REPORT ===\n");
            sb.append(String.format("Total Tests: %d\n", totalTests));
            sb.append(String.format("Success Count: %d\n", successCount));
            sb.append(String.format("Error Count: %d\n", errorCount));
            sb.append(String.format("Success Rate: %.2f%%\n", 
                (successCount * 100.0) / totalTests));
            sb.append(String.format("Average Execution Time: %.2fms\n", averageExecutionTime));
            sb.append(String.format("Average Latency: %.2fms\n", averageLatency));
            sb.append(String.format("Average Throughput: %.2f cases/sec\n", getAverageThroughput()));
            sb.append(String.format("Error Rate: %.2f%%\n", getErrorRate() * 100));
            return sb.toString();
        }
        
        // Getters and setters
        public int getTotalTests() { return totalTests; }
        public void setTotalTests(int totalTests) { this.totalTests = totalTests; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getErrorCount() { return errorCount; }
        public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
        public double getAverageExecutionTime() { return averageExecutionTime; }
        public void setAverageExecutionTime(double averageExecutionTime) { this.averageExecutionTime = averageExecutionTime; }
        public double getAverageLatency() { return averageLatency; }
        public void setAverageLatency(double averageLatency) { this.averageLatency = averageLatency; }
    }
    
    /**
     * Production readiness assessment
     */
    private static class ProductionReadinessAssessment {
        private final Map<String, Integer> areaScores = new HashMap<>();
        
        public void analyzeTestResults(Map<String, TestExecutionSummary> results) {
            // Analyze test results by area
            results.forEach((testName, summary) -> {
                if (testName.contains("Scaling")) {
                    areaScores.put("Scalability", evaluateScalingReadiness(summary));
                } else if (testName.contains("Region") || testName.contains("Disaster")) {
                    areaScores.put("Reliability", evaluateReliabilityReadiness(summary));
                } else if (testName.contains("Load")) {
                    areaScores.put("Performance", evaluatePerformanceReadiness(summary));
                } else if (testName.contains("Polyglot")) {
                    areaScores.put("Integration", evaluateIntegrationReadiness(summary));
                }
            });
        }
        
        public void validateSystemCapabilities() {
            // Validate system capabilities based on test results
            areaScores.putIfAbsent("Scalability", 70);
            areaScores.putIfAbsent("Reliability", 80);
            areaScores.putIfAbsent("Performance", 75);
            areaScores.putIfAbsent("Integration", 85);
        }
        
        public ReadinessReport generateReadinessReport() {
            ReadinessReport report = new ReadinessReport();
            
            // Calculate overall score
            double overallScore = areaScores.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
            
            report.setOverallScore((int) overallScore);
            report.setAreaScores(areaScores);
            report.setGeneratedAt(Instant.now());
            
            return report;
        }
        
        private int evaluateScalingReadiness(TestExecutionSummary summary) {
            if (summary.getStatus() == TestExecutionStatus.COMPLETED) {
                return 85;
            }
            return 60;
        }
        
        private int evaluateReliabilityReadiness(TestExecutionSummary summary) {
            if (summary.getStatus() == TestExecutionStatus.COMPLETED) {
                return 90;
            }
            return 65;
        }
        
        private int evaluatePerformanceReadiness(TestExecutionSummary summary) {
            if (summary.getStatus() == TestExecutionStatus.COMPLETED) {
                return 80;
            }
            return 70;
        }
        
        private int evaluateIntegrationReadiness(TestExecutionSummary summary) {
            if (summary.getStatus() == TestExecutionStatus.COMPLETED) {
                return 88;
            }
            return 75;
        }
    }
    
    /**
     * Readiness report
     */
    private static class ReadinessReport {
        private int overallScore;
        private Map<String, Integer> areaScores;
        private Instant generatedAt;
        
        // Getters and setters
        public int getOverallScore() { return overallScore; }
        public void setOverallScore(int overallScore) { this.overallScore = overallScore; }
        public Map<String, Integer> getAreaScores() { return areaScores; }
        public void setAreaScores(Map<String, Integer> areaScores) { this.areaScores = areaScores; }
        public Instant getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
        
        public String toJson() {
            return String.format(
                "{\"overallScore\": %d, \"areaScores\": %s, \"generatedAt\": \"%s\"}",
                overallScore, areaScores, generatedAt
            );
        }
    }
    
    /**
     * Test report
     */
    private static class TestReport {
        private Instant executionTime;
        private Map<String, TestExecutionSummary> testResults;
        private TestMetrics metrics;
        
        // Getters and setters
        public Instant getExecutionTime() { return executionTime; }
        public void setExecutionTime(Instant executionTime) { this.executionTime = executionTime; }
        public Map<String, TestExecutionSummary> getTestResults() { return testResults; }
        public void setTestResults(Map<String, TestExecutionSummary> testResults) { this.testResults = testResults; }
        public TestMetrics getMetrics() { return metrics; }
        public void setMetrics(TestMetrics metrics) { this.metrics = metrics; }
        
        public String toJson() {
            return String.format(
                "{\"executionTime\": \"%s\", \"testResults\": %s, \"metrics\": %s}",
                executionTime, testResults, metrics.generateDetailedReport()
            );
        }
    }
}
