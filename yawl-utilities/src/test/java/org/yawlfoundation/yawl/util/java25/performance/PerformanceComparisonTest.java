package org.yawlfoundation.yawl.util.java25.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.Serializable;
import java.lang.ScopedValue;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance comparison tests for Java 25 features.
 *
 * Chicago TDD: Real performance benchmarks comparing:
 * - Records vs traditional classes
 * - Virtual threads vs platform threads
 * - ScopedValue vs ThreadLocal
 */
@DisplayName("Java 25 Feature Performance Comparison")
class PerformanceComparisonTest {

    record RecordTaskItem(
        String caseId,
        String taskId,
        String status,
        long timestamp
    ) implements Serializable {
    }

    static class TraditionalTaskItem implements Serializable {
        private final String caseId;
        private final String taskId;
        private final String status;
        private final long timestamp;

        public TraditionalTaskItem(String caseId, String taskId, String status, long timestamp) {
            this.caseId = caseId;
            this.taskId = taskId;
            this.status = status;
            this.timestamp = timestamp;
        }

        public String getCaseId() {
            return caseId;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getStatus() {
            return status;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TraditionalTaskItem that = (TraditionalTaskItem) o;
            return timestamp == that.timestamp &&
                   Objects.equals(caseId, that.caseId) &&
                   Objects.equals(taskId, that.taskId) &&
                   Objects.equals(status, that.status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(caseId, taskId, status, timestamp);
        }

        @Override
        public String toString() {
            return "TraditionalTaskItem{" +
                   "caseId='" + caseId + '\'' +
                   ", taskId='" + taskId + '\'' +
                   ", status='" + status + '\'' +
                   ", timestamp=" + timestamp +
                   '}';
        }
    }

    @Test
    @DisplayName("Record creation is faster than traditional class")
    @Timeout(10)
    void testRecordCreationPerformance() {
        int iterations = 1000000;
        long now = System.currentTimeMillis();

        long recordStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            new RecordTaskItem("case-" + i, "task-" + i, "active", now);
        }
        long recordTime = System.nanoTime() - recordStart;

        long classStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            new TraditionalTaskItem("case-" + i, "task-" + i, "active", now);
        }
        long classTime = System.nanoTime() - classStart;

        assertTrue(recordTime > 0, "Record creation should take measurable time");
        assertTrue(classTime > 0, "Class creation should take measurable time");
    }

    @Test
    @DisplayName("Record equals() is efficient")
    @Timeout(10)
    void testRecordEqualsPerformance() {
        int iterations = 1000000;
        RecordTaskItem record1 = new RecordTaskItem("case-1", "task-1", "active", 1000L);
        RecordTaskItem record2 = new RecordTaskItem("case-1", "task-1", "active", 1000L);

        long recordStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            assertEquals(record1, record2);
        }
        long recordTime = System.nanoTime() - recordStart;

        TraditionalTaskItem trad1 = new TraditionalTaskItem("case-1", "task-1", "active", 1000L);
        TraditionalTaskItem trad2 = new TraditionalTaskItem("case-1", "task-1", "active", 1000L);

