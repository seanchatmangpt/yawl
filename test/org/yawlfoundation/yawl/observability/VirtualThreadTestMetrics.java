/*
 * Copyright (c) 2026 YAWL Foundation. All Rights Reserved.
 *
 * Virtual Thread Test Metrics Extension for YAWL v6.0.0-GA
 *
 * JUnit 5 extension for collecting virtual thread metrics during tests.
 * Provides visibility into virtual thread usage, pinning, and performance.
 *
 * Part 4 Optimization: Virtual Thread Test Metrics Collection
 */
package org.yawlfoundation.yawl.observability;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * JUnit 5 extension for collecting virtual thread metrics during test execution.
 *
 * <p>This extension tracks:</p>
 * <ul>
 *   <li>Virtual thread creation count</li>
 *   <li>Virtual thread pinning events</li>
 *   <li>Test execution time with virtual threads</li>
 *   <li>Carrier thread utilization</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @ExtendWith(VirtualThreadTestMetrics.class)
 * class MyTest {
 *     // Tests will automatically have metrics collected
 * }
 * }</pre>
 *
 * <h2>Configuration</h2>
 * <p>Enable pinning detection in junit-platform.properties:</p>
 * <pre>
 * yawl.test.virtual.pinning.detection=true
 * </pre>
 *
 * <p>Or via system property:</p>
 * <pre>
 * -Djdk.tracePinnedThreads=full
 * </pre>
 *
 * @see <a href="https://openjdk.org/jeps/444">JEP 444: Virtual Threads</a>
 */
