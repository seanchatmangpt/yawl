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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ProcessMiningTrainingDataExtractor.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ProcessMiningTrainingDataExtractorTest {

    private ProcessMiningTrainingDataExtractor extractor;
    private MockWorkflowEventStore mockEventStore;

    @BeforeEach
    public void setUp() {
        mockEventStore = new MockWorkflowEventStore();
        extractor = new ProcessMiningTrainingDataExtractor(mockEventStore);
    }

    @Test
    public void testExtractTabularReturnsValidDataset() throws Exception {
        addTestCaseEvents("case-001", false);
        addTestCaseEvents("case-002", true);

        YSpecificationID specId = new YSpecificationID("test-spec", "1.0", "http://test");
        TrainingDataset dataset = extractor.extractTabular(specId, 10);

        assertNotNull(dataset);
        assertTrue(dataset.rows().size() > 0, "Should extract at least one case");
        assertEquals("test-spec", dataset.specificationId());
    }

    @Test
    public void testExtractFeatureNames() throws Exception {
        addTestCaseEvents("case-001", false);

        YSpecificationID specId = new YSpecificationID("test-spec", "1.0", "http://test");
        TrainingDataset dataset = extractor.extractTabular(specId, 10);

        assertNotNull(dataset.featureNames());
        assertTrue(dataset.featureNames().contains("caseDurationMs"));
        assertTrue(dataset.featureNames().contains("taskCount"));
        assertTrue(dataset.featureNames().contains("distinctWorkItems"));
        assertTrue(dataset.featureNames().contains("hadCancellations"));
        assertTrue(dataset.featureNames().contains("avgTaskWaitMs"));
    }

    @Test
    public void testExtractLabelsCancelled() throws Exception {
        addTestCaseEvents("case-cancelled", true);

        YSpecificationID specId = new YSpecificationID("test-spec", "1.0", "http://test");
        TrainingDataset dataset = extractor.extractTabular(specId, 10);

        assertTrue(dataset.labels().stream().anyMatch(l -> l.equals("failed")),
            "Should mark cancelled cases as failed");
    }

    @Test
    public void testExtractLabelsCompleted() throws Exception {
        addTestCaseEvents("case-completed", false);

        YSpecificationID specId = new YSpecificationID("test-spec", "1.0", "http://test");
        TrainingDataset dataset = extractor.extractTabular(specId, 10);

        assertTrue(dataset.labels().stream().anyMatch(l -> l.equals("completed")),
            "Should mark non-cancelled cases as completed");
    }

    @Test
    public void testToCsvFormat() throws Exception {
        addTestCaseEvents("case-001", false);

        YSpecificationID specId = new YSpecificationID("test-spec", "1.0", "http://test");
        TrainingDataset dataset = extractor.extractTabular(specId, 10);
        String csv = extractor.toCsv(dataset);

        assertNotNull(csv);
        assertTrue(csv.contains("caseDurationMs"), "CSV should contain header");
        assertTrue(csv.contains(","), "CSV should contain commas");
        assertTrue(csv.contains("completed") || csv.contains("failed"),
            "CSV should contain labels");
    }

    @Test
    public void testToCsvMultipleRows() throws Exception {
        addTestCaseEvents("case-001", false);
        addTestCaseEvents("case-002", false);

        YSpecificationID specId = new YSpecificationID("test-spec", "1.0", "http://test");
        TrainingDataset dataset = extractor.extractTabular(specId, 10);
        String csv = extractor.toCsv(dataset);

        String[] lines = csv.split("\n");
        assertTrue(lines.length > 1, "CSV should have header + at least one data row");
    }

    private void addTestCaseEvents(String caseId, boolean cancelled) {
        Instant now = Instant.now();

        mockEventStore.addEvent(new MockWorkflowEvent(
            "evt-1", caseId, "item-1", WorkflowEvent.EventType.CASE_STARTED, now
        ));

        mockEventStore.addEvent(new MockWorkflowEvent(
            "evt-2", caseId, "item-1", WorkflowEvent.EventType.WORKITEM_ENABLED,
            now.plusSeconds(1)
        ));

        mockEventStore.addEvent(new MockWorkflowEvent(
            "evt-3", caseId, "item-1", WorkflowEvent.EventType.WORKITEM_STARTED,
            now.plusSeconds(2)
        ));

        if (cancelled) {
            mockEventStore.addEvent(new MockWorkflowEvent(
                "evt-4", caseId, "item-1", WorkflowEvent.EventType.CASE_CANCELLED,
                now.plusSeconds(3)
            ));
        } else {
            mockEventStore.addEvent(new MockWorkflowEvent(
                "evt-4", caseId, "item-1", WorkflowEvent.EventType.CASE_COMPLETED,
                now.plusSeconds(10)
            ));
        }
    }

    /**
     * Mock implementation of WorkflowEventStore for testing.
     */
    private static class MockWorkflowEventStore extends WorkflowEventStore {
        private final List<WorkflowEvent> allEvents = new ArrayList<>();

        void addEvent(WorkflowEvent event) {
            allEvents.add(event);
        }

        @Override
        public List<WorkflowEvent> loadEvents(String caseId) throws EventStoreException {
            if (caseId.isEmpty()) {
                return new ArrayList<>(allEvents);
            }
            return allEvents.stream()
                .filter(e -> e.getCaseId().equals(caseId))
                .toList();
        }
    }

    /**
     * Mock implementation of WorkflowEvent for testing.
     */
    private static class MockWorkflowEvent extends WorkflowEvent {
        private final String eventId;
        private final String caseId;
        private final String workItemId;
        private final EventType eventType;
        private final Instant timestamp;

        MockWorkflowEvent(String eventId, String caseId, String workItemId,
                          EventType eventType, Instant timestamp) {
            this.eventId = eventId;
            this.caseId = caseId;
            this.workItemId = workItemId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }

        @Override
        public String getEventId() { return eventId; }
        @Override
        public String getCaseId() { return caseId; }
        @Override
        public String getWorkItemId() { return workItemId; }
        @Override
        public EventType getEventType() { return eventType; }
        @Override
        public Instant getTimestamp() { return timestamp; }
    }
}
