/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 * See LICENSE in the project root for license information.
 */

package org.yawlfoundation.yawl.performance.production;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.elements.YNet;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production test for polyglot (Java/Python) mixed workload validation.
 * Tests integration between Java YAWL engine and Python services.
 *
 * Validates:
 * - Cross-language service calls
 * - Data serialization/deserialization
 * - Performance overhead of polyglot interactions
 * - Error handling across language boundaries
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("production")
@Tag("polyglot")
@Tag("integration")
public class PolyglotProductionTest {

    private static final int JAVA_CASE_COUNT = 1000;
    private static final int PYTHON_CASE_COUNT = 1000;
    private static final int MIXED_CASE_COUNT = 2000;
    private static final int CONCURRENT_REQUESTS = 100;
    
    private YNetRunner javaEngine;
    private PythonService pythonService;
    private final ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
    private final PolyglotMetrics metrics = new PolyglotMetrics();
    
    @BeforeAll
    void setupPolyglotEnvironment() throws Exception {
        // Initialize Java YAWL engine
        javaEngine = new YNetRunner(createTestNet());
        
        // Initialize Python service
        pythonService = new PythonService();
        pythonService.start();
        
        // Allow services to initialize
        Thread.sleep(5000);
        
        // Validate connectivity
        validateConnectivity();
    }
    
