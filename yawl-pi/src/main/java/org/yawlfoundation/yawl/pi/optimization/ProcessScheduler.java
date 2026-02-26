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

package org.yawlfoundation.yawl.pi.optimization;

import org.yawlfoundation.yawl.pi.PIException;

import java.time.Instant;
import java.util.*;

/**
 * Schedules process tasks using shortest processing time (SPT) algorithm.
 *
 * <p>Tasks are ordered by increasing estimated duration, minimizing average
 * completion time and total flow time through the workflow.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ProcessScheduler {

    /**
     * Create a new process scheduler.
     */
    public ProcessScheduler() {
    }

    /**
     * Schedule tasks using shortest processing time algorithm.
     *
     * @param taskIds task identifiers to schedule
     * @param estimatedDurationsMs estimated duration in milliseconds for each task
     * @param startTime base start time for the schedule
     * @return scheduling result with task order and start times
     * @throws PIException if any task duration is invalid
     */
    public SchedulingResult schedule(List<String> taskIds,
                                      Map<String, Long> estimatedDurationsMs,
                                      Instant startTime) throws PIException {
        if (taskIds == null || taskIds.isEmpty()) {
            return new SchedulingResult(
                List.of(),
                Map.of(),
                true,
                null
            );
        }

        if (estimatedDurationsMs == null) {
            throw new PIException("Durations map cannot be null", "optimization");
        }

        // Validate all durations are positive
        for (String taskId : taskIds) {
            Long duration = estimatedDurationsMs.get(taskId);
            if (duration == null) {
                throw new PIException(
                    "Missing duration for task: " + taskId,
                    "optimization"
                );
            }
            if (duration <= 0) {
                throw new PIException(
                    "Task '" + taskId + "' has invalid duration: " + duration,
                    "optimization"
                );
            }
        }

        // Sort by duration (SPT)
        List<String> sorted = new ArrayList<>(taskIds);
        sorted.sort(
            Comparator.comparingLong(id -> estimatedDurationsMs.get(id))
        );

        // Compute start times
        Map<String, Instant> startTimes = new LinkedHashMap<>();
        Instant current = startTime;

        for (String taskId : sorted) {
            startTimes.put(taskId, current);
            current = current.plusMillis(estimatedDurationsMs.get(taskId));
        }

        return new SchedulingResult(
            sorted,
            startTimes,
            true,
            null
        );
    }
}
