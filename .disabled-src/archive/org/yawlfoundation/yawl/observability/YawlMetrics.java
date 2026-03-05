package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer metrics for YAWL engine observability.
 *
 * Provides key metrics for production monitoring:
 *
 * Counter metrics:
 * - yawl.case.created: Cases created
 * - yawl.case.completed: Cases completed
 * - yawl.case.failed: Cases failed
 * - yawl.task.executed: Tasks executed
 * - yawl.task.failed: Task execution failures
 *
 * Gauge metrics:
 * - yawl.case.active: Active case count
 * - yawl.queue.depth: Engine queue depth
 * - yawl.threadpool.active: Active worker threads
 *
 * Timer metrics:
 * - yawl.case.duration: Case execution time
 * - yawl.task.duration: Task execution time
 * - yawl.engine.latency: Engine request latency
 */
public class YawlMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(YawlMetrics.class);
    private static YawlMetrics instance;

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter caseCreatedCounter;
    private final Counter caseCompletedCounter;
    private final Counter caseFailedCounter;
    private final Counter taskExecutedCounter;
    private final Counter taskFailedCounter;

    // Gauges
    private final AtomicLong activeCaseCount = new AtomicLong(0);
    private final AtomicLong queueDepth = new AtomicLong(0);
    private final AtomicLong activeThreads = new AtomicLong(0);

    // Timers
    private final Timer caseExecutionTimer;
    private final Timer taskExecutionTimer;
    private final Timer engineLatencyTimer;

    private YawlMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);

        // Initialize counters
        this.caseCreatedCounter = Counter.builder("yawl.case.created")
                .description("Total cases created")
                .register(meterRegistry);

        this.caseCompletedCounter = Counter.builder("yawl.case.completed")
                .description("Total cases completed successfully")
                .register(meterRegistry);

        this.caseFailedCounter = Counter.builder("yawl.case.failed")
                .description("Total cases failed")
                .register(meterRegistry);

        this.taskExecutedCounter = Counter.builder("yawl.task.executed")
                .description("Total tasks executed")
                .register(meterRegistry);

        this.taskFailedCounter = Counter.builder("yawl.task.failed")
                .description("Total task execution failures")
                .register(meterRegistry);

        // Initialize gauges
        Gauge.builder("yawl.case.active", activeCaseCount::get)
                .description("Number of active cases")
                .register(meterRegistry);

        Gauge.builder("yawl.queue.depth", queueDepth::get)
                .description("Engine work queue depth")
                .register(meterRegistry);

        Gauge.builder("yawl.threadpool.active", activeThreads::get)
                .description("Number of active worker threads")
                .register(meterRegistry);

        // Initialize timers
        this.caseExecutionTimer = Timer.builder("yawl.case.duration")
                .description("Time to execute a case")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.taskExecutionTimer = Timer.builder("yawl.task.duration")
                .description("Time to execute a task")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.engineLatencyTimer = Timer.builder("yawl.engine.latency")
                .description("YAWL engine request latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        LOGGER.info("YawlMetrics initialized with Micrometer registry");
    }

    /**
     * Initializes the singleton metrics instance.
     */
    public static synchronized void initialize(MeterRegistry meterRegistry) {
        if (instance == null) {
            instance = new YawlMetrics(meterRegistry);
        }
    }

    /**
     * Gets the singleton metrics instance.
     */
    public static YawlMetrics getInstance() {
        if (instance == null) {
            throw new IllegalStateException("YawlMetrics not initialized. Call initialize() first.");
        }
        return instance;
    }

    // Case metrics

    public void incrementCaseCreated() {
        caseCreatedCounter.increment();
        activeCaseCount.incrementAndGet();
    }

    public void incrementCaseCompleted() {
        caseCompletedCounter.increment();
        activeCaseCount.decrementAndGet();
    }

    public void incrementCaseFailed() {
        caseFailedCounter.increment();
        activeCaseCount.decrementAndGet();
    }

    public long getActiveCaseCount() {
        return activeCaseCount.get();
    }

    public void setActiveCaseCount(long count) {
        activeCaseCount.set(count);
    }

    // Task metrics

    public void incrementTaskExecuted() {
        taskExecutedCounter.increment();
    }

    public void incrementTaskFailed() {
        taskFailedCounter.increment();
    }

    // Queue metrics

    public void setQueueDepth(long depth) {
        queueDepth.set(Math.max(0, depth));
    }

    public long getQueueDepth() {
        return queueDepth.get();
    }

    // Thread pool metrics

    public void setActiveThreads(long count) {
        activeThreads.set(Math.max(0, count));
    }

    public long getActiveThreads() {
        return activeThreads.get();
    }

    // Timer methods

    public Timer.Sample startCaseExecutionTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordCaseExecutionTime(Timer.Sample sample) {
        sample.stop(caseExecutionTimer);
    }

    public Timer.Sample startTaskExecutionTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordTaskExecutionTime(Timer.Sample sample) {
        sample.stop(taskExecutionTimer);
    }

    public Timer.Sample startEngineLatencyTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordEngineLatency(Timer.Sample sample) {
        sample.stop(engineLatencyTimer);
    }

    /**
     * Records case execution duration in milliseconds.
     */
    public void recordCaseDuration(long durationMs) {
        caseExecutionTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Records task execution duration in milliseconds.
     */
    public void recordTaskDuration(long durationMs) {
        taskExecutionTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Records engine latency in milliseconds.
     */
    public void recordEngineLatencyMs(long latencyMs) {
        engineLatencyTimer.record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Gets the meter registry for custom metric registration.
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}
