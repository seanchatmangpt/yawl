/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.consumers;

import java.time.Instant;

/**
 * Represents the result of an A2A task execution.
 *
 * @param taskId the unique task ID
 * @param taskType the type of task performed
 * @param status the execution status
 * @param message descriptive message about the result
 * @param executionTimestamp when the task was executed
 */
public record TaskResult(
    String taskId,
    String taskType,
    String status,
    String message,
    Instant executionTimestamp
) {

    /**
     * Task status constants.
     */
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * Creates a successful task result.
     */
    public static TaskResult success(String taskId, String taskType, String message) {
        return new TaskResult(taskId, taskType, STATUS_COMPLETED, message, Instant.now());
    }

    /**
     * Creates a failed task result.
     */
    public static TaskResult failure(String taskId, String taskType, String message) {
        return new TaskResult(taskId, taskType, STATUS_FAILED, message, Instant.now());
    }

    /**
     * Creates a pending task result.
     */
    public static TaskResult pending(String taskId, String taskType, String message) {
        return new TaskResult(taskId, taskType, STATUS_PENDING, message, Instant.now());
    }

    /**
     * Returns true if the task was successful.
     */
    public boolean isSuccessful() {
        return STATUS_COMPLETED.equals(status);
    }

    /**
     * Returns true if the task is pending.
     */
    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    /**
     * Returns true if the task failed.
     */
    public boolean isFailed() {
        return STATUS_FAILED.equals(status);
    }

    /**
     * Returns true if the task is completed.
     */
    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    /**
     * Returns true if the task was cancelled.
     */
    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(status);
    }

    /**
     * Gets a user-friendly status message.
     */
    public String getFriendlyStatus() {
        return switch (status) {
            case STATUS_SENT -> "Sent to provider";
            case STATUS_PENDING -> "Waiting for response";
            case STATUS_PROCESSING -> "Currently processing";
            case STATUS_COMPLETED -> "Completed successfully";
            case STATUS_FAILED -> "Failed to complete";
            case STATUS_CANCELLED -> "Cancelled";
            default -> "Unknown status";
        };
    }
}