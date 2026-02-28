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

package org.yawlfoundation.yawl.benchmark.soak;

import java.io.IOException;
import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection harness for long-running benchmark tests.
 *
 * <p>Periodically samples JVM metrics via MXBeans and writes them to a JSONL stream
 * for analysis. Supports real-time monitoring of heap growth, GC behavior, and
 * thread activity during stress tests.</p>
 *
 * <p>Thread-safe and designed for background collection during 24+ hour runs.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class BenchmarkMetricsCollector {

    /**
     * Immutable metric snapshot sampled from JVM MXBeans.
     */
    public record MetricSnapshot(
            long timestamp,
            long heapUsedMB,
            long heapMaxMB,
            long heapCommittedMB,
            long gcCollectionCount,
            long gcCollectionTimeMs,
            int threadCount,
            int peakThreadCount,
            long casesProcessed,
            double throughputCasesPerSec) {

        /**
         * Returns a JSON representation of this snapshot.
         */
        public String toJson() {
            return String.format(
                    "{\"timestamp\":%d,\"heap_used_mb\":%d,\"heap_max_mb\":%d," +
                    "\"heap_committed_mb\":%d,\"gc_collection_count\":%d," +
                    "\"gc_collection_time_ms\":%d,\"thread_count\":%d," +
                    "\"peak_thread_count\":%d,\"cases_processed\":%d," +
                    "\"throughput_cases_per_sec\":%.2f}",
                    timestamp, heapUsedMB, heapMaxMB, heapCommittedMB,
                    gcCollectionCount, gcCollectionTimeMs, threadCount,
                    peakThreadCount, casesProcessed, throughputCasesPerSec);
        }
    }

    private final MemoryMXBean memoryMXBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    private final ThreadMXBean threadMXBean;
    private final Path outputPath;
    private final long intervalMs;
    private final ExecutorService executor;
    private final AtomicLong casesProcessed;
    private final AtomicLong lastCaseCount;
    private final AtomicLong lastSampleTime;
    private volatile boolean running;

    /**
     * Create a metrics collector writing to the specified file.
     *
     * @param outputPath Path to write JSONL metrics stream
     * @param intervalSeconds Sampling interval in seconds
     */
    public BenchmarkMetricsCollector(Path outputPath, int intervalSeconds) {
        this.outputPath = outputPath;
        this.intervalMs = intervalSeconds * 1000L;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = Thread.ofVirtual().name("metrics-collector").unstarted(r);
            t.setDaemon(true);
            return t;
        });
        this.casesProcessed = new AtomicLong(0);
        this.lastCaseCount = new AtomicLong(0);
        this.lastSampleTime = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * Start background metrics collection.
     * Collection continues until {@link #stop()} is called.
     */
    public void start() {
        running = true;
        executor.submit(this::collectLoop);
    }

    /**
     * Stop background metrics collection and wait for completion.
     */
    public void stop() throws InterruptedException {
        running = false;
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    /**
     * Record that a case was processed (for throughput calculation).
     */
    public void recordCaseProcessed() {
        casesProcessed.incrementAndGet();
    }

    /**
     * Record multiple cases processed (batch operation).
     */
    public void recordCasesProcessed(long count) {
        casesProcessed.addAndGet(count);
    }

    /**
     * Capture current JVM metrics without writing (for testing/manual checks).
     */
    public MetricSnapshot captureSnapshot() {
        long timestamp = System.currentTimeMillis();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long heapUsedMB = heapUsage.getUsed() / (1024 * 1024);
        long heapMaxMB = heapUsage.getMax() / (1024 * 1024);
        long heapCommittedMB = heapUsage.getCommitted() / (1024 * 1024);

        long totalGCCount = 0;
        long totalGCTime = 0;
        for (GarbageCollectorMXBean bean : gcBeans) {
            totalGCCount += bean.getCollectionCount();
            totalGCTime += bean.getCollectionTime();
        }

        int threadCount = threadMXBean.getThreadCount();
        int peakThreadCount = threadMXBean.getPeakThreadCount();

        long currentCaseCount = casesProcessed.get();
        long currentTime = System.currentTimeMillis();
        long timeDeltaMs = currentTime - lastSampleTime.get();
        long caseDelta = currentCaseCount - lastCaseCount.get();
        double throughput = (timeDeltaMs > 0)
                ? (caseDelta * 1000.0) / timeDeltaMs
                : 0.0;

        return new MetricSnapshot(
                timestamp,
                heapUsedMB,
                heapMaxMB,
                heapCommittedMB,
                totalGCCount,
                totalGCTime,
                threadCount,
                peakThreadCount,
                currentCaseCount,
                throughput);
    }

    /**
     * Main collection loop. Runs on background thread.
     */
    private void collectLoop() {
        try {
            // Create or append to output file
            boolean fileExists = Files.exists(outputPath);
            while (running) {
                MetricSnapshot snapshot = captureSnapshot();
                lastCaseCount.set(snapshot.casesProcessed());
                lastSampleTime.set(snapshot.timestamp());

                // Append JSON line to file
                String jsonLine = snapshot.toJson() + "\n";
                Files.writeString(
                        outputPath,
                        jsonLine,
                        StandardCharsets.UTF_8,
                        fileExists
                                ? StandardOpenOption.APPEND
                                : StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
                fileExists = true;

                Thread.sleep(intervalMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println("Failed to write metrics: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
