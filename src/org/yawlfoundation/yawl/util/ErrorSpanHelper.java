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
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.yawlfoundation.yawl.engine.observability.YAWLTelemetry;
import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.yawlfoundation.yawl.exceptions.YStateException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility class for creating error spans and recording failures in YAWL operations.
 *
 * <p>This utility follows Toyota Production System (TPS) principles by making
 * errors visible through comprehensive logging and metrics. Every error is recorded
 * with full context including case, task, and specification identifiers.</p>
 *
 * <p>This implementation uses YAWLTelemetry for metrics instead of direct OTEL
 * to avoid dependency issues across modules.</p>
 *
 * <h2>TPS Principles Applied</h2>
 * <ul>
 *   <li><b>Jidoka (Autonomation)</b>: Errors are immediately surfaced through
 *       structured logging and metrics, not silently swallowed</li>
 *   <li><b>Andon (Visual Control)</b>: Every error creates visible signals
 *       via log entries and metric counters</li>
 *   <li><b>5 Whys</b>: Full context capture enables root cause analysis</li>
 *   <li><b>Kaizen (Continuous Improvement)</b>: Aggregated metrics reveal
 *       systemic issues for process improvement</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All methods in this class are thread-safe and can be called from multiple
 * concurrent threads. Internal state uses concurrent collections and atomic variables.</p>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Basic error recording
 * ErrorSpanHelper.recordError("task.execution", "case-123", exception);
 *
 * // Task-specific errors with full context
 * ErrorSpanHelper.recordTaskError("task-456", "case-123", "spec-789", "execute", exception);
 *
 * // Case-level errors
 * ErrorSpanHelper.recordCaseError("case-123", "spec-789", "cancel", exception);
 *
 * // Specification-level errors
 * ErrorSpanHelper.recordSpecificationError("spec-789", "validation", exception);
 *
 * // Deadlock detection
 * ErrorSpanHelper.recordDeadlock("case-123", "spec-789", "task-A,task-B,task-C");
 *
 * // Lock contention
 * ErrorSpanHelper.recordLockContention("case-123", "caseLock-123", 150);
 *
 * // State exception handling
 * ErrorSpanHelper.recordStateException("case-123", "task-456", stateException);
 * }</pre>
 *
 * @author YAWL Development Team
 * @version 6.0.0
 * @since 6.0.0
 */
public final class ErrorSpanHelper {

    private static final Logger LOGGER = LogManager.getLogger(ErrorSpanHelper.class);

    /** Threshold in milliseconds for high contention warning */
    private static final long HIGH_CONTENTION_THRESHOLD_MS = 500;

    /** Threshold in milliseconds for critical contention error */
    private static final long CRITICAL_CONTENTION_THRESHOLD_MS = 2000;

    /** Lock for thread-safe statistics updates */
    private static final ReentrantLock STATS_LOCK = new ReentrantLock();

    /** Counter for total errors recorded */
    private static final AtomicLong TOTAL_ERRORS = new AtomicLong(0);

    /** Counter for state exceptions */
    private static final AtomicLong STATE_EXCEPTIONS = new AtomicLong(0);

    /** Counter for data state exceptions */
    private static final AtomicLong DATA_STATE_EXCEPTIONS = new AtomicLong(0);

    /** Counter for deadlock events */
    private static final AtomicLong DEADLOCK_EVENTS = new AtomicLong(0);

    /** Counter for lock contention events */
    private static final AtomicLong LOCK_CONTENTION_EVENTS = new AtomicLong(0);

    /** Map tracking errors by operation type for analysis */
    private static final ConcurrentHashMap<String, AtomicLong> ERRORS_BY_OPERATION =
        new ConcurrentHashMap<>();

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private ErrorSpanHelper() {
        throw new UnsupportedOperationException("Utility class - cannot be instantiated");
    }

    // =========================================================================
    // Core Error Recording Methods
    // =========================================================================

