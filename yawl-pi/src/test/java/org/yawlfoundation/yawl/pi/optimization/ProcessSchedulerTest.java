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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.pi.PIException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProcessScheduler.
 *
 * Tests verify that the Shortest Processing Time (SPT) algorithm correctly
 * schedules tasks and computes start times.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class ProcessSchedulerTest {

    private ProcessScheduler scheduler;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        scheduler = new ProcessScheduler();
        baseTime = Instant.now();
    }

    @Test
    void testSchedule_ShortestProcessingTimeOrdering() throws PIException {
        List<String> taskIds = List.of("task1", "task2", "task3");
        Map<String, Long> durations = Map.of(
            "task1", 300L,
            "task2", 100L,
            "task3", 200L
        );

        SchedulingResult result = scheduler.schedule(taskIds, durations, baseTime);

        assertNotNull(result);
        assertTrue(result.feasible());
        assertNull(result.infeasibilityReason());

        // SPT: shortest first: 100, 200, 300
        List<String> expectedOrder = List.of("task2", "task3", "task1");
        assertEquals(expectedOrder, result.orderedTaskIds());
    }

    @Test
    void testSchedule_ScheduledStartTimes() throws PIException {
        List<String> taskIds = List.of("task1", "task2", "task3");
        Map<String, Long> durations = Map.of(
            "task1", 100L,
            "task2", 200L,
            "task3", 150L
        );

        SchedulingResult result = scheduler.schedule(taskIds, durations, baseTime);

        assertNotNull(result);
        assertEquals(3, result.scheduledStartTimes().size());

        // SPT: task1 (100ms) first, then task3 (150ms), then task2 (200ms)
        assertEquals(baseTime, result.scheduledStartTimes().get("task1"));

        // Task3 (150ms) starts after task1
        assertEquals(baseTime.plusMillis(100), result.scheduledStartTimes().get("task3"));

        // Task2 (200ms) starts after task3
        assertEquals(baseTime.plusMillis(250), result.scheduledStartTimes().get("task2"));
    }

    @Test
    void testSchedule_EmptyTaskList() throws PIException {
        SchedulingResult result = scheduler.schedule(List.of(), Map.of(), baseTime);

        assertNotNull(result);
        assertTrue(result.feasible());
        assertEquals(0, result.orderedTaskIds().size());
        assertEquals(0, result.scheduledStartTimes().size());
    }

    @Test
    void testSchedule_SingleTask() throws PIException {
        List<String> taskIds = List.of("task1");
        Map<String, Long> durations = Map.of("task1", 500L);

        SchedulingResult result = scheduler.schedule(taskIds, durations, baseTime);

        assertNotNull(result);
        assertEquals(1, result.orderedTaskIds().size());
        assertEquals("task1", result.orderedTaskIds().get(0));
        assertEquals(baseTime, result.scheduledStartTimes().get("task1"));
    }

    @Test
    void testSchedule_MissingDurationThrows() {
        List<String> taskIds = List.of("task1", "task2");
        Map<String, Long> durations = Map.of("task1", 100L);  // task2 missing

        assertThrows(PIException.class, () -> {
            scheduler.schedule(taskIds, durations, baseTime);
        });
    }

    @Test
    void testSchedule_ZeroDurationThrows() {
        List<String> taskIds = List.of("task1");
        Map<String, Long> durations = Map.of("task1", 0L);

        assertThrows(PIException.class, () -> {
            scheduler.schedule(taskIds, durations, baseTime);
        });
    }

    @Test
    void testSchedule_NegativeDurationThrows() {
        List<String> taskIds = List.of("task1");
        Map<String, Long> durations = Map.of("task1", -100L);

        assertThrows(PIException.class, () -> {
            scheduler.schedule(taskIds, durations, baseTime);
        });
    }

    @Test
    void testSchedule_NullDurationsMapThrows() {
        List<String> taskIds = List.of("task1");

        assertThrows(PIException.class, () -> {
            scheduler.schedule(taskIds, null, baseTime);
        });
    }

    @Test
    void testSchedule_NullTaskListIsEmpty() throws PIException {
        SchedulingResult result = scheduler.schedule(null, Map.of(), baseTime);

        assertNotNull(result);
        assertEquals(0, result.orderedTaskIds().size());
    }

    @Test
    void testSchedule_AllSameDuration() throws PIException {
        List<String> taskIds = List.of("task1", "task2", "task3");
        Map<String, Long> durations = Map.of(
            "task1", 100L,
            "task2", 100L,
            "task3", 100L
        );

        SchedulingResult result = scheduler.schedule(taskIds, durations, baseTime);

        assertNotNull(result);
        assertEquals(3, result.orderedTaskIds().size());
        // All have same duration, so any order is valid; the third task starts at baseTime+200ms
        assertEquals(baseTime.plusMillis(200),
            result.scheduledStartTimes().get(result.orderedTaskIds().get(2)));
    }

    @Test
    void testSchedule_LargeDurations() throws PIException {
        List<String> taskIds = List.of("quick", "slow");
        Map<String, Long> durations = Map.of(
            "quick", 1L,
            "slow", 86400000L  // 1 day in ms
        );

        SchedulingResult result = scheduler.schedule(taskIds, durations, baseTime);

        assertNotNull(result);
        assertEquals(2, result.orderedTaskIds().size());
        // "slow" should be first (SPT: shortest first is wrong - let me check the code)
        // Actually looking at the scheduler, it sorts by shortest first
        assertEquals("quick", result.orderedTaskIds().get(0));
        assertEquals("slow", result.orderedTaskIds().get(1));
    }

    @Test
    void testSchedule_ScheduleIsFeasible() throws PIException {
        List<String> taskIds = List.of("task1", "task2");
        Map<String, Long> durations = Map.of(
            "task1", 100L,
            "task2", 200L
        );

        SchedulingResult result = scheduler.schedule(taskIds, durations, baseTime);

        assertTrue(result.feasible());
        assertNull(result.infeasibilityReason());
    }

    @Test
    void testSchedule_ManyTasks() throws PIException {
        Map<String, Long> durations = new HashMap<>();
        List<String> taskIds = new java.util.ArrayList<>();

        for (int i = 0; i < 100; i++) {
            String taskId = "task" + i;
            taskIds.add(taskId);
            durations.put(taskId, (long) (i + 1) * 10);
        }

        SchedulingResult result = scheduler.schedule(taskIds, durations, baseTime);

        assertNotNull(result);
        assertEquals(100, result.orderedTaskIds().size());
        assertEquals(100, result.scheduledStartTimes().size());
        assertTrue(result.feasible());
    }

    @Test
    void testSchedule_ScheduledTimesIncreaseMonotonically() throws PIException {
        List<String> taskIds = List.of("a", "b", "c", "d");
        Map<String, Long> durations = Map.of(
            "a", 50L,
            "b", 100L,
            "c", 75L,
            "d", 200L
        );

        SchedulingResult result = scheduler.schedule(taskIds, durations, baseTime);

        List<String> orderedTasks = result.orderedTaskIds();
        Instant prevEndTime = baseTime;

        for (String taskId : orderedTasks) {
            Instant startTime = result.scheduledStartTimes().get(taskId);
            assertTrue(startTime.isAfter(prevEndTime) || startTime.equals(prevEndTime));
            prevEndTime = startTime.plusMillis(durations.get(taskId));
        }
    }
}
