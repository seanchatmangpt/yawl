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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Extracts tabular training data from workflow event store.
 *
 * <p>Reads workflow events for a specification, computes features per case
 * (duration, task count, cancellations, etc.), and produces a TrainingDataset
 * suitable for ONNX model training. Uses virtual threads for parallel case processing.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ProcessMiningTrainingDataExtractor {

    private final WorkflowEventStore eventStore;

    /**
     * Construct with event store reference.
     *
     * @param eventStore Workflow event store
     * @throws NullPointerException if eventStore is null
     */
    public ProcessMiningTrainingDataExtractor(WorkflowEventStore eventStore) {
        if (eventStore == null) {
            throw new NullPointerException("eventStore is required");
        }
        this.eventStore = eventStore;
    }

    /**
     * Extract tabular training dataset from workflow history.
     *
     * <p>Computes features: case duration ms, task count, distinct work items,
     * cancellation indicator, average task wait ms.
     * Labels: "completed" or "failed" based on presence of CASE_CANCELLED.
     *
     * @param specId Workflow specification
     * @param maxCases Maximum number of cases to extract (for sampling)
     * @return Training dataset with features and labels
     * @throws WorkflowEventStore.EventStoreException if event retrieval fails
     */
    public TrainingDataset extractTabular(YSpecificationID specId, int maxCases)
            throws WorkflowEventStore.EventStoreException {

        List<String> featureNames = List.of(
            "caseDurationMs",
            "taskCount",
            "distinctWorkItems",
            "hadCancellations",
            "avgTaskWaitMs"
        );

        Map<String, CaseFeatures> caseData = new ConcurrentHashMap<>();
        Executor executor = Executors.newVirtualThreadPerTaskExecutor();

        List<String> allCaseIds = eventStore.loadCaseIds(specId.getIdentifier())
            .stream()
            .limit(maxCases)
            .toList();

        for (String caseId : allCaseIds) {
            executor.execute(() -> {
                try {
                    List<WorkflowEvent> events = eventStore.loadEvents(caseId);
                    if (!events.isEmpty()) {
                        caseData.put(caseId, new CaseFeatures(events));
                    }
                } catch (WorkflowEventStore.EventStoreException e) {
                    Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), e);
                }
            });
        }

        ((java.util.concurrent.ExecutorService) executor).shutdown();
        try {
            ((java.util.concurrent.ExecutorService) executor).awaitTermination(
                Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<double[]> rows = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        caseData.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .forEach(entry -> {
                CaseFeatures cf = entry.getValue();
                rows.add(new double[] {
                    cf.caseDurationMs,
                    cf.taskCount,
                    cf.distinctWorkItems,
                    cf.hadCancellations ? 1.0 : 0.0,
                    cf.avgTaskWaitMs
                });
                labels.add(cf.hadCancellations ? "failed" : "completed");
            });

        return new TrainingDataset(
            featureNames,
            rows,
            labels,
            specId.getIdentifier(),
            rows.size()
        );
    }

    /**
     * Convert training dataset to CSV format.
     *
     * @param dataset Dataset to convert
     * @return CSV string with header row followed by data rows
     */
    public String toCsv(TrainingDataset dataset) {
        StringJoiner joiner = new StringJoiner("\n");

        StringJoiner headerJoiner = new StringJoiner(",");
        dataset.featureNames().forEach(headerJoiner::add);
        headerJoiner.add("label");
        joiner.add(headerJoiner.toString());

        for (int i = 0; i < dataset.rows().size(); i++) {
            double[] row = dataset.rows().get(i);
            StringJoiner dataJoiner = new StringJoiner(",");
            for (double val : row) {
                dataJoiner.add(String.valueOf(val));
            }
            dataJoiner.add(dataset.labels().get(i));
            joiner.add(dataJoiner.toString());
        }

        return joiner.toString();
    }

    /**
     * Immutable container for computed features of a single case.
     */
    private static class CaseFeatures {
        final long caseDurationMs;
        final int taskCount;
        final int distinctWorkItems;
        final boolean hadCancellations;
        final double avgTaskWaitMs;

        CaseFeatures(List<WorkflowEvent> events) {
            if (events.isEmpty()) {
                this.caseDurationMs = 0;
                this.taskCount = 0;
                this.distinctWorkItems = 0;
                this.hadCancellations = false;
                this.avgTaskWaitMs = 0.0;
                return;
            }

            events.sort(Comparator.comparing(WorkflowEvent::getTimestamp));

            long startTime = events.get(0).getTimestamp().toEpochMilli();
            long endTime = events.get(events.size() - 1).getTimestamp().toEpochMilli();
            this.caseDurationMs = endTime - startTime;

            this.taskCount = (int) events.stream()
                .filter(e -> e.getEventType() == WorkflowEvent.EventType.WORKITEM_STARTED)
                .count();

            this.distinctWorkItems = (int) events.stream()
                .map(WorkflowEvent::getWorkItemId)
                .distinct()
                .count();

            this.hadCancellations = events.stream()
                .anyMatch(e -> e.getEventType() == WorkflowEvent.EventType.CASE_CANCELLED);

            LinkedHashMap<String, Long> enableTimes = new LinkedHashMap<>();
            LinkedHashMap<String, Long> startTimes = new LinkedHashMap<>();

            for (WorkflowEvent event : events) {
                String itemId = event.getWorkItemId();
                if (event.getEventType() == WorkflowEvent.EventType.WORKITEM_ENABLED) {
                    enableTimes.put(itemId, event.getTimestamp().toEpochMilli());
                } else if (event.getEventType() == WorkflowEvent.EventType.WORKITEM_STARTED) {
                    startTimes.put(itemId, event.getTimestamp().toEpochMilli());
                }
            }

            double totalWait = 0.0;
            int countWaits = 0;
            for (Map.Entry<String, Long> entry : startTimes.entrySet()) {
                Long enableTime = enableTimes.get(entry.getKey());
                if (enableTime != null) {
                    totalWait += entry.getValue() - enableTime;
                    countWaits++;
                }
            }

            this.avgTaskWaitMs = countWaits > 0 ? totalWait / countWaits : 0.0;
        }
    }
}