    /**
     * Records a basic error with YAWL telemetry and structured logging.
     *
     * <p>This method provides the foundation for all error recording in YAWL,
     * ensuring consistent logging format and metric recording.</p>
     *
     * @param operation The name of the operation that failed (e.g., "task.execution")
     * @param caseId The YAWL case identifier (may be null for engine-level errors)
     * @param exception The exception that occurred (must not be null)
     * @throws IllegalArgumentException if operation or exception is null
     */
    public static void recordError(String operation, String caseId, Exception exception) {
        validateNotNull(operation, "operation");
        validateNotNull(exception, "exception");

        recordErrorInternal(operation, caseId, null, null, exception, null);
    }

    /**
     * Records a task-specific error with YAWL telemetry.
     *
     * <p>Use this method when an error occurs within a specific task context,
     * such as task execution failures, data binding errors, or timeout conditions.</p>
     *
     * @param taskId The YAWL task identifier (e.g., "OrderProcessing-CheckInventory")
     * @param caseId The YAWL case identifier
     * @param operation The name of the operation that failed (e.g., "execute", "complete")
     * @param exception The exception that occurred
     * @throws IllegalArgumentException if taskId, operation, or exception is null
     */
    public static void recordTaskError(String taskId, String caseId, String operation,
                                        Exception exception) {
        recordTaskError(taskId, caseId, null, operation, exception);
    }

    /**
     * Records a task-specific error with full context including specification.
     *
     * @param taskId The YAWL task identifier
     * @param caseId The YAWL case identifier (may be null)
     * @param specId The YAWL specification identifier (may be null)
     * @param operation The name of the operation that failed
     * @param exception The exception that occurred
     * @throws IllegalArgumentException if taskId, operation, or exception is null
     */
    public static void recordTaskError(String taskId, String caseId, String specId,
                                        String operation, Exception exception) {
        validateNotNull(taskId, "taskId");
        validateNotNull(operation, "operation");
        validateNotNull(exception, "exception");

        String fullOperation = operation + ".task";
        recordErrorInternal(fullOperation, caseId, taskId, specId, exception, null);
    }

    /**
     * Records a case-level error with YAWL telemetry.
     *
     * <p>Use this method for errors that affect an entire case, such as
     * case initialization failures, cancellation errors, or case-level validation.</p>
     *
     * @param caseId The YAWL case identifier
     * @param operation The name of the operation that failed (e.g., "initialize", "cancel")
     * @param exception The exception that occurred
     * @throws IllegalArgumentException if caseId, operation, or exception is null
     */
    public static void recordCaseError(String caseId, String operation, Exception exception) {
        recordCaseError(caseId, null, operation, exception);
    }

    /**
     * Records a case-level error with specification context.
     *
     * @param caseId The YAWL case identifier
     * @param specId The YAWL specification identifier (may be null)
     * @param operation The name of the operation that failed
     * @param exception The exception that occurred
     * @throws IllegalArgumentException if caseId, operation, or exception is null
     */
    public static void recordCaseError(String caseId, String specId, String operation,
                                        Exception exception) {
        validateNotNull(caseId, "caseId");
        validateNotNull(operation, "operation");
        validateNotNull(exception, "exception");

        String fullOperation = operation + ".case";
        recordErrorInternal(fullOperation, caseId, null, specId, exception, null);
    }

    /**
     * Records a specification-level error with YAWL telemetry.
     *
     * <p>Use this method for errors related to specification loading, validation,
     * or parsing that occur before any case is created.</p>
     *
     * @param specId The YAWL specification identifier (URI or name)
     * @param operation The name of the operation that failed (e.g., "load", "validate")
     * @param exception The exception that occurred
     * @throws IllegalArgumentException if specId, operation, or exception is null
     */
    public static void recordSpecificationError(String specId, String operation,
                                                 Exception exception) {
        validateNotNull(specId, "specId");
        validateNotNull(operation, "operation");
        validateNotNull(exception, "exception");

        String fullOperation = operation + ".spec";
        recordErrorInternal(fullOperation, null, null, specId, exception, null);
    }

    // =========================================================================
    // Specific Exception Type Handlers
    // =========================================================================

