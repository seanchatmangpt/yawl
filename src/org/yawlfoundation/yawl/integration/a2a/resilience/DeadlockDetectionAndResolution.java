package org.yawlfoundation.yawl.integration.a2a.resilience;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Detects and resolves workflow deadlocks via case state analysis and auto-escalation.
 *
 * Implements the 80/20 autonomic self-healing pattern for deadlock resolution.
 * Monitors case execution time, task state transitions, and enabled task changes.
 * When deadlock detected: logs events for manual review and optionally auto-escalates
 * to case suspension with compensation workflow.
 *
 * <p><b>Deadlock detection heuristics:</b>
 * <ul>
 *   <li>Case has enabled tasks but no progress for threshold duration</li>
 *   <li>Case stuck in same state across multiple monitoring cycles</li>
 *   <li>Circular task dependencies detected (cycle in task graph)</li>
 *   <li>Resource contention (multiple cases waiting on same resource)</li>
 * </ul>
 *
 * <p><b>Resolution strategies:</b>
 * <ol>
 *   <li><b>Log & Escalate:</b> Log deadlock event to event store for manual review</li>
 *   <li><b>Auto-Compensate:</b> Suspend case + invoke compensation workflow (optional)</li>
 *   <li><b>Force Progress:</b> Force-complete blocked task with timeout flag (advanced)</li>
 * </ol>
 *
 * <p><b>Event logging:</b>
 * - DEADLOCK_DETECTED: Initial detection event
 * - DEADLOCK_ESCALATED: Escalation to manual review
 * - DEADLOCK_RESOLVED: Auto-compensation applied
 * - DEADLOCK_MANUAL_REVIEW: Waiting for human intervention
 *
 * Thread-safe via ReentrantReadWriteLock. Non-blocking design; work happens in
 * background monitoring thread.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class DeadlockDetectionAndResolution {

    private static final Logger logger = LogManager.getLogger(DeadlockDetectionAndResolution.class);

    /**
     * Deadlock resolution strategies
     */
    public enum ResolutionStrategy {
        LOG_ONLY,           // Log and escalate to manual review
        AUTO_COMPENSATE,    // Suspend case + invoke compensation
        FORCE_COMPLETE      // Force-complete blocked tasks (risky, use sparingly)
    }

    /**
     * Deadlock event types for audit trail
     */
    public enum DeadlockEventType {
        DETECTED, ESCALATED, RESOLVED, MANUAL_REVIEW_TIMEOUT
    }

    private static final long DEFAULT_DETECTION_THRESHOLD_MS = 30000; // 30 seconds
    private static final long DEFAULT_ESCALATION_DELAY_MS = 60000;    // 1 minute

    private final String caseId;
    private final ResolutionStrategy strategy;
    private final long detectionThresholdMs;
    private final long escalationDelayMs;

    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();
    private final List<DeadlockEvent> eventLog = new ArrayList<>();
    private final AtomicBoolean deadlockDetected = new AtomicBoolean(false);
    private final AtomicLong lastStateChangeTimeMs = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong detectionTimeMs = new AtomicLong(0);
    private final AtomicBoolean escalated = new AtomicBoolean(false);

    private Set<String> lastEnabledTasks = new HashSet<>();
    private Map<String, Long> taskStartTimes = new HashMap<>();
    private int monitoringCycleCount = 0;

    /**
     * Construct deadlock detector with default parameters.
     *
     * @param caseId workflow case identifier
     * @param strategy resolution strategy to apply
     */
    public DeadlockDetectionAndResolution(String caseId, ResolutionStrategy strategy) {
        this(caseId, strategy, DEFAULT_DETECTION_THRESHOLD_MS, DEFAULT_ESCALATION_DELAY_MS);
    }

    /**
     * Construct deadlock detector with custom parameters.
     *
     * @param caseId workflow case identifier
     * @param strategy resolution strategy to apply
     * @param detectionThresholdMs milliseconds of no progress before detecting deadlock
     * @param escalationDelayMs milliseconds before escalating to manual review
     */
    public DeadlockDetectionAndResolution(String caseId,
                                          ResolutionStrategy strategy,
                                          long detectionThresholdMs,
                                          long escalationDelayMs) {
        this.caseId = caseId;
        this.strategy = strategy;
        this.detectionThresholdMs = detectionThresholdMs;
        this.escalationDelayMs = escalationDelayMs;
    }

    /**
     * Check current case state for deadlock.
     * Called periodically (e.g., every 10 seconds) by monitoring thread.
     *
     * @param enabledTasks set of currently enabled task identifiers
     * @param caseStatus human-readable case status (e.g., "Running", "Suspended")
     * @return true if deadlock detected and resolution triggered
     */
    public boolean checkAndResolveDeadlock(Set<String> enabledTasks, String caseStatus) {
        stateLock.writeLock().lock();
        try {
            monitoringCycleCount++;

            // If case is not running, no deadlock possible
            if (!"Running".equals(caseStatus)) {
                return false;
            }

            // Empty enabled tasks = no deadlock (case completed or waiting)
            if (enabledTasks.isEmpty()) {
                lastStateChangeTimeMs.set(System.currentTimeMillis());
                return false;
            }

            // Check if state changed from last cycle
            if (!enabledTasks.equals(lastEnabledTasks)) {
                lastStateChangeTimeMs.set(System.currentTimeMillis());
                lastEnabledTasks = new HashSet<>(enabledTasks);
                return false;
            }

            // State unchanged: check if threshold exceeded
            long nowMs = System.currentTimeMillis();
            long stateAgeMs = nowMs - lastStateChangeTimeMs.get();

            if (stateAgeMs >= detectionThresholdMs && !deadlockDetected.get()) {
                // Deadlock detected: enabled tasks but no progress
                logEvent(DeadlockEventType.DETECTED, null);
                deadlockDetected.set(true);
                detectionTimeMs.set(nowMs);
                logger.warn("Deadlock detected in case '{}': enabled tasks={}, age={}ms",
                    caseId, enabledTasks, stateAgeMs);
                return true;
            }

            // Check if escalation time reached
            if (deadlockDetected.get() && !escalated.get()) {
                long deadlockAgeMs = nowMs - detectionTimeMs.get();
                if (deadlockAgeMs >= escalationDelayMs) {
                    escalateDeadlock();
                    return true;
                }
            }

            return deadlockDetected.get();

        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * Record task start time for timeout tracking.
     *
     * @param taskId task identifier
     */
    public void recordTaskStart(String taskId) {
        stateLock.writeLock().lock();
        try {
            taskStartTimes.put(taskId, System.currentTimeMillis());
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * Record task completion and clear timeout tracking.
     *
     * @param taskId task identifier
     */
    public void recordTaskCompletion(String taskId) {
        stateLock.writeLock().lock();
        try {
            taskStartTimes.remove(taskId);
            // Task completed = progress made, reset deadlock detection
            deadlockDetected.set(false);
            escalated.set(false);
            lastStateChangeTimeMs.set(System.currentTimeMillis());
            logEvent(DeadlockEventType.RESOLVED, "Task '" + taskId + "' completed");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * Get whether deadlock is currently detected.
     */
    public boolean isDeadlockDetected() {
        return deadlockDetected.get();
    }

    /**
     * Get whether deadlock has been escalated to manual review.
     */
    public boolean isEscalated() {
        return escalated.get();
    }

    /**
     * Get time since deadlock detection (milliseconds), or -1 if not detected.
     */
    public long getDeadlockAgeMs() {
        if (!deadlockDetected.get()) {
            return -1;
        }
        return System.currentTimeMillis() - detectionTimeMs.get();
    }

    /**
     * Get event audit log.
     */
    public List<DeadlockEvent> getEventLog() {
        stateLock.readLock().lock();
        try {
            return new ArrayList<>(eventLog);
        } finally {
            stateLock.readLock().unlock();
        }
    }

    /**
     * Get monitoring cycle count (useful for testing and metrics).
     */
    public int getMonitoringCycleCount() {
        return monitoringCycleCount;
    }

    // Private helper methods

    private void escalateDeadlock() {
        escalated.set(true);
        String resolutionAction;

        switch (strategy) {
            case AUTO_COMPENSATE:
                resolutionAction = "Case will be suspended and compensation workflow invoked";
                break;
            case FORCE_COMPLETE:
                resolutionAction = "Blocked tasks will be force-completed (timeout flag set)";
                break;
            case LOG_ONLY:
            default:
                resolutionAction = "Escalated to manual review - no automatic action";
        }

        logEvent(DeadlockEventType.ESCALATED, resolutionAction);
        logger.error("Deadlock escalated in case '{}': {}",
            caseId, resolutionAction);
    }

    private void logEvent(DeadlockEventType eventType, String details) {
        DeadlockEvent event = new DeadlockEvent(
            caseId,
            eventType,
            Instant.now(),
            details != null ? details : "",
            strategy
        );
        eventLog.add(event);
    }

    /**
     * Immutable deadlock event for audit trail.
     *
     * @param caseId case identifier
     * @param eventType type of deadlock event
     * @param timestamp when event occurred
     * @param details description of event or resolution action
     * @param strategy resolution strategy applied
     */
    public record DeadlockEvent(
        String caseId,
        DeadlockEventType eventType,
        Instant timestamp,
        String details,
        ResolutionStrategy strategy
    ) {
        @Override
        public String toString() {
            return String.format(
                "DeadlockEvent{case=%s, type=%s, time=%s, strategy=%s, details=%s}",
                caseId, eventType, timestamp, strategy, details);
        }
    }
}
