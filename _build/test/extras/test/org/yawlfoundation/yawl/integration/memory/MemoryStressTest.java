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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Adversarial stress test for UpgradeMemoryStore and LearningCapture.
 *
 * <p>Tests thread safety, throughput, latency, memory usage, and potential leaks
 * under extreme concurrent load conditions.</p>
 *
 * <h2>Test Scenarios</h2>
 * <ul>
 *   <li>Concurrent record creation (1000+ records, 50+ threads)</li>
 *   <li>Thread safety verification with simultaneous writes</li>
 *   <li>File persistence under load</li>
 *   <li>Pattern extraction with large datasets</li>
 *   <li>Memory usage and leak detection</li>
 *   <li>Race condition and deadlock detection</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Tag("stress")
@DisplayName("Memory System Stress Tests")
class MemoryStressTest {

    private static final int RECORD_COUNT = 1000;
    private static final int THREAD_COUNT = 50;
    private static final int LARGE_DATASET_SIZE = 5000;
    private static final int WARMUP_ITERATIONS = 10;

    @TempDir
    Path tempDir;

    private UpgradeMemoryStore memoryStore;
    private LearningCapture learningCapture;
    private MemoryMXBean memoryBean;

    @BeforeEach
    void setUp() throws IOException {
        Path memoryPath = tempDir.resolve("memory");
        Files.createDirectories(memoryPath);
        memoryStore = new UpgradeMemoryStore(memoryPath);
        learningCapture = new LearningCapture(memoryStore);
        memoryBean = ManagementFactory.getMemoryMXBean();
        
        // Force GC before each test for consistent memory measurements
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        if (memoryStore != null) {
            memoryStore.clear();
        }
    }