    /**
     * Records a YStateException with full context extraction.
     *
     * <p>YStateException indicates an invalid workflow state was encountered.
     * This method extracts additional context from the exception for metrics.</p>
     *
     * @param caseId The YAWL case identifier (may be null)
     * @param taskId The YAWL task identifier (may be null)
     * @param exception The YStateException that occurred
     * @throws IllegalArgumentException if exception is null
     */
    public static void recordStateException(String caseId, String taskId,
                                             YStateException exception) {
        validateNotNull(exception, "exception");

        STATE_EXCEPTIONS.incrementAndGet();
        String operation = "state.invalid";
        recordErrorInternal(operation, caseId, taskId, null, exception, "STATE_ERROR");
    }

    /**
     * Records a YDataStateException with full context extraction.
     *
     * <p>YDataStateException indicates data validation or query failures.
     * This method extracts the query string, source task, and validation errors
     * for comprehensive error tracking.</p>
     *
     * @param caseId The YAWL case identifier (may be null)
     * @param taskId The YAWL task identifier (may be null)
     * @param exception The YDataStateException that occurred
     * @throws IllegalArgumentException if exception is null
     */
    public static void recordDataStateException(String caseId, String taskId,
                                                 YDataStateException exception) {
        validateNotNull(exception, "exception");

        DATA_STATE_EXCEPTIONS.incrementAndGet();

        // Extract additional context from the exception
        String source = exception.getSource() != null ? exception.getSource().toString() : taskId;
        String errorType = exception.getErrors() != null ? "validation" : "query";

        // Record via YAWLTelemetry with extracted context
        YAWLTelemetry telemetry = getTelemetrySafely();
        if (telemetry != null && caseId != null) {
            telemetry.recordValidationError(
                caseId,
                source,
                errorType,
                exception.getMessage()
            );
        }

        String operation = "data." + errorType;
        recordErrorInternal(operation, caseId, taskId, null, exception, "DATA_STATE_ERROR");
    }

    // =========================================================================
    // Deadlock and Lock Contention Recording
    // =========================================================================

    /**
     * Records a deadlock detection event.
     *
     * <p>Deadlock detection is critical for workflow reliability. This method
     * records deadlock events with full context for analysis and alerting.</p>
     *
     * <p>TPS Principle: Deadlocks represent a quality problem that must be
     * immediately visible (Jidoka) for root cause analysis.</p>
     *
     * @param caseId The YAWL case identifier where deadlock was detected
     * @param specId The YAWL specification identifier
     * @param deadlockedTasks Comma-separated list of deadlocked task IDs
     * @throws IllegalArgumentException if caseId, specId, or deadlockedTasks is null
     */
    public static void recordDeadlock(String caseId, String specId, String deadlockedTasks) {
        validateNotNull(caseId, "caseId");
        validateNotNull(specId, "specId");
        validateNotNull(deadlockedTasks, "deadlockedTasks");

        DEADLOCK_EVENTS.incrementAndGet();

        // Parse deadlocked tasks for count
        String[] taskArray = deadlockedTasks.split(",");
        int taskCount = taskArray.length;

        // Build structured log message
        String message = String.format(
            "[DEADLOCK] Case '%s' specification '%s' involves %d deadlocked tasks: [%s]",
            caseId, specId, taskCount, deadlockedTasks.trim()
        );

        LOGGER.error(message);

        // Record metrics via YAWLTelemetry
        YAWLTelemetry telemetry = getTelemetrySafely();
        if (telemetry != null) {
            telemetry.recordDeadlock(caseId, specId, taskCount);
        }

        // Update operation-specific counter
        incrementOperationCounter("deadlock.detected");
    }

