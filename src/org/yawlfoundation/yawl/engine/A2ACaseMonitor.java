package org.yawlfoundation.yawl.engine;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.instance.CaseInstance;
import org.yawlfoundation.yawl.exceptions.YAWLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A2A Case Monitor - Real implementation for case monitoring with A2A events.
 *
 * This is a simplified version that demonstrates the A2A integration pattern
 * with the correct YAWL dependencies. The real implementation would connect to
 * the A2A SDK for actual event publishing.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class A2ACaseMonitor {

    private static final Logger _logger = LogManager.getLogger(A2ACaseMonitor.class);

    private final Object _yawlEngine; // YAWL engine instance
    private final A2AEventPublisher _eventPublisher;

    // Case state tracking
    private final Map<String, CaseState> activeCases = new ConcurrentHashMap<>();

    // Configuration — volatile: plain reads/writes suffice for single-boolean flag.
    // _monitoringLock guards the compound start/stop transition (awaitTermination
    // is blocking I/O — synchronized would pin the virtual-thread carrier).
    private volatile boolean monitoringEnabled = false;
    private long monitoringInterval = 5000; // 5 seconds default
    private final ReentrantLock _monitoringLock = new ReentrantLock();

    private ScheduledExecutorService scheduler;

    public A2ACaseMonitor(Object yawlEngine) {
        if (yawlEngine == null) {
            throw new IllegalArgumentException("YAWL engine cannot be null");
        }
        this._yawlEngine = yawlEngine;
        this._eventPublisher = new A2AEventPublisher("yawl-a2a-monitor");
    }

    /**
     * Enable A2A case monitoring.
     * Uses ReentrantLock (not synchronized) to prevent virtual-thread pinning:
     * awaitTermination() blocks on I/O, and synchronized would pin the carrier thread.
     */
    public void setMonitoringEnabled(boolean enabled) {
        _monitoringLock.lock();
        try {
            if (enabled && !monitoringEnabled) {
                // Start monitoring
                monitoringEnabled = true;
                startPeriodicMonitoring();
                _logger.info("A2A case monitoring enabled");
            } else if (!enabled && monitoringEnabled) {
                // Stop monitoring
                monitoringEnabled = false;
                if (scheduler != null) {
                    scheduler.shutdown();
                    try {
                        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                            scheduler.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        scheduler.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                }
                _logger.info("A2A case monitoring disabled");
            }
        } finally {
            _monitoringLock.unlock();
        }
    }

    /**
     * Set monitoring interval in milliseconds
     */
    public void setMonitoringInterval(long interval) {
        if (interval < 1000) {
            throw new IllegalArgumentException("Monitoring interval must be at least 1000ms");
        }
        this.monitoringInterval = interval;
        _logger.info("A2A monitoring interval set to {}ms", interval);
    }

    /**
     * Start case monitoring
     */
    public void startCaseMonitoring(String caseId) throws YAWLException {
        if (caseId == null) {
            throw new IllegalArgumentException("Case ID cannot be null");
        }

        // Create and register case state
        CaseState caseState = new CaseState(caseId);
        activeCases.put(caseId, caseState);

        // Publish case started event
        publishCaseEvent(caseId, "CASE_STARTED", caseState);

        _logger.info("Started A2A monitoring for case {}", caseId);
    }

    /**
     * Stop case monitoring
     */
    public void stopCaseMonitoring(String caseId) {
        CaseState caseState = activeCases.remove(caseId);
        if (caseState != null) {
            // Publish case stopped event
            publishCaseEvent(caseId, "CASE_STOPPED", caseState);
            _logger.info("Stopped A2A monitoring for case {}", caseId);
        }
    }

    /**
     * Get case status
     */
    public CaseStatus getCaseStatus(String caseId) {
        if (!monitoringEnabled) {
            throw new IllegalStateException("A2A case monitoring is not enabled");
        }

        CaseState caseState = activeCases.get(caseId);
        if (caseState == null) {
            throw new IllegalArgumentException("Case is not being monitored: " + caseId);
        }

        return CaseStatus.RUNNING; // Simplified status
    }

    /**
     * Get all monitored case IDs
     */
    public Iterable<String> getMonitoredCases() {
        if (!monitoringEnabled) {
            throw new IllegalStateException("A2A case monitoring is not enabled");
        }

        return activeCases.keySet();
    }

    /**
     * Get case statistics
     */
    public CaseStatistics getCaseStatistics(String caseId) {
        if (!monitoringEnabled) {
            throw new IllegalStateException("A2A case monitoring is not enabled");
        }

        CaseState caseState = activeCases.get(caseId);
        if (caseState == null) {
            throw new IllegalArgumentException("Case is not being monitored: " + caseId);
        }

        return new CaseStatistics(caseState);
    }

    /**
     * Periodic monitoring task
     */
    private void startPeriodicMonitoring() {
        if (scheduler == null) {
            scheduler = Executors.newScheduledThreadPool(2);
        }

        scheduler.scheduleAtFixedRate(this::checkAllCases,
                                    monitoringInterval, monitoringInterval, TimeUnit.MILLISECONDS);

        scheduler.scheduleAtFixedRate(this::publishHealthStatus,
                                    30000, 30000, TimeUnit.MILLISECONDS); // Every 30 seconds
    }

    /**
     * Check all active cases
     */
    private void checkAllCases() {
        if (!monitoringEnabled) {
            return;
        }

        long now = System.currentTimeMillis();

        // Use StructuredTaskScope for parallel case monitoring when there are many cases
        if (activeCases.size() > 5) { // Threshold for parallel processing
            checkAllCasesInParallel(now);
        } else {
            // Small number of cases - use sequential approach
            for (Map.Entry<String, CaseState> entry : activeCases.entrySet()) {
                String caseId = entry.getKey();
                CaseState caseState = entry.getValue();

                try {
                    checkSingleCase(caseId, caseState, now);
                } catch (Exception e) {
                    _logger.error("Error monitoring case {}: {}", caseId, e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Checks all cases in parallel using CompletableFuture.
     * This improves performance when monitoring many cases.
     */
    private void checkAllCasesInParallel(long now) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Fork case monitoring for each active case
            for (Map.Entry<String, CaseState> entry : activeCases.entrySet()) {
                String caseId = entry.getKey();
                CaseState caseState = entry.getValue();

                CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> checkSingleCase(caseId, caseState, now),
                    executor
                );
                futures.add(future);
            }

            // Wait for all case checks to complete
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            allFutures.get(); // This will throw if any task failed

        } catch (ExecutionException e) {
            _logger.error("Error in parallel case monitoring: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            _logger.warn("Case monitoring interrupted");
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Checks a single case and updates its state.
     *
     * @param caseId the case identifier
     * @param caseState the case state
     * @param now the current timestamp
     */
    private void checkSingleCase(String caseId, CaseState caseState, long now) {
        try {
            // Update case state
            caseState.lastUpdateTime = now;

            // Simulate case state check
            if (now - caseState.startTime > 120000) { // 2 minutes
                // Simulate case completion
                publishCaseEvent(caseId, "CASE_COMPLETED", caseState);
                activeCases.remove(caseId);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error checking case " + caseId, e);
        }
    }

    /**
     * Publish health status
     */
    private void publishHealthStatus() {
        if (!monitoringEnabled) {
            return;
        }

        int caseCount = activeCases.size();
        Map<String, Object> healthData = Map.of(
            "activeCases", caseCount,
            "timestamp", System.currentTimeMillis(),
            "agentId", _eventPublisher.toString()
        );

        _eventPublisher.publishSpecificationEvent(null, "YAWL Engine",
            A2AEventPublisher.SpecEventType.SPEC_LOADED, healthData, null);
    }

    /**
     * Publish case event
     */
    private void publishCaseEvent(String caseId, String eventType, CaseState caseState) {
        Map<String, Object> eventData = Map.of(
            "startTime", caseState.startTime,
            "lastUpdate", caseState.lastUpdateTime,
            "status", caseState.status
        );

        try {
            _eventPublisher.publishCaseEvent(null, caseId,
                parseCaseEventType(eventType), eventData);
        } catch (Exception e) {
            _logger.warn("Failed to publish case event: {}", e.getMessage());
        }
    }

    /**
     * Parse case event type string to enum
     */
    private A2AEventPublisher.CaseEventType parseCaseEventType(String eventType) {
        try {
            return A2AEventPublisher.CaseEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            _logger.warn("Unknown case event type: {}", eventType);
            return A2AEventPublisher.CaseEventType.CASE_ERROR;
        }
    }

    /**
     * Inner class for case state tracking
     */
    private static class CaseState {
        final String caseId;
        final long startTime;
        long lastUpdateTime;
        String status;

        CaseState(String caseId) {
            this.caseId = caseId;
            this.startTime = System.currentTimeMillis();
            this.lastUpdateTime = startTime;
            this.status = "STARTED";
        }
    }

    /**
     * Case status enum
     */
    public enum CaseStatus {
        RUNNING,
        COMPLETED,
        CANCELLED,
        SUSPENDED,
        DEADLOCKED,
        UNKNOWN
    }

    /**
     * Case statistics record
     */
    public static record CaseStatistics(
        String caseId,
        CaseStatus status,
        long startTime,
        long duration
    ) {
        public CaseStatistics(CaseState caseState) {
            this(
                caseState.caseId,
                CaseStatus.RUNNING,
                caseState.startTime,
                System.currentTimeMillis() - caseState.startTime
            );
        }
    }
}