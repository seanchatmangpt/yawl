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

package org.yawlfoundation.yawl.pi.predictive;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.yawlfoundation.yawl.pi.PIException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Predicts workflow bottleneck (slowest task) from historical events.
 *
 * <p>Analyzes events to identify the task with highest average wait time
 * between WORKITEM_ENABLED and WORKITEM_STARTED. Provides confidence level
 * based on sample size.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class BottleneckPredictor {

    private final PredictiveModelRegistry registry;
    private final WorkflowEventStore eventStore;

    /**
     * Construct with dependencies.
     *
     * @param registry ONNX model registry (optional for future use)
     * @param eventStore Workflow event store
     * @throws NullPointerException if any dependency is null
     */
    public BottleneckPredictor(PredictiveModelRegistry registry,
                                WorkflowEventStore eventStore) {
        if (registry == null) throw new NullPointerException("registry is required");
        if (eventStore == null) throw new NullPointerException("eventStore is required");

        this.registry = registry;
        this.eventStore = eventStore;
    }

    /**
     * Predict bottleneck task for a specification.
     *
     * <p>Aggregates wait times across all historical cases for each task,
     * identifies task with highest average wait time.
     *
     * @param specId Specification to analyze
     * @return Bottleneck prediction with task name and expected wait
     * @throws PIException If no event data found or retrieval fails
     */
    public BottleneckPrediction predict(YSpecificationID specId) throws PIException {
        try {
            Map<String, TaskWaitStats> taskStats = new HashMap<>();
            long totalEvents = 0;

            List<String> allCaseIds = eventStore.loadEvents("")
                .stream()
                .map(WorkflowEvent::getCaseId)
                .distinct()
                .toList();

            for (String caseId : allCaseIds) {
                List<WorkflowEvent> events = eventStore.loadEvents(caseId);
                totalEvents += events.size();

                Map<String, Long> enableTimes = new HashMap<>();

                for (WorkflowEvent event : events) {
                    String itemId = event.getWorkItemId();

                    if (event.getEventType() == WorkflowEvent.EventType.WORKITEM_ENABLED) {
                        enableTimes.put(itemId, event.getTimestamp().toEpochMilli());
                    } else if (event.getEventType() == WorkflowEvent.EventType.WORKITEM_STARTED) {
                        Long enableTime = enableTimes.get(itemId);
                        if (enableTime != null) {
                            long waitMs = event.getTimestamp().toEpochMilli() - enableTime;

                            String taskName = extractTaskName(itemId);
                            taskStats.computeIfAbsent(taskName, k -> new TaskWaitStats())
                                .addWait(waitMs);
                        }
                    }
                }
            }

            if (taskStats.isEmpty()) {
                throw new PIException(
                    "No event data found for specification: " + specId.getIdentifier(),
                    "predictive");
            }

            String bottleneckTask = taskStats.entrySet().stream()
                .max((a, b) -> Double.compare(a.getValue().avgWaitMs, b.getValue().avgWaitMs))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new PIException(
                    "Failed to identify bottleneck task",
                    "predictive"));

            TaskWaitStats stats = taskStats.get(bottleneckTask);
            double confidence = Math.min(1.0, (double) stats.sampleCount / Math.max(1, totalEvents / 10));

            return new BottleneckPrediction(
                specId.getIdentifier(),
                bottleneckTask,
                stats.avgWaitMs,
                confidence,
                Instant.now()
            );
        } catch (WorkflowEventStore.EventStoreException e) {
            throw new PIException(
                "Failed to load events for bottleneck analysis",
                "predictive",
                e);
        }
    }

    private String extractTaskName(String workItemId) {
        int lastDot = workItemId.lastIndexOf('.');
        return lastDot >= 0 ? workItemId.substring(lastDot + 1) : workItemId;
    }

    /**
     * Accumulates wait statistics for a task.
     */
    private static class TaskWaitStats {
        double totalWaitMs = 0.0;
        int sampleCount = 0;
        double avgWaitMs = 0.0;

        void addWait(long waitMs) {
            totalWaitMs += waitMs;
            sampleCount++;
            avgWaitMs = totalWaitMs / sampleCount;
        }
    }
}