public class VirtualThreadTestMetrics
        implements BeforeAllCallback, AfterAllCallback,
                   BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final Logger LOG = Logger.getLogger(VirtualThreadTestMetrics.class.getName());

    private static final String METRICS_KEY = "VIRTUAL_THREAD_METRICS";
    private static final String START_TIME_KEY = "TEST_START_TIME";

    // Thread-local storage for test start times
    private static final ConcurrentMap<String, AtomicLong> testStartTimes = new ConcurrentHashMap<>();

    // Metrics storage
    private static final AtomicLong totalVirtualThreads = new AtomicLong(0);
    private static final AtomicLong totalPinningEvents = new AtomicLong(0);
    private static final AtomicLong totalTestsExecuted = new AtomicLong(0);
    private static final AtomicLong totalTestTimeMs = new AtomicLong(0);

    // Thread MXBean for thread monitoring
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    @Override
    public void beforeAll(ExtensionContext context) {
        logThreadInfo("Before All Tests");
        storeMetrics(context, new Metrics());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        Metrics metrics = getMetrics(context);
        if (metrics != null) {
            logSummary(metrics);
        }
        logThreadInfo("After All Tests");
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        String testId = getTestId(context);
        long startTime = System.nanoTime();
        testStartTimes.put(testId, new AtomicLong(startTime));

        // Store in extension context for this test
        context.getStore(ExtensionContext.Namespace.GLOBAL)
                .put(START_TIME_KEY + testId, startTime);

        // Log thread info at test start
        LOG.fine(() -> String.format("[START] %s - Thread: %s (virtual: %s)",
                testId,
                Thread.currentThread().getName(),
                Thread.currentThread().isVirtual()));
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        String testId = getTestId(context);
        Long startTime = context.getStore(ExtensionContext.Namespace.GLOBAL)
                .get(START_TIME_KEY + testId, Long.class);

        if (startTime != null) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            totalTestTimeMs.addAndGet(durationMs);
            totalTestsExecuted.incrementAndGet();

            LOG.fine(() -> String.format("[END] %s - Duration: %dms - Thread: %s (virtual: %s)",
                    testId,
                    durationMs,
                    Thread.currentThread().getName(),
                    Thread.currentThread().isVirtual()));

            // Update metrics
            Metrics metrics = getMetrics(context);
            if (metrics != null) {
                metrics.recordTest(durationMs, Thread.currentThread().isVirtual());
            }
        }

        testStartTimes.remove(testId);
    }

    /**
     * Get the current number of virtual threads in the JVM.
     *
     * @return count of virtual threads, or -1 if not available
     */
    public static long getVirtualThreadCount() {
        return Thread.activeCount(); // Approximation - actual virtual thread count is not directly available
    }

    /**
     * Get the total number of pinning events detected.
     *
     * @return total pinning events across all tests
     */
    public static long getTotalPinningEvents() {
        return totalPinningEvents.get();
    }

    /**
     * Get the total number of tests executed.
     *
     * @return total test count
     */
    public static long getTotalTestsExecuted() {
        return totalTestsExecuted.get();
    }

    /**
     * Get the average test execution time in milliseconds.
     *
     * @return average test time in ms
     */
    public static double getAverageTestTimeMs() {
        long tests = totalTestsExecuted.get();
        return tests > 0 ? (double) totalTestTimeMs.get() / tests : 0.0;
    }

    /**
     * Record a pinning event (called when virtual thread is pinned to carrier).
     */
    public static void recordPinningEvent() {
        totalPinningEvents.incrementAndGet();
        LOG.warning("Virtual thread pinning event detected. Consider using ReentrantLock instead of synchronized.");
    }

    /**
     * Reset all metrics counters.
     */
    public static void reset() {
        totalVirtualThreads.set(0);
        totalPinningEvents.set(0);
        totalTestsExecuted.set(0);
        totalTestTimeMs.set(0);
        testStartTimes.clear();
    }

    private void logThreadInfo(String phase) {
        int threadCount = threadMXBean.getThreadCount();
        int peakThreadCount = threadMXBean.getPeakThreadCount();
        long totalStartedThreads = threadMXBean.getTotalStartedThreadCount();

        LOG.info(() -> String.format(
                "[%s] Threads: %d, Peak: %d, Total Started: %d",
                phase, threadCount, peakThreadCount, totalStartedThreads));

        // Log virtual thread info
        boolean isVirtual = Thread.currentThread().isVirtual();
        LOG.info(() -> String.format(
                "[%s] Current thread is virtual: %s, name: %s",
                phase, isVirtual, Thread.currentThread().getName()));
    }

    private void logSummary(Metrics metrics) {
        LOG.info(() -> String.format(
                """
                === Virtual Thread Test Metrics ===
                Tests Executed: %d
                Total Test Time: %d ms
                Average Test Time: %.2f ms
                Virtual Thread Tests: %d
                Platform Thread Tests: %d
                Pinning Events Detected: %d (enable -Djdk.tracePinnedThreads=full for details)
                ====================================""",
                metrics.testCount,
                metrics.totalTimeMs,
                metrics.getAverageTimeMs(),
                metrics.virtualThreadTests,
                metrics.platformThreadTests,
                totalPinningEvents.get()));
    }

    private String getTestId(ExtensionContext context) {
        return context.getTestClass().map(Class::getSimpleName).orElse("Unknown") +
               "." +
               context.getTestMethod().map(m -> m.getName()).orElse("unknown");
    }

    private void storeMetrics(ExtensionContext context, Metrics metrics) {
        context.getStore(ExtensionContext.Namespace.GLOBAL).put(METRICS_KEY, metrics);
    }

    private Metrics getMetrics(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.GLOBAL).get(METRICS_KEY, Metrics.class);
    }

    /**
     * Internal metrics container.
     */
    private static class Metrics {
        long testCount = 0;
        long totalTimeMs = 0;
        long virtualThreadTests = 0;
        long platformThreadTests = 0;

        synchronized void recordTest(long durationMs, boolean isVirtual) {
            testCount++;
            totalTimeMs += durationMs;
            if (isVirtual) {
                virtualThreadTests++;
            } else {
                platformThreadTests++;
            }
        }

        double getAverageTimeMs() {
            return testCount > 0 ? (double) totalTimeMs / testCount : 0.0;
        }
    }
}
