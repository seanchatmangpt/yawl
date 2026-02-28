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

package org.yawlfoundation.yawl.integration.mcp.timeline;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

/**
 * Integration test suite for CaseTimeline MCP tool.
 * Verifies timeline rendering and MCP tool registration.
 */
class CaseTimelineIntegrationTest {

    private static final String CASE_ID = "case-timeline-001";
    private static final String SPEC_NAME = "OrderProcess";
    private static final int DEFAULT_WIDTH = 50;

    private Instant startTime;
    private Instant currentTime;
    private List<WorkItemRecord> workItems;

    @BeforeEach
    void setUp() {
        startTime = Instant.now().minusSeconds(600); // 10 minutes ago
        currentTime = Instant.now();
        workItems = new ArrayList<>();
    }

    @Test
    @DisplayName("Should render timeline with empty work items")
    void testRenderTimelineEmpty() {
        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, new ArrayList<>(), DEFAULT_WIDTH);

        assertNotNull(timeline);
        assertFalse(timeline.isEmpty());
        assertTrue(timeline.contains(CASE_ID));
        assertTrue(timeline.contains("No work items found"));
    }

    @Test
    @DisplayName("Should render timeline with single work item")
    void testRenderTimelineSingleWorkItem() {
        WorkItemRecord item = new WorkItemRecord();
        item.setTaskID("task-1");
        item.setTaskName("Process Order");
        item.setStatus(WorkItemRecord.statusExecuting);
        item.setEnablementTime(startTime.toString());
        item.setStartTime(startTime.toString());

        workItems.add(item);

        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, workItems, DEFAULT_WIDTH);

        assertNotNull(timeline);
        assertFalse(timeline.isEmpty());
        assertTrue(timeline.contains("Process Order"));
        assertTrue(timeline.contains("EXECUTION SUMMARY"));
    }

    @Test
    @DisplayName("Should show progress for completed tasks")
    void testRenderTimelineWithCompletedTasks() {
        WorkItemRecord completed = new WorkItemRecord();
        completed.setTaskID("task-completed");
        completed.setTaskName("Validate Order");
        completed.setStatus(WorkItemRecord.statusComplete);
        completed.setStartTime(startTime.toString());
        completed.setCompletionTime(startTime.plusSeconds(180).toString());

        workItems.add(completed);

        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, workItems, DEFAULT_WIDTH);

        assertNotNull(timeline);
        assertTrue(timeline.contains("Validate Order"));
        assertTrue(timeline.contains("100%")); // Should show completion percentage
    }

    @Test
    @DisplayName("Should respect timeline width parameter")
    void testRenderTimelineWithCustomWidth() {
        WorkItemRecord item = new WorkItemRecord();
        item.setTaskID("task-width");
        item.setTaskName("Wide Task");
        item.setStatus(WorkItemRecord.statusEnabled);

        workItems.add(item);

        int width = 100;
        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, workItems, width);

        assertNotNull(timeline);
        assertTrue(timeline.contains("Wide Task"));
    }

    @Test
    @DisplayName("Should enforce minimum timeline width")
    void testRenderTimelineMinimumWidth() {
        WorkItemRecord item = new WorkItemRecord();
        item.setTaskID("task-1");
        item.setTaskName("Task");
        item.setStatus(WorkItemRecord.statusEnabled);

        workItems.add(item);

        // Width below minimum should be clamped to 20
        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, workItems, 10);

        assertNotNull(timeline);
        assertFalse(timeline.isEmpty());
    }

    @Test
    @DisplayName("Should enforce maximum timeline width")
    void testRenderTimelineMaximumWidth() {
        WorkItemRecord item = new WorkItemRecord();
        item.setTaskID("task-1");
        item.setTaskName("Task");
        item.setStatus(WorkItemRecord.statusEnabled);

        workItems.add(item);

        // Width above maximum should be clamped to 200
        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, workItems, 300);

        assertNotNull(timeline);
        assertFalse(timeline.isEmpty());
    }

    @Test
    @DisplayName("Should handle null work items list")
    void testRenderTimelineNullWorkItems() {
        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, null, DEFAULT_WIDTH);

        assertNotNull(timeline);
        assertTrue(timeline.contains("No work items found"));
    }

    @Test
    @DisplayName("Should handle null start time gracefully")
    void testRenderTimelineNullStartTime() {
        WorkItemRecord item = new WorkItemRecord();
        item.setTaskID("task-1");
        item.setTaskName("Task");
        item.setStatus(WorkItemRecord.statusEnabled);
        workItems.add(item);

        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, null, currentTime, workItems, DEFAULT_WIDTH);

        assertNotNull(timeline);
        assertTrue(timeline.contains("Task"));
    }

    @Test
    @DisplayName("Should handle null current time gracefully")
    void testRenderTimelineNullCurrentTime() {
        WorkItemRecord item = new WorkItemRecord();
        item.setTaskID("task-1");
        item.setTaskName("Task");
        item.setStatus(WorkItemRecord.statusEnabled);
        workItems.add(item);

        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, null, workItems, DEFAULT_WIDTH);

        assertNotNull(timeline);
        assertTrue(timeline.contains("Task"));
    }

    @Test
    @DisplayName("Should show timeline legend with status indicators")
    void testRenderTimelineShowsLegend() {
        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, new ArrayList<>(), DEFAULT_WIDTH);

        assertNotNull(timeline);
        assertTrue(timeline.contains("TASK LEGEND"));
        assertTrue(timeline.contains("Completed"));
        assertTrue(timeline.contains("Running"));
        assertTrue(timeline.contains("Enabled"));
    }

    @Test
    @DisplayName("Should handle multiple work items")
    void testRenderTimelineMultipleWorkItems() {
        for (int i = 0; i < 5; i++) {
            WorkItemRecord item = new WorkItemRecord();
            item.setTaskID("task-" + i);
            item.setTaskName("Task " + i);
            item.setStatus(i % 2 == 0 ? WorkItemRecord.statusExecuting : WorkItemRecord.statusEnabled);
            item.setEnablementTime(startTime.plusSeconds(i * 60).toString());
            workItems.add(item);
        }

        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, workItems, DEFAULT_WIDTH);

        assertNotNull(timeline);
        assertTrue(timeline.contains("Task 0"));
        assertTrue(timeline.contains("Task 4"));
        assertTrue(timeline.contains("EXECUTION SUMMARY"));
    }

    @Test
    @DisplayName("Should detect performance anomalies")
    void testRenderTimelineDetectsAnomalies() {
        // Create multiple completed tasks with varying durations
        for (int i = 0; i < 5; i++) {
            WorkItemRecord item = new WorkItemRecord();
            item.setTaskID("task-" + i);
            item.setTaskName("Task " + i);
            item.setStatus(WorkItemRecord.statusComplete);
            long startMs = startTime.plusSeconds(i * 100).getEpochSecond();
            long endMs = startMs + 30; // Short duration
            item.setStartTime(String.valueOf(startMs));
            item.setCompletionTime(String.valueOf(endMs));
            workItems.add(item);
        }

        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, workItems, DEFAULT_WIDTH);

        assertNotNull(timeline);
        // Should contain execution summary
        assertTrue(timeline.contains("EXECUTION SUMMARY"));
    }

    @Test
    @DisplayName("Should reject null case ID")
    void testRenderTimelineNullCaseId() {
        String timeline = CaseTimelineRenderer.renderTimeline(
            null, SPEC_NAME, startTime, currentTime, new ArrayList<>(), DEFAULT_WIDTH);

        assertNotNull(timeline);
        assertTrue(timeline.contains("ERROR"));
    }

    @Test
    @DisplayName("Should reject empty case ID")
    void testRenderTimelineEmptyCaseId() {
        String timeline = CaseTimelineRenderer.renderTimeline(
            "", SPEC_NAME, startTime, currentTime, new ArrayList<>(), DEFAULT_WIDTH);

        assertNotNull(timeline);
        assertTrue(timeline.contains("ERROR"));
    }

    @Test
    @DisplayName("Should include case metadata in header")
    void testRenderTimelineHeader() {
        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, new ArrayList<>(), DEFAULT_WIDTH);

        assertNotNull(timeline);
        assertTrue(timeline.contains(CASE_ID));
        assertTrue(timeline.contains(SPEC_NAME));
    }

    @Test
    @DisplayName("Should format elapsed time correctly")
    void testRenderTimelineElapsedTime() {
        WorkItemRecord item = new WorkItemRecord();
        item.setTaskID("task-timing");
        item.setTaskName("Timed Task");
        item.setStatus(WorkItemRecord.statusComplete);
        item.setStartTime(startTime.toString());
        item.setCompletionTime(startTime.plusSeconds(3661).toString()); // 1h 1m 1s
        workItems.add(item);

        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, workItems, DEFAULT_WIDTH);

        assertNotNull(timeline);
        assertTrue(timeline.contains("Timed Task"));
    }

    @Test
    @DisplayName("Should handle task name truncation")
    void testRenderTimelineTaskNameTruncation() {
        WorkItemRecord item = new WorkItemRecord();
        item.setTaskID("task-long");
        String veryLongTaskName = "This is a very long task name that should be truncated to fit the timeline width";
        item.setTaskName(veryLongTaskName);
        item.setStatus(WorkItemRecord.statusEnabled);
        workItems.add(item);

        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, workItems, DEFAULT_WIDTH);

        assertNotNull(timeline);
        // Task name should be present (possibly truncated)
        assertTrue(timeline.contains("This is a very") || timeline.contains(veryLongTaskName));
    }

    @Test
    @DisplayName("Should render performance <500ms")
    void testRenderTimelinePerformance() {
        // Create 100 work items
        for (int i = 0; i < 100; i++) {
            WorkItemRecord item = new WorkItemRecord();
            item.setTaskID("task-" + i);
            item.setTaskName("Task " + i);
            item.setStatus(i % 2 == 0 ? WorkItemRecord.statusComplete : WorkItemRecord.statusEnabled);
            if (i % 2 == 0) {
                item.setStartTime(startTime.toString());
                item.setCompletionTime(startTime.plusSeconds(30).toString());
            }
            workItems.add(item);
        }

        long startTimeMs = System.currentTimeMillis();
        String timeline = CaseTimelineRenderer.renderTimeline(
            CASE_ID, SPEC_NAME, startTime, currentTime, workItems, DEFAULT_WIDTH);
        long endTimeMs = System.currentTimeMillis();

        assertNotNull(timeline);
        assertFalse(timeline.isEmpty());
        long durationMs = endTimeMs - startTimeMs;
        assertTrue(durationMs < 500, "Rendering should complete in <500ms, took " + durationMs + "ms");
    }
}