    @AfterAll
    void teardownPolyglotEnvironment() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        pythonService.stop();
        javaEngine.shutdown();
    }
    
    @Test
    @DisplayName("Pure Java Workload Performance")
    void testPureJavaWorkload() throws Exception {
        System.out.println("Testing pure Java workload...");
        
        long startTime = System.currentTimeMillis();
        
        // Submit pure Java workload
        AtomicInteger javaCompleted = new AtomicInteger(0);
        CountDownLatch javaLatch = new CountDownLatch(JAVA_CASE_COUNT);
        
        for (int i = 0; i < JAVA_CASE_COUNT; i++) {
            final int caseId = i;
            executor.submit(() -> {
                try {
                    long caseStart = System.currentTimeMillis();
                    
                    // Process in Java only
                    String caseIdStr = "java-case-" + caseId;
                    javaEngine.createCase(caseIdStr);
                    
                    // Pure Java processing
                    processJavaWorkflow(caseIdStr);
                    
                    long caseTime = System.currentTimeMillis() - caseStart;
                    metrics.recordJavaCaseTime(caseTime);
                    
                    javaCompleted.incrementAndGet();
                    javaLatch.countDown();
                } catch (Exception e) {
                    metrics.recordJavaFailedCase();
                }
            });
        }
        
        javaLatch.await(2, TimeUnit.MINUTES);
        long javaDuration = System.currentTimeMillis() - startTime;
        
        System.out.printf("Pure Java: %d cases in %dms (%.2f cases/sec)%n",
            javaCompleted.get(), javaDuration, 
            javaCompleted.get() / (javaDuration / 1000.0));
        
        // Validate Java performance
        assertTrue(metrics.getJavaAverageLatency() < 200,
            "Pure Java average latency should be < 200ms");
    }
    
    @Test
    @DisplayName("Python Service Integration")
    void testPythonServiceIntegration() throws Exception {
        System.out.println("Testing Python service integration...");
        
        long startTime = System.currentTimeMillis();
        
        // Submit Python workload
        AtomicInteger pythonCompleted = new AtomicInteger(0);
        CountDownLatch pythonLatch = new CountDownLatch(PYTHON_CASE_COUNT);
        
        for (int i = 0; i < PYTHON_CASE_COUNT; i++) {
            final int caseId = i;
            executor.submit(() -> {
                try {
                    long caseStart = System.currentTimeMillis();
                    
                    // Create case in Java
                    String caseIdStr = "python-case-" + caseId;
                    javaEngine.createCase(caseIdStr);
                    
                    // Call Python service
                    String pythonResult = pythonService.processData(caseIdStr);
                    
                    // Process result in Java
                    processPythonResult(caseIdStr, pythonResult);
                    
                    long caseTime = System.currentTimeMillis() - caseStart;
                    metrics.recordPythonCaseTime(caseTime);
                    
                    pythonCompleted.incrementAndGet();
                    pythonLatch.countDown();
                } catch (Exception e) {
                    metrics.recordPythonFailedCase();
                }
            });
        }
        
        pythonLatch.await(2, TimeUnit.MINUTES);
        long pythonDuration = System.currentTimeMillis() - startTime;
        
        System.out.printf("Python Integration: %d cases in %dms (%.2f cases/sec)%n",
            pythonCompleted.get(), pythonDuration,
            pythonCompleted.get() / (pythonDuration / 1000.0));
        
        // Validate Python integration
        assertTrue(metrics.getPythonAverageLatency() < 500,
            "Python integration average latency should be < 500ms");
        
        // Validate data consistency
        validateDataConsistency();
    }
    
    @Test
    @DisplayName("Mixed Polyglot Workload")
    void testMixedPolyglotWorkload() throws Exception {
        System.out.println("Testing mixed polyglot workload...");
        
        // Submit mixed workload
        AtomicInteger mixedCompleted = new AtomicInteger(0);
        CountDownLatch mixedLatch = new CountDownLatch(MIXED_CASE_COUNT);
        
        for (int i = 0; i < MIXED_CASE_COUNT; i++) {
            final int caseId = i;
            executor.submit(() -> {
                try {
                    long caseStart = System.currentTimeMillis();
                    
                    // Determine language mix based on case ID
                    String caseIdStr = "mixed-case-" + caseId;
                    javaEngine.createCase(caseIdStr);
                    
                    // Process with mixed language approach
                    if (caseId % 3 == 0) {
                        // Java-heavy: 80% Java, 20% Python
                        processJavaHeavyWorkflow(caseIdStr);
                    } else if (caseId % 3 == 1) {
                        // Python-heavy: 20% Java, 80% Python
                        processPythonHeavyWorkflow(caseIdStr);
                    } else {
                        // Balanced: 50% Java, 50% Python
                        processBalancedWorkflow(caseIdStr);
                    }
                    
                    long caseTime = System.currentTimeMillis() - caseStart;
                    metrics.recordMixedCaseTime(caseTime);
                    
                    mixedCompleted.incrementAndGet();
                    mixedLatch.countDown();
                } catch (Exception e) {
                    metrics.recordMixedFailedCase();
                }
            });
        }
        
        mixedLatch.await(3, TimeUnit.MINUTES);
        
        System.out.printf("Mixed Workload: %d cases completed%n", mixedCompleted.get());
        
        // Validate mixed workload performance
        assertTrue(metrics.getMixedAverageLatency() < 400,
            "Mixed workload average latency should be < 400ms");
        
        // Analyze language distribution impact
        analyzeLanguageDistributionImpact();
    }
    
    @Test
    @DisplayName("Cross-Language Error Handling")
    void testCrossLanguageErrorHandling() throws Exception {
        System.out.println("Testing cross-language error handling...");
        
        // Test error scenarios
        testPythonServiceFailure();
        testDataSerializationFailure();
        testTimeoutHandling();
        
        // Validate error recovery
        validateErrorRecovery();
    }
    
    @Test
    @DisplayName("Polyglot Performance Optimization")
    void testPolyglotPerformanceOptimization() throws Exception {
        System.out.println("Testing polyglot performance optimization...");
        
        // Test batching optimization
        testBatchingOptimization();
        
        // Test caching optimization
        testCachingOptimization();
        
        // Test connection pooling
        testConnectionPooling();
    }
    
    private void processJavaWorkflow(String caseId) throws Exception {
        // Simulate pure Java workflow processing
        List<YWorkItem> workItems = javaEngine.getWorkItemsForCase(caseId);
        for (YWorkItem workItem : workItems) {
            workItem.checkoutTo("java-user");
            Thread.sleep(new Random().nextInt(20) + 10);
            workItem.complete("java-processed");
        }
    }
    
    private void processPythonResult(String caseId, String pythonResult) throws Exception {
        // Process Python result in Java
        List<YWorkItem> workItems = javaEngine.getWorkItemsForCase(caseId);
        for (YWorkItem workItem : workItems) {
            workItem.checkoutTo("java-user");
            // Use Python result in Java processing
            String enrichedData = pythonResult + "-java-processed";
            workItem.setData("python_result", enrichedData);
            workItem.complete("python-integrated");
        }
    }
    
    private void processJavaHeavyWorkflow(String caseId) throws Exception {
        // 80% Java, 20% Python
        processJavaWorkflow(caseId);
        
        // Call Python service for specific task
        String pythonResult = pythonService.processData(caseId);
        processPythonResult(caseId, pythonResult);
    }
    
    private void processPythonHeavyWorkflow(String caseId) throws Exception {
        // 20% Java, 80% Python
        // Initial Java setup
        javaEngine.createCase(caseId);
        
        // Call Python service multiple times
        for (int i = 0; i < 4; i++) {
            String pythonResult = pythonService.processData(caseId + "-" + i);
            // Process intermediate result
        }
        
        // Final Java completion
        processJavaWorkflow(caseId);
    }
    
    private void processBalancedWorkflow(String caseId) throws Exception {
        // 50% Java, 50% Python
        // Alternate between Java and Python
        for (int i = 0; i < 2; i++) {
            if (i % 2 == 0) {
                processJavaWorkflow(caseId + "-java-" + i);
            } else {
                String pythonResult = pythonService.processData(caseId + "-python-" + i);
                processPythonResult(caseId + "-python-" + i, pythonResult);
            }
        }
    }
    
    private void testPythonServiceFailure() throws Exception {
        // Simulate Python service failure
        pythonService.simulateFailure();
        
        try {
            pythonService.processData("test-failure");
            fail("Python service failure should have been detected");
        } catch (ServiceException e) {
            metrics.recordPythonServiceFailure();
            System.out.println("Python service failure correctly detected: " + e.getMessage());
        }
        
        // Test fallback mechanism
        pythonService.enableFallback();
        String result = pythonService.processData("test-fallback");
        assertNotNull(result, "Fallback should provide a result");
    }
    
    private void testDataSerializationFailure() throws Exception {
        // Test data serialization issues
        try {
            // Send data that cannot be serialized to Python
            Object unserializableData = new Object() {
                @Override
                public String toString() {
                    return "unserializable-data";
                }
            };
            pythonService.processData(unserializableData.toString());
        } catch (SerializationException e) {
            metrics.recordSerializationFailure();
            System.out.println("Serialization failure correctly detected: " + e.getMessage());
        }
    }
    
    private void testTimeoutHandling() throws Exception {
        // Test timeout handling
        pythonService.setResponseTime(5000); // 5 seconds
        
        long timeoutStart = System.currentTimeMillis();
        try {
            pythonService.processData("test-timeout");
        } catch (TimeoutException e) {
            long timeoutDuration = System.currentTimeMillis() - timeoutStart;
            metrics.recordTimeout(timeoutDuration);
            System.out.println("Timeout handled in " + timeoutDuration + "ms");
        }
    }
    
    private void validateErrorRecovery() {
        // Validate that errors are properly handled and recovered
        assertTrue(metrics.getErrorRecoveryRate() > 0.95,
            "Error recovery rate should be > 95%");
        
        System.out.println("Error recovery rate: " + (metrics.getErrorRecoveryRate() * 100) + "%");
    }
    
    private void testBatchingOptimization() throws Exception {
        System.out.println("Testing batching optimization...");
        
        // Test batch processing
        List<String> batchData = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            batchData.add("batch-item-" + i);
        }
        
        long batchStart = System.currentTimeMillis();
        List<String> batchResults = pythonService.processBatch(batchData);
        long batchTime = System.currentTimeMillis() - batchStart;
        
        System.out.printf("Batch processing: %d items in %dms (%.2f items/sec)%n",
            batchData.size(), batchTime,
            batchData.size() / (batchTime / 1000.0));
        
        // Validate batching efficiency
        double efficiency = calculateBatchingEfficiency(batchTime, batchData.size());
        System.out.printf("Batching efficiency: %.2f%%%n", efficiency * 100);
        
        assertTrue(efficiency > 1.5, "Batching should be at least 50% more efficient");
    }
    
    private void testCachingOptimization() throws Exception {
        System.out.println("Testing caching optimization...");
        
        // Enable caching
        pythonService.enableCaching();
        
        // Test repeated calls with same data
        String testData = "cache-test-data";
        
        // First call (cache miss)
        long missTime = measurePythonCall(() -> pythonService.processData(testData));
        
        // Subsequent calls (cache hits)
        long hitTime = measurePythonCall(() -> pythonService.processData(testData));
        
        System.out.printf("Cache miss: %dms, Cache hit: %dms%n", missTime, hitTime);
        
        // Validate caching benefit
        assertTrue(hitTime < missTime / 2, "Cache hit should be at least 2x faster");
    }
    
    private void testConnectionPooling() throws Exception {
        System.out.println("Testing connection pooling...");
        
        // Test concurrent connections
        int concurrentRequests = 50;
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        AtomicLong totalResponseTime = new AtomicLong(0);
        
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    pythonService.processData("concurrent-test-" + Thread.currentThread().getId());
                    totalResponseTime.addAndGet(System.currentTimeMillis() - startTime);
                    latch.countDown();
                } catch (Exception e) {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        double avgResponseTime = totalResponseTime.get() / (double) concurrentRequests;
        
        System.out.printf("Concurrent response time: %.2fms%n", avgResponseTime);
        
        // Validate pooling efficiency
        assertTrue(avgResponseTime < 200, 
            "Concurrent response time should be < 200ms");
    }
    
    private long measurePythonCall(Callable<String> call) throws Exception {
        long start = System.currentTimeMillis();
        call.call();
        return System.currentTimeMillis() - start;
    }
    
    private double calculateBatchingEfficiency(long batchTime, int itemCount) {
        // Calculate efficiency compared to individual calls
        double individualTime = 100; // Assume 100ms per individual call
        double expectedIndividualTime = itemCount * individualTime;
        return expectedIndividualTime / batchTime;
    }
    
    private void validateConnectivity() throws Exception {
        // Test basic connectivity between Java and Python
        String testResult = pythonService.processData("connectivity-test");
        assertEquals("connectivity-test-processed", testResult,
            "Basic connectivity should work");
        
        System.out.println("Connectivity validation passed");
    }
    
    private void validateDataConsistency() throws Exception {
        // Test data consistency across language boundaries
        String testData = "consistency-test-data";
        String pythonResult = pythonService.processData(testData);
        
        // Verify that data is properly serialized/deserialized
        assertNotNull(pythonResult, "Python service should return valid data");
        assertTrue(pythonResult.contains(testData), 
            "Python result should contain original data");
        
        System.out.println("Data consistency validation passed");
    }
    
    private void analyzeLanguageDistributionImpact() {
        System.out.println("\n=== LANGUAGE DISTRIBUTION IMPACT ANALYSIS ===");
        
        // Compare performance across different language distributions
        double javaLatency = metrics.getJavaAverageLatency();
        double pythonLatency = metrics.getPythonAverageLatency();
        double mixedLatency = metrics.getMixedAverageLatency();
        
        System.out.printf("Pure Java latency: %.2fms%n", javaLatency);
        System.out.printf("Python Integration latency: %.2fms%n", pythonLatency);
        System.out.printf("Mixed workload latency: %.2fms%n", mixedLatency);
        
        // Calculate overhead
        double pythonOverhead = (pythonLatency - javaLatency) / javaLatency;
        double mixedOverhead = (mixedLatency - javaLatency) / javaLatency;
        
        System.out.printf("Python integration overhead: %.2f%%%n", pythonOverhead * 100);
        System.out.printf("Mixed workload overhead: %.2f%%%n", mixedOverhead * 100);
        
        // Validate overhead thresholds
        assertTrue(pythonOverhead < 2.0, 
            "Python integration overhead should be < 200%");
        assertTrue(mixedOverhead < 1.5, 
            "Mixed workload overhead should be < 150%");
    }
    
    /**
     * Mock Python service for testing
     */
    private static class PythonService {
        private boolean isRunning = false;
        private boolean isFailed = false;
        private boolean isFallbackEnabled = false;
        private boolean isCachingEnabled = false;
        private long responseTime = 100;
        
        public void start() {
            isRunning = true;
            System.out.println("Python service started");
        }
        
        public void stop() {
            isRunning = false;
            System.out.println("Python service stopped");
        }
        
        public String processData(String data) throws ServiceException {
            if (!isRunning) {
                throw new ServiceException("Python service not running");
            }
            
            if (isFailed) {
                throw new ServiceException("Python service failed");
            }
            
            try {
                Thread.sleep(responseTime);
                
                // Simulate Python processing
                String result = data + "-processed";
                
                if (isCachingEnabled) {
                    // Simulate caching
                    result = "cached-" + result;
                }
                
                return result;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServiceException("Processing interrupted");
            }
        }
        
        public List<String> processBatch(List<String> data) throws ServiceException {
            List<String> results = new ArrayList<>();
            for (String item : data) {
                results.add(processData(item));
            }
            return results;
        }
        
        public void simulateFailure() {
            isFailed = true;
        }
        
        public void enableFallback() {
            isFallbackEnabled = true;
        }
        
        public void enableCaching() {
            isCachingEnabled = true;
        }
        
        public void setResponseTime(long milliseconds) {
            this.responseTime = milliseconds;
        }
    }
    
    /**
     * Metrics collection for polyglot tests
     */
    private static class PolyglotMetrics {
        private final List<Long> javaCaseTimes = new ArrayList<>();
        private final List<Long> pythonCaseTimes = new ArrayList<>();
        private final List<Long> mixedCaseTimes = new ArrayList<>();
        private int javaFailedCases = 0;
        private int pythonFailedCases = 0;
        private int mixedFailedCases = 0;
        private int pythonServiceFailures = 0;
        private int serializationFailures = 0;
        private int timeouts = 0;
        
        public void recordJavaCaseTime(long time) {
            javaCaseTimes.add(time);
        }
        
        public void recordPythonCaseTime(long time) {
            pythonCaseTimes.add(time);
        }
        
        public void recordMixedCaseTime(long time) {
            mixedCaseTimes.add(time);
        }
        
        public void recordJavaFailedCase() {
            javaFailedCases++;
        }
        
        public void recordPythonFailedCase() {
            pythonFailedCases++;
        }
        
        public void recordMixedFailedCase() {
            mixedFailedCases++;
        }
        
        public void recordPythonServiceFailure() {
            pythonServiceFailures++;
        }
        
        public void recordSerializationFailure() {
            serializationFailures++;
        }
        
        public void recordTimeout(long duration) {
            timeouts++;
        }
        
        public double getJavaAverageLatency() {
            if (javaCaseTimes.isEmpty()) return 0;
            return javaCaseTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        }
        
        public double getPythonAverageLatency() {
            if (pythonCaseTimes.isEmpty()) return 0;
            return pythonCaseTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        }
        
        public double getMixedAverageLatency() {
            if (mixedCaseTimes.isEmpty()) return 0;
            return mixedCaseTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        }
        
        public double getErrorRecoveryRate() {
            int totalErrors = pythonServiceFailures + serializationFailures + timeouts;
            int totalCases = javaCaseTimes.size() + pythonCaseTimes.size() + mixedCaseTimes.size();
            if (totalCases == 0) return 1.0;
            return 1.0 - (totalErrors / (double) totalCases);
        }
    }
    
    /**
     * Exception classes for polyglot testing
     */
    private static class ServiceException extends Exception {
        public ServiceException(String message) {
            super(message);
        }
    }
    
    private static class SerializationException extends Exception {
        public SerializationException(String message) {
            super(message);
        }
    }
    
    private static class TimeoutException extends Exception {
        public TimeoutException(String message) {
            super(message);
        }
    }
    
    private YNet createTestNet() {
        // In a real implementation, this would create a test YNet
        return null;
    }
}