    /**
     * Records a lock contention event.
     *
     * <p>Lock contention can indicate performance bottlenecks or potential
     * deadlock situations. This method tracks contention severity.</p>
     *
     * <p>Contention levels:</p>
     * <ul>
     *   <li>{@code < 500ms}: DEBUG level - normal contention</li>
     *   <li>{@code 500-2000ms}: WARN level - high contention</li>
     *   <li>{@code > 2000ms}: ERROR level - critical contention</li>
     * </ul>
     *
     * @param caseId The YAWL case identifier (may be null for engine-level locks)
     * @param lockName The name of the contended lock (e.g., "caseLock-123")
     * @param waitMillis The wait duration in milliseconds
     * @throws IllegalArgumentException if lockName is null or waitMillis is negative
     */
    public static void recordLockContention(String caseId, String lockName, long waitMillis) {
        validateNotNull(lockName, "lockName");
        if (waitMillis < 0) {
            throw new IllegalArgumentException("waitMillis must be non-negative");
        }

        LOCK_CONTENTION_EVENTS.incrementAndGet();

        // Build structured log message with severity based on wait time
        String message = String.format(
            "[LOCK_CONTENTION] Case '%s' lock '%s' wait: %d ms",
            caseId != null ? caseId : "engine", lockName, waitMillis
        );

        // Log at appropriate level based on severity
        if (waitMillis >= CRITICAL_CONTENTION_THRESHOLD_MS) {
            LOGGER.error("{} [CRITICAL]", message);
        } else if (waitMillis >= HIGH_CONTENTION_THRESHOLD_MS) {
            LOGGER.warn("{} [HIGH]", message);
        } else {
            LOGGER.debug("{}", message);
        }

        // Record metrics via YAWLTelemetry
        YAWLTelemetry telemetry = getTelemetrySafely();
        if (telemetry != null) {
            telemetry.recordLockContention(waitMillis, caseId, "lock.contention");
        }

        // Update operation-specific counter
        incrementOperationCounter("lock.contention");
    }

    // =========================================================================
    // Timing-Aware Error Recording
    // =========================================================================

    /**
     * Records an error with timing information.
     *
     * <p>Use this method when you have measured the operation duration before
     * the error occurred. This enables correlation between error rates and
     * operation latency.</p>
     *
     * @param operation The name of the operation that failed
     * @param caseId The YAWL case identifier (may be null)
     * @param exception The exception that occurred
     * @param durationNanos The duration of the operation in nanoseconds (before failure)
     * @throws IllegalArgumentException if operation or exception is null, or durationNanos is negative
     */
    public static void recordErrorWithTiming(String operation, String caseId,
                                              Exception exception, long durationNanos) {
        validateNotNull(operation, "operation");
        validateNotNull(exception, "exception");
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must be non-negative");
        }

