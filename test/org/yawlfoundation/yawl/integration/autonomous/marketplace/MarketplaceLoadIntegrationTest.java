package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.yawlfoundation.yawl.elements.YNetRunner;
import org.yawlfoundation.yawl.elements.YWorkItem;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YSpecificationID;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * High-throughput load testing for YAWL Marketplace with virtual threads.
 *
 * Tests performance characteristics under various load patterns using
 * virtual threads for efficient concurrency.
 */
public class MarketplaceLoadIntegrationTest {

    private static final String ENGINE_URL = "http://localhost:8080/yawl";
    private static final String WORKFLOW_URL = "http://localhost:8080/marketplace/workflow";
    private static final int THREAD_POOL_SIZE = 200;
    private static final Duration TEST_TIMEOUT = Duration.ofMinutes(2);

    private HttpClient httpClient;
    private YNetRunner engine;
    private ExecutorService virtualThreadExecutor;

    @BeforeEach
    void setUp() throws Exception {
        // Skip if engine not available
        assumeTrue(isEngineAvailable(), "YAWL Engine not available - skipping tests");

        // Configure virtual thread executor
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // Configure HTTP client with connection pooling
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_2)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

        // Initialize engine
        engine = new YNetRunner();
        assertTrue(engine.start(), "Failed to start YAWL engine");
    }

    private boolean isEngineAvailable() {
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(ENGINE_URL + "/engine"))
                .timeout(Duration.ofSeconds(3))
                .build();

            var response = httpClient.send(request, BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ENABLE_LOAD_TESTS", matches = "true")
    void testSustainedQueryLoad() throws Exception {
        System.out.println("[+] Starting sustained query load test: 100 QPS for 1 minute");

        final int targetQPS = 100;
        final int durationSeconds = 60;
        final int expectedRequests = targetQPS * durationSeconds;

        AtomicLong totalRequests = new AtomicLong(0);
        AtomicLong totalTimeMs = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);

        Instant startTime = Instant.now();
        Instant endTime = startTime.plusSeconds(durationSeconds);

        // Submit sustained load
        CountDownLatch loadComplete = new CountDownLatch(1);
        List<CompletableFuture<Void>> futures = IntStream.range(0, THREAD_POOL_SIZE)
            .mapToObj(i -> CompletableFuture.runAsync(() -> submitLoadRequests(
                targetQPS,
                endTime,
                totalRequests,
                totalTimeMs,
                errorCount,
                loadComplete
            ), virtualThreadExecutor))
            .collect(Collectors.toList());

        // Wait for load completion
        loadComplete.await();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Instant actualEndTime = Instant.now();
        Duration testDuration = Duration.between(startTime, actualEndTime);

        // Calculate metrics
        double actualQPS = totalRequests.get() / testDuration.toSeconds();
        double avgResponseTime = totalRequests.get() > 0 ?
            (double) totalTimeMs.get() / totalRequests.get() : 0;

        // Performance assertions
        System.out.printf("[+] Sustained load results: QPS=%.2f, Requests=%d, Errors=%d, Avg RT=%.2fms%n",
            actualQPS, totalRequests.get(), errorCount.get(), avgResponseTime);

        assertTrue(actualQPS >= targetQPS * 0.95,
            String.format("QPS too low: %.2f < %.2f", actualQPS, targetQPS * 0.95));

        assertEquals(0, errorCount.get(), "No errors allowed in sustained load");

        assertTrue(avgResponseTime < 1000,
            String.format("Average response time too high: %.2fms", avgResponseTime));

        // Assert we completed expected number of requests
        assertTrue(totalRequests.get() >= expectedRequests * 0.9,
            String.format("Insufficient requests: %d < %d",
                totalRequests.get(), expectedRequests * 0.9));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ENABLE_LOAD_TESTS", matches = "true")
    void testBurstLoad() throws Exception {
        System.out.println("[+] Starting burst load test: 500 queries in 10 seconds");

        final int totalQueries = 500;
        final int durationSeconds = 10;
        final int expectedConcurrency = Math.min(THREAD_POOL_SIZE, totalQueries / 2);

        AtomicInteger activeRequests = new AtomicInteger(0);
        CountDownLatch allComplete = new CountDownLatch(totalQueries);
        AtomicLong totalTimeMs = new AtomicLong(0);
        AtomicLong errorCount = AtomicLong.new(0);

        Instant startTime = Instant.now();

        // Submit burst load
        IntStream.range(0, totalQueries).forEach(i -> {
            virtualThreadExecutor.submit(() -> {
                activeRequests.incrementAndGet();
                try {
                    Instant requestStart = Instant.now();

                    // Execute query
                    HttpResponse<String> response = executeQuery();

                    long responseTime = Duration.between(requestStart, Instant.now()).toMillis();
                    totalTimeMs.addAndGet(responseTime);

                    if (response.statusCode() != 200) {
                        errorCount.incrementAndGet();
                    }

                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    activeRequests.decrementAndGet();
                    allComplete.countDown();
                }
            });
        });

        // Wait for completion with timeout
        assertTrue(allComplete.await(durationSeconds + 5, TimeUnit.SECONDS),
            "Burst load did not complete within timeout");

        Instant actualEndTime = Instant.now();
        Duration testDuration = Duration.between(startTime, actualEndTime);

        // Calculate metrics
        double actualQPS = totalQueries / testDuration.toSeconds();
        double avgResponseTime = totalQueries > 0 ?
            (double) totalTimeMs.get() / totalQueries : 0;

        System.out.printf("[+] Burst load results: QPS=%.2f, Duration=%.2fs, Errors=%d, Avg RT=%.2fms%n",
            actualQPS, testDuration.toSeconds(), errorCount.get(), avgResponseTime);

        // Performance assertions
        assertTrue(actualQPS >= totalQueries / durationSeconds * 0.9,
            String.format("Burst QPS too low: %.2f", actualQPS));

        assertEquals(0, errorCount.get(), "No errors allowed in burst load");

        // Check concurrency was achieved
        assertTrue(activeRequests.get() <= expectedConcurrency,
            String.format("Concurrency too high: %d > %d",
                activeRequests.get(), expectedConcurrency));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ENABLE_LOAD_TESTS", matches = "true")
    void testConcurrentReadersAndWriters() throws Exception {
        System.out.println("[+] Starting concurrent readers/writers test");

        final int readerThreads = 15;
        final int writerThreads = 5;
        final int durationSeconds = 30;

        AtomicLong readCount = new AtomicLong(0);
        AtomicLong writeCount = new AtomicLong(0);
        AtomicLong readErrors = new AtomicLong(0);
        AtomicLong writeErrors = new AtomicLong(0);
        AtomicLong readTotalTime = new AtomicLong(0);
        AtomicLong writeTotalTime = new AtomicLong(0);

        Instant startTime = Instant.now();
        Instant endTime = startTime.plusSeconds(durationSeconds);

        // Start readers
        List<CompletableFuture<Void>> readerFutures = IntStream.range(0, readerThreads)
            .mapToObj(i -> CompletableFuture.runAsync(() -> readerWorker(
                endTime, readCount, readErrors, readTotalTime
            ), virtualThreadExecutor))
            .collect(Collectors.toList());

        // Start writers
        List<CompletableFuture<Void>> writerFutures = IntStream.range(0, writerThreads)
            .mapToObj(i -> CompletableFuture.runAsync(() -> writerWorker(
                endTime, writeCount, writeErrors, writeTotalTime
            ), virtualThreadExecutor))
            .collect(Collectors.toList());

        // Wait for completion
        CompletableFuture.allOf(
            readerFutures.toArray(new CompletableFuture[0]),
            writerFutures.toArray(new CompletableFuture[0])
        ).join();

        // Calculate metrics
        double readQPS = readCount.get() / durationSeconds;
        double writeQPS = writeCount.get() / durationSeconds;
        double readAvgRT = readCount.get() > 0 ?
            (double) readTotalTime.get() / readCount.get() : 0;
        double writeAvgRT = writeCount.get() > 0 ?
            (double) writeTotalTime.get() / writeCount.get() : 0;

        System.out.printf("[+] Concurrent workload results - Reads: QPS=%.2f, Errors=%d, Avg RT=%.2fms | Writes: QPS=%.2f, Errors=%d, Avg RT=%.2fms%n",
            readQPS, readErrors.get(), readAvgRT, writeQPS, writeErrors.get(), writeAvgRT);

        // Assertions
        assertTrue(readQPS >= 20, "Reader QPS too low");
        assertTrue(writeQPS >= 5, "Writer QPS too low");
        assertEquals(0, readErrors.get() + writeErrors.get(), "No errors allowed");
        assertTrue(readAvgRT < 500, "Reader response time too high");
        assertTrue(writeAvgRT < 1000, "Writer response time too high");

        // Verify read/write ratio is reasonable
        double ratio = readCount.get() / Math.max(1, writeCount.get());
        assertTrue(ratio >= 2 && ratio <= 10,
            String.format("Read/write ratio out of bounds: %.2f", ratio));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ENABLE_LOAD_TESTS", matches = "true")
    void testLatencyPercentiles() throws Exception {
        System.out.println("[+] Starting latency percentiles test");

        final int sampleSize = 1000;
        final int targetP50 = 200; // 200ms
        final int targetP95 = 800; // 800ms
        final int targetP99 = 1500; // 1.5s

        // Collect latency samples
        List<Long> latencies = new ConcurrentLinkedQueue<>();
        CountDownLatch sampleComplete = new CountDownLatch(sampleSize);

        IntStream.range(0, sampleSize).parallel().forEach(i -> {
            try {
                Instant start = Instant.now();
                HttpResponse<String> response = executeQuery();
                long latency = Duration.between(start, Instant.now()).toMillis();

                if (response.statusCode() == 200) {
                    latencies.add(latency);
                }

            } catch (Exception e) {
                // Sample error, skip this sample
            } finally {
                sampleComplete.countDown();
            }
        });

        assertTrue(sampleComplete.await(30, TimeUnit.SECONDS),
            "Latency sampling did not complete");

        // Calculate percentiles
        List<Long> sortedLatencies = latencies.stream()
            .sorted()
            .collect(Collectors.toList());

        int p50Index = (int) (sortedLatencies.size() * 0.50);
        int p95Index = (int) (sortedLatencies.size() * 0.95);
        int p99Index = (int) (sortedLatencies.size() * 0.99);

        long actualP50 = sortedLatencies.get(p50Index);
        long actualP95 = sortedLatencies.get(p95Index);
        long actualP99 = sortedLatencies.get(p99Index);

        System.out.printf("[+] Latency percentiles: P50=%dms, P95=%dms, P99=%dms%n",
            actualP50, actualP95, actualP99);

        // Assertions for percentiles
        assertTrue(actualP50 <= targetP50,
            String.format("P50 too high: %dms > %dms", actualP50, targetP50));
        assertTrue(actualP95 <= targetP95,
            String.format("P95 too high: %dms > %dms", actualP95, targetP95));
        assertTrue(actualP99 <= targetP99,
            String.format("P99 too high: %dms > %dms", actualP99, targetP99));

        // Additional assertions
        assertTrue(sortedLatencies.size() >= sampleSize * 0.9,
            String.format("Insufficient samples: %d < %d",
                sortedLatencies.size(), sampleSize * 0.9));

        // Check for outliers (samples > 5x P99)
        long outlierThreshold = actualP99 * 5;
        long outlierCount = sortedLatencies.stream()
            .filter(l -> l > outlierThreshold)
            .count();

        assertTrue(outlierCount <= sortedLatencies.size() * 0.01,
            String.format("Too many outliers: %d > 1%%", outlierCount));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ENABLE_LOAD_TESTS", matches = "true")
    void testMemoryUsageUnderSustainedLoad() throws Exception {
        System.out.println("[+] Starting memory usage test under sustained load");

        final int durationSeconds = 60;
        final int loadThreads = 50;

        // Monitor memory before load
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // Execute sustained load
        CountDownLatch loadComplete = new CountDownLatch(1);
        AtomicLong requestCount = new AtomicLong(0);

        List<CompletableFuture<Void>> futures = IntStream.range(0, loadThreads)
            .mapToObj(i -> CompletableFuture.runAsync(() -> memoryLoadWorker(
                durationSeconds, requestCount, loadComplete
            ), virtualThreadExecutor))
            .collect(Collectors.toList());

        // Monitor memory during load
        ScheduledExecutorService monitorExecutor = Executors.newVirtualThreadPerTaskExecutor();
        List<Long> memorySamples = new ConcurrentLinkedQueue<>();

        monitorExecutor.scheduleAtFixedRate(() -> {
            long memoryUsed = runtime.totalMemory() - runtime.freeMemory();
            memorySamples.add(memoryUsed);
        }, 0, 1, TimeUnit.SECONDS);

        // Wait for load completion
        loadComplete.await();
        monitorExecutor.shutdown();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Calculate memory metrics
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = memoryAfter - memoryBefore;
        long maxMemoryUsed = memorySamples.stream()
            .mapToLong(Long::longValue)
            .max()
            .orElse(memoryAfter);

        double memoryIncreasePercent = (double) memoryIncrease / memoryBefore * 100;
        double avgMemoryUsed = memorySamples.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);

        System.out.printf("[+] Memory usage results: Before=%dMB, After=%dMB, Increase=%.1f%%, Max=%dMB, Avg=%dMB, Requests=%d%n",
            memoryBefore / (1024 * 1024),
            memoryAfter / (1024 * 1024),
            memoryIncreasePercent,
            maxMemoryUsed / (1024 * 1024),
            (long) (avgMemoryUsed / (1024 * 1024)),
            requestCount.get());

        // Memory assertions
        assertTrue(memoryIncreasePercent < 50,
            String.format("Memory increase too high: %.1f%% > 50%%", memoryIncreasePercent));

        // Ensure no memory leaks (memory should stabilize)
        double memoryGrowthRate = (maxMemoryUsed - memoryBefore) /
            Math.max(1, durationSeconds);
        assertTrue(memoryGrowthRate < 1024 * 1024, // Less than 1MB/s growth
            String.format("Memory growth rate too high: %.1f MB/s",
                memoryGrowthRate / (1024 * 1024)));

        // Verify GC didn't run excessively (memory shouldn't decrease during load)
        assertTrue(maxMemoryUsed >= memoryBefore * 0.9,
            String.format("Memory decreased unexpectedly: %d < %d",
                maxMemoryUsed, memoryBefore * 0.9));
    }

    // Helper methods for test workers

    private void submitLoadRequests(int targetQPS, Instant endTime,
                                   AtomicLong totalRequests, AtomicLong totalTimeMs,
                                   AtomicLong errorCount, CountDownLatch complete) {
        try {
            while (Instant.now().isBefore(endTime)) {
                Instant start = Instant.now();

                try {
                    HttpResponse<String> response = executeQuery();
                    long responseTime = Duration.between(start, Instant.now()).toMillis();

                    if (response.statusCode() == 200) {
                        totalRequests.incrementAndGet();
                        totalTimeMs.addAndGet(responseTime);
                    } else {
                        errorCount.incrementAndGet();
                    }

                    // Calculate delay to maintain target QPS
                    long elapsed = Duration.between(start, Instant.now()).toMillis();
                    long delay = Math.max(0, (1000 / targetQPS) - elapsed);

                    Thread.sleep(delay);

                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    Thread.sleep(100); // Backoff on error
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            complete.countDown();
        }
    }

    private void readerWorker(Instant endTime, AtomicLong readCount,
                            AtomicLong readErrors, AtomicLong readTotalTime) {
        try {
            while (Instant.now().isBefore(endTime)) {
                try {
                    Instant start = Instant.now();
                    HttpResponse<String> response = executeQuery();
                    long responseTime = Duration.between(start, Instant.now()).toMillis();

                    if (response.statusCode() == 200) {
                        readCount.incrementAndGet();
                        readTotalTime.addAndGet(responseTime);
                    } else {
                        readErrors.incrementAndGet();
                    }

                } catch (Exception e) {
                    readErrors.incrementAndGet();
                }

                // Random jitter to avoid synchronization
                Thread.sleep(50 + ThreadLocalRandom.current().nextInt(100));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void writerWorker(Instant endTime, AtomicLong writeCount,
                            AtomicLong writeErrors, AtomicLong writeTotalTime) {
        try {
            while (Instant.now().isBefore(endTime)) {
                try {
                    Instant start = Instant.now();
                    HttpResponse<String> response = executeWorkflowUpdate();
                    long responseTime = Duration.between(start, Instant.now()).toMillis();

                    if (response.statusCode() == 200) {
                        writeCount.incrementAndGet();
                        writeTotalTime.addAndGet(responseTime);
                    } else {
                        writeErrors.incrementAndGet();
                    }

                } catch (Exception e) {
                    writeErrors.incrementAndGet();
                }

                // Slower than readers to simulate real workload
                Thread.sleep(200 + ThreadLocalRandom.current().nextInt(300));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void memoryLoadWorker(int durationSeconds, AtomicLong requestCount,
                                 CountDownLatch complete) {
        Instant endTime = Instant.now().plusSeconds(durationSeconds);
        try {
            while (Instant.now().isBefore(endTime)) {
                Instant start = Instant.now();

                try {
                    HttpResponse<String> response = executeQuery();
                    if (response.statusCode() == 200) {
                        requestCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore errors in memory test
                }

                // Maintain steady load with some randomness
                long elapsed = Duration.between(start, Instant.now()).toMillis();
                long delay = Math.max(10, 50 - elapsed + ThreadLocalRandom.current().nextInt(20));
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            complete.countDown();
        }
    }

    private HttpResponse<String> executeQuery() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(WORKFLOW_URL + "/query"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .GET()
            .build();

        return httpClient.send(request, BodyHandlers.ofString());
    }

    private HttpResponse<String> executeWorkflowUpdate() throws IOException, InterruptedException {
        String updatePayload = "{\"operation\": \"update\", \"timestamp\": \"" +
            LocalDateTime.now() + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(WORKFLOW_URL + "/update"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(updatePayload))
            .build();

        return httpClient.send(request, BodyHandlers.ofString());
    }

    @AfterEach
    void tearDown() {
        if (virtualThreadExecutor != null) {
            virtualThreadExecutor.shutdownNow();
        }
        if (engine != null) {
            engine.stop();
        }
    }
}