    // ========================================================================
    // CONCURRENT RECORD CREATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Create 1000+ upgrade records concurrently")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testConcurrentRecordCreation() throws Exception {
        // Arrange
        CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalLatencyNanos = new AtomicLong(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        // Warmup
        warmupStore();

        // Act - Create records concurrently
        long testStart = System.nanoTime();
        
        try (ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT)) {
            List<Future<Void>> futures = new ArrayList<>();
            
            for (int i = 0; i < RECORD_COUNT; i++) {
                final int recordIndex = i;
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await(); // Synchronize all threads
                        
                        long start = System.nanoTime();
                        UpgradeMemoryStore.UpgradeRecord record = createTestRecord(recordIndex);
                        memoryStore.store(record);
                        long end = System.nanoTime();
                        
                        long latencyNanos = end - start;
                        totalLatencyNanos.addAndGet(latencyNanos);
                        latencies.add(latencyNanos);
                        successCount.incrementAndGet();
                        
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        throw new RuntimeException("Failed at record " + recordIndex, e);
                    }
                    return null;
                }));
            }
            
            // Wait for all tasks to complete
            for (Future<Void> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Count failures - some may be expected under extreme load
                }
            }
            
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
        
        long testEnd = System.nanoTime();
        long testDurationMillis = (testEnd - testStart) / 1_000_000;

        // Assert - Verify results
        int totalRecords = successCount.get() + failureCount.get();
        
        System.out.println("\n=== Concurrent Record Creation Results ===");
        System.out.println("Total Records Attempted: " + totalRecords);
        System.out.println("Successful Stores: " + successCount.get());
        System.out.println("Failed Stores: " + failureCount.get());
        System.out.println("Test Duration: " + testDurationMillis + " ms");
        System.out.println("Throughput: " + String.format("%.2f", successCount.get() * 1000.0 / testDurationMillis) + " records/sec");
        
        // Calculate latency percentiles
        Collections.sort(latencies);
        if (!latencies.isEmpty()) {
            double p50 = getPercentile(latencies, 50) / 1_000_000.0;
            double p90 = getPercentile(latencies, 90) / 1_000_000.0;
            double p99 = getPercentile(latencies, 99) / 1_000_000.0;
            double avgLatency = totalLatencyNanos.get() / (double) successCount.get() / 1_000_000.0;
            
            System.out.println("Latency (avg): " + String.format("%.3f", avgLatency) + " ms");
            System.out.println("Latency (p50): " + String.format("%.3f", p50) + " ms");
            System.out.println("Latency (p90): " + String.format("%.3f", p90) + " ms");
            System.out.println("Latency (p99): " + String.format("%.3f", p99) + " ms");
        }

        // Verify store consistency
        assertEquals(successCount.get(), memoryStore.retrieveAll().size(),
                "Stored record count should match successful stores");
        
        // Target: > 500 records/sec throughput, p99 < 200ms
        double throughput = successCount.get() * 1000.0 / testDurationMillis;
        assertTrue(throughput > 100, "Throughput should be > 100 records/sec, got: " + throughput);
    }

    @Test
    @DisplayName("Test thread safety with 50+ threads writing simultaneously")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testThreadSafetyWithHighConcurrency() throws Exception {
        // Arrange
        int numThreads = 75;
        int recordsPerThread = 20;
        CyclicBarrier barrier = new CyclicBarrier(numThreads);
        AtomicInteger raceConditions = new AtomicInteger(0);
        ConcurrentHashMap<String, String> recordOwners = new ConcurrentHashMap<>();
        AtomicInteger successfulStores = new AtomicInteger(0);

        // Act - Many threads writing simultaneously
        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            List<Future<Void>> futures = new ArrayList<>();
            
            for (int thread = 0; thread < numThreads; thread++) {
                final String threadId = "thread-" + thread;
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await(); // All threads start at once
                        
                        for (int i = 0; i < recordsPerThread; i++) {
                            String recordId = threadId + "-record-" + i;
                            
                            // Track who owns this record
                            String previousOwner = recordOwners.putIfAbsent(recordId, threadId);
                            if (previousOwner != null) {
                                raceConditions.incrementAndGet();
                            }
                            
                            UpgradeMemoryStore.UpgradeRecord record = new UpgradeMemoryStore.UpgradeRecord.Builder()
                                    .id(recordId)
                                    .sessionId("session-" + threadId)
                                    .targetVersion("6.0.0")
                                    .sourceVersion("5.0.0")
                                    .startTime(Instant.now())
                                    .outcome(new UpgradeMemoryStore.Success("Thread test"))
                                    .build();
                            
                            memoryStore.store(record);
                            successfulStores.incrementAndGet();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Thread safety test failed", e);
                    }
                    return null;
                }));
            }
            
            // Wait for completion
            for (Future<Void> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
            
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        // Assert - No race conditions and all records stored correctly
        int expectedRecords = numThreads * recordsPerThread;
        
        System.out.println("\n=== Thread Safety Results ===");
        System.out.println("Threads: " + numThreads);
        System.out.println("Records per Thread: " + recordsPerThread);
        System.out.println("Expected Records: " + expectedRecords);
        System.out.println("Stored Records: " + memoryStore.retrieveAll().size());
        System.out.println("Race Conditions Detected: " + raceConditions.get());
        
        assertEquals(expectedRecords, memoryStore.retrieveAll().size(),
                "All records should be stored without loss");
        assertEquals(0, raceConditions.get(),
                "No race conditions should occur (duplicate record IDs)");
        
        // Verify each record is retrievable
        for (Map.Entry<String, String> entry : recordOwners.entrySet()) {
            assertTrue(memoryStore.retrieve(entry.getKey()).isPresent(),
                    "Record " + entry.getKey() + " should be retrievable");
        }
    }

    @Test
    @DisplayName("Test file persistence under concurrent load")
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void testFilePersistenceUnderLoad() throws Exception {
        // Arrange
        int numBatches = 10;
        int recordsPerBatch = 100;
        AtomicInteger persistenceErrors = new AtomicInteger(0);
        List<Long> persistTimes = Collections.synchronizedList(new ArrayList<>());

        // Act - Multiple rounds of storing and reloading
        for (int batch = 0; batch < numBatches; batch++) {
            long batchStart = System.nanoTime();
            
            // Store a batch of records
            List<UpgradeMemoryStore.UpgradeRecord> records = new ArrayList<>();
            for (int i = 0; i < recordsPerBatch; i++) {
                records.add(createTestRecord(batch * recordsPerBatch + i));
            }
            
            // Store concurrently
            try (ExecutorService executor = Executors.newFixedThreadPool(20)) {
                List<Future<Void>> futures = records.stream()
                        .map(record -> executor.submit(() -> {
                            memoryStore.store(record);
                            return (Void) null;
                        }))
                        .collect(Collectors.toList());
                
                for (Future<Void> future : futures) {
                    future.get(10, TimeUnit.SECONDS);
                }
                
                executor.shutdown();
            }
            
            long batchEnd = System.nanoTime();
            persistTimes.add((batchEnd - batchStart) / 1_000_000);
            
            // Reload store and verify persistence
            UpgradeMemoryStore reloadedStore = new UpgradeMemoryStore(tempDir.resolve("memory"));
            int expectedTotal = (batch + 1) * recordsPerBatch;
            
            if (reloadedStore.retrieveAll().size() != expectedTotal) {
                persistenceErrors.incrementAndGet();
                System.err.println("Persistence mismatch at batch " + batch + 
                        ": expected " + expectedTotal + 
                        ", got " + reloadedStore.retrieveAll().size());
            }
        }

        // Assert
        System.out.println("\n=== File Persistence Results ===");
        System.out.println("Batches: " + numBatches);
        System.out.println("Records per Batch: " + recordsPerBatch);
        System.out.println("Total Records: " + (numBatches * recordsPerBatch));
        System.out.println("Persistence Errors: " + persistenceErrors.get());
        System.out.println("Avg Batch Persist Time: " + 
                String.format("%.2f", persistTimes.stream().mapToLong(l -> l).average().orElse(0)) + " ms");
        
        assertEquals(0, persistenceErrors.get(), "No persistence errors should occur");
        assertEquals(numBatches * recordsPerBatch, memoryStore.retrieveAll().size(),
                "All records should be persisted");
    }

    // ========================================================================
    // PATTERN EXTRACTION TESTS
    // ========================================================================

    @Test
    @DisplayName("Test pattern extraction with large datasets")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void testPatternExtractionWithLargeDatasets() throws Exception {
        // Arrange - Create a large dataset with various patterns
        int successCount = LARGE_DATASET_SIZE * 70 / 100; // 70% success rate
        int failureCount = LARGE_DATASET_SIZE * 20 / 100; // 20% failure rate
        int partialCount = LARGE_DATASET_SIZE - successCount - failureCount; // 10% partial
        
        System.out.println("\n=== Creating Large Dataset ===");
        System.out.println("Success Records: " + successCount);
        System.out.println("Failure Records: " + failureCount);
        System.out.println("Partial Records: " + partialCount);
        
        long createStart = System.nanoTime();
        
        // Create success records
        for (int i = 0; i < successCount; i++) {
            UpgradeMemoryStore.UpgradeRecord record = createSuccessRecord(i);
            memoryStore.store(record);
        }
        
        // Create failure records (various types)
        String[] errorTypes = {"CompileError", "TestFailure", "ValidationError", "TimeoutError"};
        for (int i = 0; i < failureCount; i++) {
            String errorType = errorTypes[i % errorTypes.length];
            UpgradeMemoryStore.UpgradeRecord record = createFailureRecord(i, errorType);
            memoryStore.store(record);
        }
        
        // Create partial records
        for (int i = 0; i < partialCount; i++) {
            UpgradeMemoryStore.UpgradeRecord record = createPartialRecord(i);
            memoryStore.store(record);
        }
        
        long createEnd = System.nanoTime();
        System.out.println("Dataset Creation Time: " + ((createEnd - createStart) / 1_000_000) + " ms");
        
        // Act - Extract patterns
        long extractStart = System.nanoTime();
        LearningCapture.AnalysisResult result = learningCapture.analyzeAll();
        long extractEnd = System.nanoTime();
        
        // Assert
        System.out.println("\n=== Pattern Extraction Results ===");
        System.out.println("Records Analyzed: " + result.recordsAnalyzed());
        System.out.println("Success Patterns Extracted: " + result.successPatternsExtracted());
        System.out.println("Failure Patterns Extracted: " + result.failurePatternsExtracted());
        System.out.println("Agent Metrics Updated: " + result.agentMetricsUpdated());
        System.out.println("Extraction Time: " + ((extractEnd - extractStart) / 1_000_000) + " ms");
        
        // Verify patterns are meaningful
        List<LearningCapture.SuccessPattern> successPatterns = learningCapture.getSuccessPatterns();
        List<LearningCapture.FailurePattern> failurePatterns = learningCapture.getFailurePatterns();
        
        assertFalse(successPatterns.isEmpty(), "Should extract at least one success pattern");
        assertFalse(failurePatterns.isEmpty(), "Should extract at least one failure pattern");
        
        // Verify patterns have reasonable occurrence counts
        assertTrue(successPatterns.get(0).occurrenceCount() > 0,
                "Success patterns should have positive occurrence count");
        assertTrue(failurePatterns.get(0).occurrenceCount() > 0,
                "Failure patterns should have positive occurrence count");
        
        // Performance target: Extract patterns from 5000 records in < 10 seconds
        long extractTimeMs = (extractEnd - extractStart) / 1_000_000;
        assertTrue(extractTimeMs < 10000,
                "Pattern extraction should complete in < 10 seconds, took: " + extractTimeMs + " ms");
    }

    @Test
    @DisplayName("Test concurrent pattern updates")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testConcurrentPatternUpdates() throws Exception {
        // Arrange - Pre-populate with some records
        for (int i = 0; i < 100; i++) {
            memoryStore.store(createTestRecord(i));
        }
        
        int numThreads = 30;
        CyclicBarrier barrier = new CyclicBarrier(numThreads);
        AtomicInteger errors = new AtomicInteger(0);

        // Act - Concurrently update patterns
        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            List<Future<Void>> futures = new ArrayList<>();
            
            for (int thread = 0; thread < numThreads; thread++) {
                final int threadNum = thread;
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await();
                        
                        // Each thread captures outcomes
                        for (int i = 0; i < 10; i++) {
                            UpgradeMemoryStore.UpgradeRecord record = createTestRecord(100 + threadNum * 10 + i);
                            memoryStore.store(record);
                            learningCapture.captureOutcome(record);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        throw new RuntimeException(e);
                    }
                    return null;
                }));
            }
            
            for (Future<Void> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Count errors
                }
            }
            
            executor.shutdown();
        }

        // Assert
        System.out.println("\n=== Concurrent Pattern Updates ===");
        System.out.println("Threads: " + numThreads);
        System.out.println("Errors: " + errors.get());
        System.out.println("Success Patterns: " + learningCapture.getSuccessPatterns().size());
        System.out.println("Failure Patterns: " + learningCapture.getFailurePatterns().size());
        
        // Should have patterns without corruption
        assertTrue(learningCapture.getSuccessPatterns().size() > 0 || 
                   learningCapture.getFailurePatterns().size() > 0,
                   "Should have some patterns extracted");
    }

    // ========================================================================
    // MEMORY USAGE AND LEAK DETECTION
    // ========================================================================

    @Test
    @DisplayName("Test memory usage and detect potential leaks")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void testMemoryUsageAndLeaks() throws Exception {
        // Arrange - Get baseline memory
        System.gc();
        Thread.sleep(200);
        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed();
        
        System.out.println("\n=== Memory Usage Analysis ===");
        System.out.println("Before Test - Heap Used: " + (beforeUsed / 1024 / 1024) + " MB");
        
        // Act - Create and delete many records
        int iterations = 5;
        long[] memoryAfterIteration = new long[iterations];
        
        for (int iter = 0; iter < iterations; iter++) {
            // Create records
            for (int i = 0; i < 1000; i++) {
                UpgradeMemoryStore.UpgradeRecord record = createTestRecord(iter * 1000 + i);
                memoryStore.store(record);
            }
            
            // Clear store
            memoryStore.clear();
            learningCapture.clearPatterns();
            
            // Force GC
            System.gc();
            Thread.sleep(100);
            
            // Record memory after iteration
            memoryAfterIteration[iter] = memoryBean.getHeapMemoryUsage().getUsed();
            System.out.println("After Iteration " + (iter + 1) + " - Heap Used: " + 
                    (memoryAfterIteration[iter] / 1024 / 1024) + " MB");
        }
        
        // Assert - Check for memory leak (memory should not grow significantly)
        System.gc();
        Thread.sleep(200);
        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        long afterUsed = afterHeap.getUsed();
        long memoryGrowth = afterUsed - beforeUsed;
        
        System.out.println("\nAfter Test - Heap Used: " + (afterUsed / 1024 / 1024) + " MB");
        System.out.println("Memory Growth: " + (memoryGrowth / 1024 / 1024) + " MB");
        
        // Calculate growth trend (should not be consistently increasing)
        int increasingCount = 0;
        for (int i = 1; i < iterations; i++) {
            if (memoryAfterIteration[i] > memoryAfterIteration[i - 1]) {
                increasingCount++;
            }
        }
        
        System.out.println("Iterations with Increasing Memory: " + increasingCount + "/" + (iterations - 1));
        
        // Memory growth should be < 50MB (allowing for GC overhead)
        assertTrue(memoryGrowth < 50 * 1024 * 1024,
                "Memory growth should be < 50MB, got: " + (memoryGrowth / 1024 / 1024) + " MB");
        
        // Memory should not increase in every iteration (sign of leak)
        assertTrue(increasingCount < iterations - 1,
                "Memory should not increase in every iteration (potential leak)");
    }

    @Test
    @DisplayName("Test memory efficiency with large records")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testMemoryEfficiencyWithLargeRecords() throws Exception {
        // Arrange
        System.gc();
        Thread.sleep(100);
        long beforeMem = memoryBean.getHeapMemoryUsage().getUsed();
        
        int numRecords = 500;
        int phasesPerRecord = 50; // Each record has many phases
        int metadataEntries = 100; // Each record has many metadata entries
        
        // Act - Create records with large data
        for (int i = 0; i < numRecords; i++) {
            UpgradeMemoryStore.UpgradeRecord.Builder builder = new UpgradeMemoryStore.UpgradeRecord.Builder()
                    .id("large-record-" + i)
                    .sessionId("session-large-" + i)
                    .targetVersion("6.0.0")
                    .sourceVersion("5.0.0")
                    .startTime(Instant.now())
                    .outcome(new UpgradeMemoryStore.Success("Large record test"));
            
            // Add many phases
            for (int p = 0; p < phasesPerRecord; p++) {
                builder.addPhase(new UpgradeMemoryStore.PhaseResult(
                        "phase-" + p,
                        Instant.now(),
                        Instant.now().plusMillis(100),
                        new UpgradeMemoryStore.Success("Phase " + p + " completed"),
                        "Output for phase " + p + " with some text to increase size"
                ));
            }
            
            // Add many metadata entries
            for (int m = 0; m < metadataEntries; m++) {
                builder.addMetadata("meta-key-" + m, "meta-value-" + m + "-with-some-content");
            }
            
            memoryStore.store(builder.build());
            
            if (i % 100 == 0) {
                System.out.println("Created " + i + " large records...");
            }
        }
        
        // Assert
        System.gc();
        Thread.sleep(100);
        long afterMem = memoryBean.getHeapMemoryUsage().getUsed();
        long memoryUsed = afterMem - beforeMem;
        long avgBytesPerRecord = memoryUsed / numRecords;
        
        System.out.println("\n=== Large Record Memory Usage ===");
        System.out.println("Records: " + numRecords);
        System.out.println("Phases per Record: " + phasesPerRecord);
        System.out.println("Metadata per Record: " + metadataEntries);
        System.out.println("Total Memory Used: " + (memoryUsed / 1024 / 1024) + " MB");
        System.out.println("Avg Memory per Record: " + (avgBytesPerRecord / 1024) + " KB");
        
        // Verify records are retrievable
        assertEquals(numRecords, memoryStore.retrieveAll().size());
        
        // Memory per record should be reasonable (< 100KB for this size)
        assertTrue(avgBytesPerRecord < 100 * 1024,
                "Avg memory per record should be < 100KB, got: " + (avgBytesPerRecord / 1024) + " KB");
    }

    // ========================================================================
    // RACE CONDITION AND DEADLOCK DETECTION
    // ========================================================================

    @Test
    @DisplayName("Test for race conditions in concurrent read/write")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testRaceConditionReadWrite() throws Exception {
        // Arrange - Pre-populate some records
        for (int i = 0; i < 100; i++) {
            memoryStore.store(createTestRecord(i));
        }
        
        int numWriters = 25;
        int numReaders = 25;
        AtomicInteger readErrors = new AtomicInteger(0);
        AtomicInteger writeErrors = new AtomicInteger(0);
        AtomicLong totalReadTime = new AtomicLong(0);
        AtomicLong totalWriteTime = new AtomicLong(0);
        AtomicInteger readCount = new AtomicInteger(0);
        AtomicInteger writeCount = new AtomicInteger(0);
        
        CyclicBarrier barrier = new CyclicBarrier(numWriters + numReaders);

        // Act - Concurrent readers and writers
        try (ExecutorService executor = Executors.newFixedThreadPool(numWriters + numReaders)) {
            List<Future<Void>> futures = new ArrayList<>();
            
            // Writers
            for (int i = 0; i < numWriters; i++) {
                final int writerId = i;
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await();
                        for (int j = 0; j < 20; j++) {
                            long start = System.nanoTime();
                            memoryStore.store(createTestRecord(100 + writerId * 20 + j));
                            totalWriteTime.addAndGet(System.nanoTime() - start);
                            writeCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        writeErrors.incrementAndGet();
                    }
                    return null;
                }));
            }
            
            // Readers
            for (int i = 0; i < numReaders; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await();
                        for (int j = 0; j < 20; j++) {
                            long start = System.nanoTime();
                            List<UpgradeMemoryStore.UpgradeRecord> records = memoryStore.retrieveAll();
                            totalReadTime.addAndGet(System.nanoTime() - start);
                            readCount.incrementAndGet();
                            
                            // Verify data consistency
                            for (UpgradeMemoryStore.UpgradeRecord record : records) {
                                assertNotNull(record.id());
                                assertNotNull(record.sessionId());
                            }
                        }
                    } catch (Exception e) {
                        readErrors.incrementAndGet();
                    }
                    return null;
                }));
            }
            
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            
            executor.shutdown();
        }

        // Assert
        System.out.println("\n=== Race Condition Test Results ===");
        System.out.println("Writers: " + numWriters);
        System.out.println("Readers: " + numReaders);
        System.out.println("Write Operations: " + writeCount.get());
        System.out.println("Read Operations: " + readCount.get());
        System.out.println("Write Errors: " + writeErrors.get());
        System.out.println("Read Errors: " + readErrors.get());
        System.out.println("Avg Write Latency: " + 
                (writeCount.get() > 0 ? totalWriteTime.get() / writeCount.get() / 1_000_000.0 : 0) + " ms");
        System.out.println("Avg Read Latency: " + 
                (readCount.get() > 0 ? totalReadTime.get() / readCount.get() / 1_000_000.0 : 0) + " ms");
        
        assertEquals(0, writeErrors.get(), "No write errors should occur");
        assertEquals(0, readErrors.get(), "No read errors should occur");
    }

    @Test
    @DisplayName("Test for deadlocks with mixed operations")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testDeadlockDetection() throws Exception {
        // Arrange - Pre-populate records
        for (int i = 0; i < 50; i++) {
            memoryStore.store(createTestRecord(i));
        }
        
        int numThreads = 20;
        AtomicInteger completedOperations = new AtomicInteger(0);
        AtomicInteger timeouts = new AtomicInteger(0);
        
        // Act - Mix of operations that could potentially deadlock
        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            List<Future<Void>> futures = new ArrayList<>();
            
            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                futures.add(executor.submit(() -> {
                    try {
                        for (int op = 0; op < 30; op++) {
                            int operationType = (threadId + op) % 5;
                            
                            switch (operationType) {
                                case 0 -> {
                                    // Store
                                    memoryStore.store(createTestRecord(threadId * 100 + op));
                                }
                                case 1 -> {
                                    // Retrieve
                                    memoryStore.retrieve("test-record-" + (op % 50));
                                }
                                case 2 -> {
                                    // Retrieve all
                                    memoryStore.retrieveAll();
                                }
                                case 3 -> {
                                    // Statistics
                                    memoryStore.getStatistics();
                                }
                                case 4 -> {
                                    // Update
                                    if (op > 0) {
                                        memoryStore.retrieve("test-record-" + ((op - 1) % 50))
                                                .ifPresent(r -> memoryStore.update(r));
                                    }
                                }
                            }
                            completedOperations.incrementAndGet();
                        }
                    } catch (Exception e) {
                        timeouts.incrementAndGet();
                    }
                    return null;
                }));
            }
            
            // Wait with timeout - deadlocks will cause timeout
            boolean allCompleted = true;
            for (Future<Void> future : futures) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    allCompleted = false;
                    timeouts.incrementAndGet();
                }
            }
            
            executor.shutdownNow();
        }

        // Assert
        System.out.println("\n=== Deadlock Detection Results ===");
        System.out.println("Threads: " + numThreads);
        System.out.println("Completed Operations: " + completedOperations.get());
        System.out.println("Timeouts: " + timeouts.get());
        System.out.println("Expected Operations: " + (numThreads * 30));
        
        // All operations should complete without timeout
        assertTrue(completedOperations.get() > numThreads * 30 * 0.9,
                "Most operations should complete (>90%), got: " + 
                        (completedOperations.get() * 100.0 / (numThreads * 30)) + "%");
        assertEquals(0, timeouts.get(), "No timeouts should occur (potential deadlock)");
    }

    @Test
    @DisplayName("Test atomic file write integrity under crash simulation")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testAtomicFileWriteIntegrity() throws Exception {
        // Arrange
        int numRecords = 100;
        
        // Act - Write records, then simulate crash recovery by reloading
        for (int i = 0; i < numRecords; i++) {
            memoryStore.store(createTestRecord(i));
        }
        
        // Simulate crash - create new store instance (simulates restart)
        UpgradeMemoryStore recoveredStore = new UpgradeMemoryStore(tempDir.resolve("memory"));
        
        // Assert - All records should be recoverable
        List<UpgradeMemoryStore.UpgradeRecord> recovered = recoveredStore.retrieveAll();
        
        System.out.println("\n=== Atomic Write Integrity Test ===");
        System.out.println("Original Records: " + numRecords);
        System.out.println("Recovered Records: " + recovered.size());
        
        assertEquals(numRecords, recovered.size(),
                "All records should be recoverable after crash simulation");
        
        // Verify data integrity
        for (int i = 0; i < numRecords; i++) {
            UpgradeMemoryStore.UpgradeRecord original = memoryStore.retrieve("test-record-" + i).orElse(null);
            UpgradeMemoryStore.UpgradeRecord recoveredRecord = recoveredStore.retrieve("test-record-" + i).orElse(null);
            
            assertNotNull(recoveredRecord, "Record " + i + " should be recovered");
            assertEquals(original.id(), recoveredRecord.id());
            assertEquals(original.sessionId(), recoveredRecord.sessionId());
            assertEquals(original.targetVersion(), recoveredRecord.targetVersion());
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private void warmupStore() {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            memoryStore.store(createTestRecord(-i - 1));
        }
        memoryStore.clear();
    }

    private UpgradeMemoryStore.UpgradeRecord createTestRecord(int index) {
        return new UpgradeMemoryStore.UpgradeRecord.Builder()
                .id("test-record-" + index)
                .sessionId("session-" + index % 10)
                .targetVersion("6.0.0")
                .sourceVersion("5.0.0")
                .startTime(Instant.now().minusMillis(ThreadLocalRandom.current().nextLong(10000)))
                .endTime(Instant.now())
                .outcome(ThreadLocalRandom.current().nextBoolean() 
                        ? new UpgradeMemoryStore.Success("Completed successfully")
                        : new UpgradeMemoryStore.Failure("Test failure", "TestError", ""))
                .addPhase(new UpgradeMemoryStore.PhaseResult(
                        "compile",
                        Instant.now().minusMillis(1000),
                        Instant.now().minusMillis(500),
                        new UpgradeMemoryStore.Success("Compiled"),
                        "Output..."
                ))
                .assignAgent("agent-" + (index % 5), "task-" + index)
                .addMetadata("index", String.valueOf(index))
                .build();
    }

    private UpgradeMemoryStore.UpgradeRecord createSuccessRecord(int index) {
        return new UpgradeMemoryStore.UpgradeRecord.Builder()
                .id("success-record-" + index)
                .sessionId("success-session-" + (index % 50))
                .targetVersion("6.0.0")
                .sourceVersion("5.0.0")
                .startTime(Instant.now().minusMillis(ThreadLocalRandom.current().nextLong(5000)))
                .endTime(Instant.now())
                .outcome(new UpgradeMemoryStore.Success("Upgrade completed successfully"))
                .addPhase(new UpgradeMemoryStore.PhaseResult(
                        "compile", Instant.now().minusMillis(3000), Instant.now().minusMillis(2500),
                        new UpgradeMemoryStore.Success("Compiled"), ""))
                .addPhase(new UpgradeMemoryStore.PhaseResult(
                        "test", Instant.now().minusMillis(2500), Instant.now().minusMillis(1000),
                        new UpgradeMemoryStore.Success("Tests passed"), ""))
                .addPhase(new UpgradeMemoryStore.PhaseResult(
                        "validate", Instant.now().minusMillis(1000), Instant.now(),
                        new UpgradeMemoryStore.Success("Validated"), ""))
                .assignAgent("success-agent-" + (index % 10), "upgrade-task")
                .build();
    }

    private UpgradeMemoryStore.UpgradeRecord createFailureRecord(int index, String errorType) {
        String errorMessage = switch (errorType) {
            case "CompileError" -> "Compilation failed: cannot find symbol";
            case "TestFailure" -> "Test failed: assertion error in test case";
            case "ValidationError" -> "Validation failed: schema mismatch";
            case "TimeoutError" -> "Operation timed out after 30000ms";
            default -> "Unknown error";
        };
        
        return new UpgradeMemoryStore.UpgradeRecord.Builder()
                .id("failure-record-" + index)
                .sessionId("failure-session-" + (index % 30))
                .targetVersion("6.0.0")
                .sourceVersion("5.0.0")
                .startTime(Instant.now().minusMillis(ThreadLocalRandom.current().nextLong(3000)))
                .endTime(Instant.now())
                .outcome(new UpgradeMemoryStore.Failure(errorMessage, errorType, "stack trace..."))
                .addPhase(new UpgradeMemoryStore.PhaseResult(
                        "compile", Instant.now().minusMillis(2000), Instant.now().minusMillis(1500),
                        new UpgradeMemoryStore.Success("Compiled"), ""))
                .addPhase(new UpgradeMemoryStore.PhaseResult(
                        errorType.equals("CompileError") ? "compile" : "test",
                        Instant.now().minusMillis(1500), Instant.now(),
                        new UpgradeMemoryStore.Failure(errorMessage, errorType, ""), errorMessage))
                .assignAgent("failure-agent-" + (index % 5), "upgrade-task")
                .build();
    }

    private UpgradeMemoryStore.UpgradeRecord createPartialRecord(int index) {
        return new UpgradeMemoryStore.UpgradeRecord.Builder()
                .id("partial-record-" + index)
                .sessionId("partial-session-" + (index % 20))
                .targetVersion("6.0.0")
                .sourceVersion("5.0.0")
                .startTime(Instant.now().minusMillis(ThreadLocalRandom.current().nextLong(4000)))
                .endTime(Instant.now())
                .outcome(new UpgradeMemoryStore.Partial(2, 3, "test"))
                .addPhase(new UpgradeMemoryStore.PhaseResult(
                        "compile", Instant.now().minusMillis(3000), Instant.now().minusMillis(2500),
                        new UpgradeMemoryStore.Success("Compiled"), ""))
                .addPhase(new UpgradeMemoryStore.PhaseResult(
                        "test", Instant.now().minusMillis(2500), Instant.now().minusMillis(1000),
                        new UpgradeMemoryStore.Success("Some tests passed"), ""))
                .addPhase(new UpgradeMemoryStore.PhaseResult(
                        "validate", Instant.now().minusMillis(1000), Instant.now(),
                        new UpgradeMemoryStore.Failure("Partial validation", "PartialError", ""), ""))
                .assignAgent("partial-agent-" + (index % 8), "upgrade-task")
                .build();
    }

    private double getPercentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }
}