        long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);

        // Build structured log message with timing
        String message = String.format(
            "[ERROR] Operation '%s' failed after %d ms for case '%s': %s",
            operation, durationMs, caseId != null ? caseId : "N/A", exception.getMessage()
        );

        LOGGER.error(message, exception);

        // Record error telemetry
        recordErrorInternal(operation, caseId, null, null, exception, null);
    }

    /**
     * Records a task error with timing information.
     *
     * @param taskId The YAWL task identifier
     * @param caseId The YAWL case identifier (may be null)
     * @param operation The name of the operation that failed
     * @param exception The exception that occurred
     * @param durationNanos The duration of the operation in nanoseconds
     * @throws IllegalArgumentException if taskId, operation, or exception is null
     */
    public static void recordTaskErrorWithTiming(String taskId, String caseId, String operation,
                                                  Exception exception, long durationNanos) {
        validateNotNull(taskId, "taskId");
        validateNotNull(operation, "operation");
        validateNotNull(exception, "exception");

        long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);

        String message = String.format(
            "[ERROR] Task '%s' operation '%s' failed after %d ms for case '%s': %s",
            taskId, operation, durationMs, caseId != null ? caseId : "N/A", exception.getMessage()
        );

        LOGGER.error(message, exception);

        String fullOperation = operation + ".task";
        recordErrorInternal(fullOperation, caseId, taskId, null, exception, null);
    }

    // =========================================================================
    // Statistics and Monitoring
    // =========================================================================

    /**
     * Returns a snapshot of error statistics.
     *
     * <p>Use this for health checks and monitoring dashboards.</p>
     *
     * @return immutable map of statistic names to values
     */
    public static Map<String, Long> getStatistics() {
        Map<String, Long> stats = new ConcurrentHashMap<>();

        stats.put("totalErrors", TOTAL_ERRORS.get());
        stats.put("stateExceptions", STATE_EXCEPTIONS.get());
        stats.put("dataStateExceptions", DATA_STATE_EXCEPTIONS.get());
        stats.put("deadlockEvents", DEADLOCK_EVENTS.get());
        stats.put("lockContentionEvents", LOCK_CONTENTION_EVENTS.get());

        // Add per-operation counts
        ERRORS_BY_OPERATION.forEach((op, count) ->
            stats.put("errors." + op, count.get()));

        return Map.copyOf(stats);
    }

    /**
     * Resets all error statistics counters to zero.
     *
     * <p>Use with caution - typically only for testing or periodic reporting.</p>
     */
    public static void resetStatistics() {
        STATS_LOCK.lock();
        try {
            TOTAL_ERRORS.set(0);
            STATE_EXCEPTIONS.set(0);
            DATA_STATE_EXCEPTIONS.set(0);
            DEADLOCK_EVENTS.set(0);
            LOCK_CONTENTION_EVENTS.set(0);
            ERRORS_BY_OPERATION.clear();

            LOGGER.info("Error statistics reset");
        } finally {
            STATS_LOCK.unlock();
        }
    }

    // =========================================================================
    // Internal Implementation
    // =========================================================================

    /**
     * Internal method for recording errors with full context.
     *
     * @param operation The operation name
     * @param caseId The case identifier (may be null)
     * @param taskId The task identifier (may be null)
     * @param specId The specification identifier (may be null)
     * @param exception The exception
     * @param errorCategory Optional error category for classification
     */
    private static void recordErrorInternal(String operation, String caseId, String taskId,
                                             String specId, Exception exception,
                                             String errorCategory) {
        // Increment total error counter
        TOTAL_ERRORS.incrementAndGet();

        // Increment operation-specific counter
        incrementOperationCounter(operation);

        // Build structured log message with context
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("[ERROR] Operation '").append(operation).append("'");

        if (caseId != null) {
            messageBuilder.append(" case='").append(caseId).append("'");
        }
        if (taskId != null) {
            messageBuilder.append(" task='").append(taskId).append("'");
        }
        if (specId != null) {
            messageBuilder.append(" spec='").append(specId).append("'");
        }
        if (errorCategory != null) {
            messageBuilder.append(" category='").append(errorCategory).append("'");
        }

        messageBuilder.append(": ").append(exception.getMessage());

        LOGGER.error(messageBuilder.toString(), exception);

        // Record metrics via YAWLTelemetry based on exception type
        YAWLTelemetry telemetry = getTelemetrySafely();
        if (telemetry != null && caseId != null) {
            if (exception instanceof YDataStateException dataEx) {
                String source = dataEx.getSource() != null
                    ? dataEx.getSource().toString()
                    : taskId;
                String type = dataEx.getErrors() != null ? "validation" : "query";
                telemetry.recordValidationError(caseId, source, type, exception.getMessage());
            } else if (exception instanceof YStateException) {
                telemetry.recordValidationError(caseId, taskId, "state",
                    exception.getMessage());
            }
        }
    }

    /**
     * Increments the counter for a specific operation type.
     *
     * @param operation The operation name
     */
    private static void incrementOperationCounter(String operation) {
        ERRORS_BY_OPERATION.computeIfAbsent(operation, k -> new AtomicLong(0))
                           .incrementAndGet();
    }

    /**
     * Gets the YAWLTelemetry instance safely, returning null if unavailable.
     *
     * @return the YAWLTelemetry instance, or null if not available
     */
    private static YAWLTelemetry getTelemetrySafely() {
        try {
            return YAWLTelemetry.getInstance();
        } catch (Exception e) {
            LOGGER.debug("YAWLTelemetry not available: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Validates that an argument is not null.
     *
     * @param value The value to check
     * @param name The parameter name for the error message
     * @throws IllegalArgumentException if value is null
     */
    private static void validateNotNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }
}
