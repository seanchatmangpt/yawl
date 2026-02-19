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
 * License for more details.
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

import java.util.concurrent.TimeUnit;

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
 * <p>Usage examples:</p>
 * <pre>{@code
 * // Basic error recording
 * ErrorSpanHelper.recordError("task.execution", "case-123", exception);
 *
 * // Task-specific errors
 * ErrorSpanHelper.recordTaskError("task-456", "case-123", "execute", exception);
 * }</pre>
 */
public class ErrorSpanHelper {

    private static final Logger LOGGER = LogManager.getLogger(ErrorSpanHelper.class);

    /**
     * Records a basic error with YAWL telemetry.
     *
     * @param operation The name of the operation that failed
     * @param caseId The YAWL case identifier
     * @param exception The exception that occurred
     */
    public static void recordError(String operation, String caseId, Exception exception) {
        recordError(operation, caseId, null, null, exception);
    }

    /**
     * Records a task-specific error with YAWL telemetry.
     *
     * @param taskId The YAWL task identifier
     * @param caseId The YAWL case identifier
     * @param operation The name of the operation that failed
     * @param exception The exception that occurred
     */
    public static void recordTaskError(String taskId, String caseId, String operation, Exception exception) {
        recordError(operation + ".task", caseId, taskId, null, exception);
    }

    /**
     * Records a case-level error with YAWL telemetry.
     *
     * @param caseId The YAWL case identifier
     * @param operation The name of the operation that failed
     * @param exception The exception that occurred
     */
    public static void recordCaseError(String caseId, String operation, Exception exception) {
        recordError(operation + ".case", caseId, null, null, exception);
    }

    /**
     * Records a specification-level error with YAWL telemetry.
     *
     * @param specId The YAWL specification identifier
     * @param caseId The YAWL case identifier
     * @param operation The name of the operation that failed
     * @param exception The exception that occurred
     */
    public static void recordSpecificationError(String specId, String caseId, String operation, Exception exception) {
        recordError(operation + ".spec", caseId, null, specId, exception);
    }

    /**
     * Records an error with full context to YAWL telemetry and logs it.
     *
     * @param operation The name of the operation that failed
     * @param caseId The YAWL case identifier (can be null)
     * @param taskId The YAWL task identifier (can be null)
     * @param specId The YAWL specification identifier (can be null)
     * @param exception The exception that occurred
     */
    private static void recordError(String operation, String caseId, String taskId, String specId, Exception exception) {
        // Log the error with structured logging
        String message = String.format("Error in operation '%s'", operation);
        if (caseId != null) {
            message += String.format(" for case '%s'", caseId);
        }
        if (taskId != null) {
            message += String.format(" task '%s'", taskId);
        }
        if (specId != null) {
            message += String.format(" spec '%s'", specId);
        }

        LOGGER.error(message + ": {}", exception.getMessage(), exception);

        // Record metrics via YAWLTelemetry
        if (caseId != null) {
            // Use specific error recording methods based on exception type
            if (exception instanceof org.yawlfoundation.yawl.elements.YDataStateException) {
                YAWLTelemetry.getInstance().recordValidationError(caseId, taskId,
                    "validation", exception.getMessage());
            } else {
                // Log that we don't have a specific method for this error type
                LOGGER.warn("No specific telemetry method for error type: {}",
                    exception.getClass().getSimpleName());
            }
        }
    }

    /**
     * Records an error with timing information.
     *
     * @param operation The name of the operation that failed
     * @param caseId The YAWL case identifier
     * @param exception The exception that occurred
     * @param durationNanos The duration of the operation in nanoseconds (before failure)
     */
    public static void recordErrorWithTiming(String operation, String caseId, Exception exception, long durationNanos) {
        // Log the error
        String message = String.format("Error in operation '%s' for case '%s' after %d ms",
            operation, caseId, TimeUnit.NANOSECONDS.toMillis(durationNanos));
        LOGGER.error(message + ": {}", exception.getMessage(), exception);

        // Record metrics via YAWLTelemetry
        if (caseId != null) {
            // Use specific error recording methods based on exception type
            if (exception instanceof org.yawlfoundation.yawl.elements.YDataStateException) {
                YAWLTelemetry.getInstance().recordValidationError(caseId, null,
                    "validation", exception.getMessage());
            } else {
                // Log that we don't have a specific method for this error type
                LOGGER.warn("No specific telemetry method for error type: {}",
                    exception.getClass().getSimpleName());
            }
        }
    }

    /**
     * Records a deadlock detection event.
     *
     * @param caseId The YAWL case identifier
     * @param specId The YAWL specification identifier
     * @param deadlockedTasks Comma-separated list of deadlocked task IDs
     */
    public static void recordDeadlock(String caseId, String specId, String deadlockedTasks) {
        String operation = "deadlock.detected";
        String message = String.format("Deadlock detected in case '%s', spec '%s', tasks: %s",
            caseId, specId, deadlockedTasks);

        LOGGER.error(message);

        // Record critical deadlock metric
        if (YAWLTelemetry.getInstance() != null) {
            // Parse deadlockedTasks to get count (assuming comma-separated)
            String[] taskArray = deadlockedTasks.split(",");
            int taskCount = taskArray.length;
            YAWLTelemetry.getInstance().recordDeadlock(caseId, specId, taskCount);
        }
    }

    /**
     * Records a lock contention event.
     *
     * @param caseId The YAWL case identifier
     * @param lockName The name of the contended lock
     * @param waitMillis The wait duration in milliseconds
     */
    public static void recordLockContention(String caseId, String lockName, long waitMillis) {
        String operation = "lock.contention";
        String message = String.format("Lock contention detected: case '%s', lock '%s', wait: %d ms",
            caseId, lockName, waitMillis);

        if (waitMillis > 500) {
            LOGGER.warn(message); // Log as warning for high contention
        } else {
            LOGGER.debug(message);
        }

        // Record metrics via YAWLTelemetry
        if (YAWLTelemetry.getInstance() != null) {
            YAWLTelemetry.getInstance().recordLockContention(waitMillis, caseId, operation);
        }
    }
}