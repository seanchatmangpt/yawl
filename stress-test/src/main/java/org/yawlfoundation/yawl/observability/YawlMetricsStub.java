package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Stub implementation of YawlMetrics for when the real metrics instance is not available.
 */
public class YawlMetricsStub {

    // Stub implementations for all YawlMetrics methods

    public void incrementCaseCreated() {
        // No-op for stub
    }

    public void incrementCaseCompleted() {
        // No-op for stub
    }

    public void incrementCaseFailed() {
        // No-op for stub
    }

    public long getActiveCaseCount() {
        return 0;
    }

    public void setActiveCaseCount(long count) {
        // No-op for stub
    }

    public void incrementTaskExecuted() {
        // No-op for stub
    }

    public void incrementTaskFailed() {
        // No-op for stub
    }

    public void setQueueDepth(long depth) {
        // No-op for stub
    }

    public long getQueueDepth() {
        return 0;
    }

    public void setActiveThreads(long count) {
        // No-op for stub
    }

    public long getActiveThreads() {
        return 0;
    }

    public Timer.Sample startCaseExecutionTimer() {
        return null;
    }

    public void recordCaseExecutionTime(Timer.Sample sample) {
        // No-op for stub
    }

    public Timer.Sample startTaskExecutionTimer() {
        return null;
    }

    public void recordTaskExecutionTime(Timer.Sample sample) {
        // No-op for stub
    }

    public Timer.Sample startEngineLatencyTimer() {
        return null;
    }

    public void recordEngineLatency(Timer.Sample sample) {
        // No-op for stub
    }

    public void recordCaseDuration(long durationMs) {
        // No-op for stub
    }

    public void recordTaskDuration(long durationMs) {
        // No-op for stub
    }

    public void recordEngineLatencyMs(long latencyMs) {
        // No-op for stub
    }

    public MeterRegistry getMeterRegistry() {
        return null;
    }
}