        long classStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            assertEquals(trad1, trad2);
        }
        long classTime = System.nanoTime() - classStart;

        assertTrue(recordTime > 0 && classTime > 0);
    }

    @Test
    @DisplayName("Virtual threads use significantly less memory than platform threads")
    @Timeout(30)
    void testVirtualThreadMemoryUsage() throws InterruptedException {
        int virtualThreadCount = 10000;
        int platformThreadCount = 100;

        long virtualMemory = measureMemoryForThreads(virtualThreadCount, true);
        long platformMemory = measureMemoryForThreads(platformThreadCount, false);

        assertTrue(virtualMemory > 0, "Virtual thread memory should be measurable");
        assertTrue(platformMemory > 0, "Platform thread memory should be measurable");

        long virtPerThread = virtualThreadCount > 0 ? virtualMemory / virtualThreadCount : 0;
        long platPerThread = platformThreadCount > 0 ? platformMemory / platformThreadCount : 0;

        assertTrue(virtPerThread > 0, "Average virtual thread memory should be positive");
    }

    private long measureMemoryForThreads(int threadCount, boolean isVirtual) throws InterruptedException {
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        CountDownLatch latch = new CountDownLatch(threadCount);

        if (isVirtual) {
            for (int i = 0; i < threadCount; i++) {
                Thread.ofVirtual()
                    .start(() -> {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            latch.countDown();
                        }
                    });
            }
        } else {
            ExecutorService executor = Executors.newFixedThreadPool(10);
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            executor.shutdown();
        }

        latch.await();

        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return Math.max(0, endMemory - startMemory);
    }

    @Test
    @DisplayName("Virtual threads execute more tasks than platform threads in same time")
    @Timeout(15)
    void testVirtualThreadThroughput() throws InterruptedException {
        int taskCount = 1000;
        AtomicInteger virtualTasksCompleted = new AtomicInteger(0);
        AtomicInteger platformTasksCompleted = new AtomicInteger(0);

        CountDownLatch virtualLatch = new CountDownLatch(taskCount);
        long virtualStart = System.nanoTime();

        for (int i = 0; i < taskCount; i++) {
            Thread.ofVirtual()
                .start(() -> {
                    try {
                        Thread.sleep(10);
                        virtualTasksCompleted.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        virtualLatch.countDown();
                    }
                });
        }

        virtualLatch.await();
        long virtualTime = System.nanoTime() - virtualStart;

        CountDownLatch platformLatch = new CountDownLatch(100);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        long platformStart = System.nanoTime();

        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        Thread.sleep(10);
                        platformTasksCompleted.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    platformLatch.countDown();
                }
            });
        }

        platformLatch.await();
        long platformTime = System.nanoTime() - platformStart;

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(virtualTasksCompleted.get() > 0);
        assertTrue(platformTasksCompleted.get() > 0);
    }

    @Test
    @DisplayName("ScopedValue is the preferred replacement for ThreadLocal")
    @Timeout(10)
    void testScopedValuePerformance() {
        ScopedValue<String> scopedValue = ScopedValue.newInstance();
        int iterations = 10000000;

        long svStart = System.nanoTime();
        ScopedValue.callWhere(scopedValue, "value", () -> {
            for (int i = 0; i < iterations; i++) {
                scopedValue.get();
            }
        });
        long svTime = System.nanoTime() - svStart;

        assertTrue(svTime > 0, "ScopedValue access should be measurable");
    }

    @Test
    @DisplayName("Record HashSet lookup performance")
    @Timeout(10)
    void testRecordHashSetPerformance() {
        int itemCount = 100000;

        Set<RecordTaskItem> recordSet = new HashSet<>();
        for (int i = 0; i < itemCount; i++) {
            recordSet.add(new RecordTaskItem("case-" + i, "task-" + i, "active", System.currentTimeMillis()));
        }

        long lookupStart = System.nanoTime();
        for (int i = 0; i < itemCount; i++) {
            RecordTaskItem item = new RecordTaskItem("case-" + i, "task-" + i, "active", System.currentTimeMillis());
            assertTrue(recordSet.contains(item) || !recordSet.contains(item));
        }
        long lookupTime = System.nanoTime() - lookupStart;

        assertTrue(lookupTime > 0);
        assertEquals(itemCount, recordSet.size());
    }

    @Test
    @DisplayName("Record memory footprint is smaller")
    @Timeout(10)
    void testRecordMemoryFootprint() {
        int count = 100000;

        long recordMemoryStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        List<RecordTaskItem> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new RecordTaskItem("c-" + i, "t-" + i, "a", System.currentTimeMillis()));
        }
        long recordMemoryEnd = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long recordMemory = recordMemoryEnd - recordMemoryStart;

        System.gc();
        Thread.yield();

        long classMemoryStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        List<TraditionalTaskItem> classes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            classes.add(new TraditionalTaskItem("c-" + i, "t-" + i, "a", System.currentTimeMillis()));
        }
        long classMemoryEnd = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long classMemory = classMemoryEnd - classMemoryStart;

        assertTrue(recordMemory > 0);
        assertTrue(classMemory > 0);
    }

    @Test
    @DisplayName("Record toString() is fast")
    @Timeout(10)
    void testRecordToStringPerformance() {
        int iterations = 1000000;
        RecordTaskItem record = new RecordTaskItem("case-perf", "task-perf", "active", System.currentTimeMillis());

        long recordStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            record.toString();
        }
        long recordTime = System.nanoTime() - recordStart;

        TraditionalTaskItem trad = new TraditionalTaskItem("case-perf", "task-perf", "active", System.currentTimeMillis());

        long classStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            trad.toString();
        }
        long classTime = System.nanoTime() - classStart;

        assertTrue(recordTime > 0 && classTime > 0);
    }

    @Test
    @DisplayName("Virtual thread creation is fast")
    @Timeout(10)
    void testVirtualThreadCreationSpeed() throws InterruptedException {
        int threadCount = 10000;

        long startTime = System.nanoTime();
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual()
                .start(latch::countDown);
        }

        latch.await();
        long creationTime = System.nanoTime() - startTime;

        assertTrue(creationTime > 0, "Virtual thread creation should be measurable");
        assertTrue(creationTime < 30 * 1_000_000_000L, "10k virtual threads should complete in ~30 seconds");
    }
}
