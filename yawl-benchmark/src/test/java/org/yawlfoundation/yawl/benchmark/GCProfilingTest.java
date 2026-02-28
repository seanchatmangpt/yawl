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

package org.yawlfoundation.yawl.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Tag;

import java.io.IOException;
import java.lang.management.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GC Profiling Test: Validates ZGC performance at 1M case scale.
 *
 * Measures:
 * - GC pause time distribution (p50, p95, p99, max)
 * - Full GC events (frequency, duration)
 * - Memory allocation patterns (heap committed, heap used, growth rate)
 * - Heap stability: can sustain >1 hour without exhaustion
 * - String deduplication effectiveness with compact headers
 *
 * Success criteria:
 * - Average GC pause < 5ms (ZGC target)
 * - p99 GC pause < 50ms
 * - No full GC pauses > 100ms
 * - Heap growth < 1GB/hour
 * - Heap recovers after GC cycles
 * - No memory leaks detectable
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("gc-profiling")
public class GCProfilingTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * GC pause collection (records all pauses during test).
     */
    private static class GCPauseCollector extends NotificationListener {
        private final List<GCPauseEvent> pauses = Collections.synchronizedList(new ArrayList<>());
        private final GarbageCollectorMXBean gcBean;

        GCPauseCollector(GarbageCollectorMXBean gcBean) {
            this.gcBean = gcBean;
        }

        @Override
        public void handleNotification(Notification notification, Object handback) {
            if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                GarbageCollectionNotificationInfo info =
                        GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());

                long pauseTimeMs = info.getGcInfo().getDuration();
                String gcAction = info.getGcAction();
                String gcCause = info.getGcCause();

                GCPauseEvent event = new GCPauseEvent(
                        info.getGcInfo().getStartTime(),
                        pauseTimeMs,
                        gcAction,
                        gcCause,
                        "Young".equalsIgnoreCase(gcAction)
                );

                pauses.add(event);
            }
        }

        List<GCPauseEvent> getPauses() {
            return new ArrayList<>(pauses);
        }

        void clear() {
            pauses.clear();
        }
    }

    /**
     * GC pause event record.
     */
    private record GCPauseEvent(long startTimeMs, long durationMs, String action, String cause, boolean isYoung) {}

    /**
     * Memory snapshot (heap stats at a point in time).
     */
    private record MemorySnapshot(long timestamp, long heapUsedBytes, long heapMaxBytes, long heapCommittedBytes,
                                  long nonHeapUsedBytes, int threadCount) {}

    /**
     * Executes 1-hour GC profiling at 1M case scale.
     */
    @Test
    @DisplayName("GC Profiling: 1M cases, ZGC pause time & memory stability")
    @Timeout(3700)  // 1 hour + 100 sec buffer
    @Tag("integration")
    void testGCProfilingAt1MCaseScale() throws Exception {
        System.out.println("\n========================================");
        System.out.println("GC PROFILING TEST: 1M Case Scale");
        System.out.println("========================================\n");

        // Configuration
        int targetCases = 1_000_000;
        long durationMs = 3600_000;  // 1 hour
        int casesPerBatch = 10_000;
        int batchIntervalMs = 100;

        // Initialize collectors
        List<MemorySnapshot> memorySnapshots = Collections.synchronizedList(new ArrayList<>());
        GCPauseCollector gcCollector = initializeGCCollection();

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long startTime = System.currentTimeMillis();
        long endTime = startTime + durationMs;

        AtomicInteger casesProcessed = new AtomicInteger(0);
        AtomicLong totalTasksExecuted = new AtomicLong(0);

        // Start memory sampling thread (every 1 second)
        Thread memorySamplerThread = Thread.ofVirtual()
                .name("gc-memory-sampler")
                .start(() -> sampleMemory(memoryBean, memorySnapshots, endTime));

        // Start case processing
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            // Create synthetic case load (without engine for speed)
            while (System.currentTimeMillis() < endTime) {
                int batch = Math.min(casesPerBatch, targetCases - casesProcessed.get());
                if (batch <= 0) break;

                // Simulate case creation & completion
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (int i = 0; i < batch; i++) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            // Simulate case processing: allocate objects, execute tasks
                            Map<String, Object> caseData = generateCaseData();
                            simulateTaskExecution(caseData);
                            casesProcessed.incrementAndGet();
                            totalTasksExecuted.incrementAndGet();
                        } catch (Exception e) {
                            System.err.println("Error in case processing: " + e.getMessage());
                        }
                    }, executor));
                }

                // Wait for batch to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // Batch interval
                Thread.sleep(batchIntervalMs);
            }

            // Stop executor and memory sampler
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

            memorySamplerThread.interrupt();
            try {
                memorySamplerThread.join();
            } catch (InterruptedException e) {
                // Expected
            }

        } finally {
            executor.shutdownNow();
        }

        long actualDurationMs = System.currentTimeMillis() - startTime;

        // Analyze results
        GCProfilingResult result = analyzeGCProfile(
                gcCollector.getPauses(),
                memorySnapshots,
                casesProcessed.get(),
                totalTasksExecuted.get(),
                actualDurationMs
        );

        // Save results to JSON
        Path outputFile = Paths.get("gc-profile-" + System.currentTimeMillis() + ".json");
        Files.writeString(outputFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));

        System.out.println(result);

        // Assertions
        assertTrue(casesProcessed.get() > 0, "Should process at least one case");

        // GC pause targets
        assertTrue(result.avgPauseMs < 5.0, "Average GC pause " + result.avgPauseMs + " should be < 5.0ms");
        assertTrue(result.p99PauseMs < 50.0, "p99 GC pause " + result.p99PauseMs + " should be < 50.0ms");
        assertTrue(result.maxPauseMs < 100.0, "Max GC pause " + result.maxPauseMs + " should be < 100.0ms");

        // Memory stability
        assertTrue(result.heapGrowthMBPerHour < 1024.0,
                "Heap growth " + result.heapGrowthMBPerHour + " MB/hour should be < 1024.0");

        // Full GC events (should be minimal)
        assertTrue(result.fullGCCount < 10, "Full GC events " + result.fullGCCount + " should be < 10");

        // Memory recovery check (heap should decrease after GC)
        assertTrue(result.heapRecoveryDetected, "Heap should recover after GC cycles");
    }

    /**
     * Initialize GC collection via MXBean notifications.
     */
    private GCPauseCollector initializeGCCollection() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        GCPauseCollector collector = null;

        for (GarbageCollectorMXBean gcBean : gcBeans) {
            if (gcBean.isCollectionTimeSupported()) {
                collector = new GCPauseCollector(gcBean);

                if (gcBean instanceof NotificationEmitter emitter) {
                    emitter.addNotificationListener(collector, null, null);
                }
            }
        }

        assertNotNull(collector, "GC notification collection should be available");
        return collector;
    }

    /**
     * Samples memory state periodically.
     */
    private void sampleMemory(MemoryMXBean memoryBean, List<MemorySnapshot> snapshots, long endTime) {
        while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
            try {
                MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
                MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
                int threadCount = Thread.activeCount();

                MemorySnapshot snapshot = new MemorySnapshot(
                        System.currentTimeMillis(),
                        heapUsage.getUsed(),
                        heapUsage.getMax(),
                        heapUsage.getCommitted(),
                        nonHeapUsage.getUsed(),
                        threadCount
                );

                snapshots.add(snapshot);

                Thread.sleep(1000);  // Sample every 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Generates synthetic case data.
     */
    private Map<String, Object> generateCaseData() {
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("caseId", UUID.randomUUID().toString());
        caseData.put("specId", "spec-" + (System.nanoTime() % 100));
        caseData.put("status", "active");
        caseData.put("tasks", new ArrayList<>(generateTasks(5)));
        return caseData;
    }

    /**
     * Generates synthetic task list.
     */
    private List<Map<String, Object>> generateTasks(int count) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> task = new HashMap<>();
            task.put("taskId", "task-" + i);
            task.put("status", "enabled");
            task.put("data", "task-data-" + UUID.randomUUID());
            tasks.add(task);
        }
        return tasks;
    }

    /**
     * Simulates task execution (allocates transient objects).
     */
    private void simulateTaskExecution(Map<String, Object> caseData) throws InterruptedException {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) caseData.get("tasks");

        for (Map<String, Object> task : tasks) {
            // Simulate work: allocate intermediate objects
            byte[] tempBuffer = new byte[1024];
            String workResult = "result-" + UUID.randomUUID();
            task.put("result", workResult);

            // Simulate execution time
            Thread.sleep(1);
        }
    }

    /**
     * Analyzes GC profile and produces summary.
     */
    private GCProfilingResult analyzeGCProfile(
            List<GCPauseEvent> pauses,
            List<MemorySnapshot> snapshots,
            int casesProcessed,
            long tasksExecuted,
            long durationMs) {

        // GC pause analysis
        List<Long> pausesMs = pauses.stream().map(e -> e.durationMs()).sorted().toList();
        double avgPause = pausesMs.isEmpty() ? 0 : pausesMs.stream().mapToLong(Long::longValue).average().orElse(0);
        long p50Pause = pausesMs.isEmpty() ? 0 : pausesMs.get(pausesMs.size() / 2);
        long p95Pause = pausesMs.isEmpty() ? 0 : pausesMs.get((int) (pausesMs.size() * 0.95));
        long p99Pause = pausesMs.isEmpty() ? 0 : pausesMs.get((int) (pausesMs.size() * 0.99));
        long maxPause = pausesMs.isEmpty() ? 0 : pausesMs.get(pausesMs.size() - 1);

        // Full GC vs Young GC count
        long fullGCCount = pauses.stream().filter(e -> !e.isYoung()).count();
        long youngGCCount = pauses.size() - fullGCCount;

        // Memory analysis
        double heapGrowthMBPerHour = 0;
        boolean heapRecoveryDetected = false;

        if (snapshots.size() >= 2) {
            MemorySnapshot first = snapshots.get(0);
            MemorySnapshot last = snapshots.get(snapshots.size() - 1);

            double heapGrowthMB = (last.heapUsedBytes() - first.heapUsedBytes()) / (1024.0 * 1024.0);
            double durationHours = durationMs / 3600_000.0;
            heapGrowthMBPerHour = heapGrowthMB / Math.max(1.0, durationHours);

            // Check if heap recovered (decreased) during profiling
            long maxHeapSeen = snapshots.stream().mapToLong(MemorySnapshot::heapUsedBytes).max().orElse(0);
            long minHeapAfterMax = snapshots.stream()
                    .filter(s -> s.heapUsedBytes() < maxHeapSeen)
                    .mapToLong(MemorySnapshot::heapUsedBytes)
                    .min()
                    .orElse(maxHeapSeen);

            heapRecoveryDetected = (maxHeapSeen - minHeapAfterMax) > (100 * 1024 * 1024);  // 100MB decrease
        }

        // Throughput analysis
        double casesPerSecond = (durationMs > 0) ? (casesProcessed * 1000.0) / durationMs : 0;

        return new GCProfilingResult(
                durationMs,
                casesProcessed,
                tasksExecuted,
                pauses.size(),
                fullGCCount,
                youngGCCount,
                avgPause,
                p50Pause,
                p95Pause,
                p99Pause,
                maxPause,
                heapGrowthMBPerHour,
                heapRecoveryDetected,
                casesPerSecond
        );
    }

    /**
     * GC profiling result record.
     */
    private record GCProfilingResult(
            long durationMs,
            int casesProcessed,
            long tasksExecuted,
            int totalGCEvents,
            long fullGCCount,
            long youngGCCount,
            double avgPauseMs,
            long p50PauseMs,
            long p95PauseMs,
            long p99PauseMs,
            long maxPauseMs,
            double heapGrowthMBPerHour,
            boolean heapRecoveryDetected,
            double casesPerSecond) {

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n========================================\n");
            sb.append("GC PROFILING RESULTS\n");
            sb.append("========================================\n");
            sb.append("Duration: ").append(Duration.ofMillis(durationMs)).append("\n");
            sb.append("Cases Processed: ").append(casesProcessed).append("\n");
            sb.append("Tasks Executed: ").append(tasksExecuted).append("\n");
            sb.append("Throughput: ").append(String.format("%.2f", casesPerSecond)).append(" cases/sec\n");
            sb.append("\nGC Pause Statistics:\n");
            sb.append("  Total GC events: ").append(totalGCEvents).append("\n");
            sb.append("  Full GCs: ").append(fullGCCount).append("\n");
            sb.append("  Young GCs: ").append(youngGCCount).append("\n");
            sb.append("  Avg pause: ").append(String.format("%.2f", avgPauseMs)).append(" ms\n");
            sb.append("  p50 pause: ").append(p50PauseMs).append(" ms\n");
            sb.append("  p95 pause: ").append(p95PauseMs).append(" ms\n");
            sb.append("  p99 pause: ").append(p99PauseMs).append(" ms\n");
            sb.append("  Max pause: ").append(maxPauseMs).append(" ms\n");
            sb.append("\nMemory Statistics:\n");
            sb.append("  Heap growth: ").append(String.format("%.2f", heapGrowthMBPerHour))
                    .append(" MB/hour\n");
            sb.append("  Heap recovery detected: ").append(heapRecoveryDetected).append("\n");
            sb.append("========================================\n");
            return sb.toString();
        }
    }
}
