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
import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle;
import org.yawlfoundation.yawl.pi.PIException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for CaseOutcomePredictor.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class CaseOutcomePredictorTest {

    private CaseOutcomePredictor predictor;
    private PredictiveModelRegistry modelRegistry;
    private MockWorkflowEventStore mockEventStore;
    private MockWorkflowDNAOracle mockDnaOracle;

    @BeforeEach
    public void setUp() throws PIException {
        Path tempDir = Files.createTempDirectory("pi-models-test");
        modelRegistry = new PredictiveModelRegistry(tempDir);
        mockEventStore = new MockWorkflowEventStore();
        mockDnaOracle = new MockWorkflowDNAOracle();
        predictor = new CaseOutcomePredictor(modelRegistry, mockEventStore, mockDnaOracle);
    }

    @Test
    public void testPredictReturnsValidPrediction() throws Exception {
        String caseId = "case-001";
        mockEventStore.addEvent(caseId, new MockWorkflowEvent(
            "evt-1", caseId, "item-1", WorkflowEvent.EventType.CASE_STARTED,
            Instant.now()
        ));
        mockEventStore.addEvent(caseId, new MockWorkflowEvent(
            "evt-2", caseId, "item-1", WorkflowEvent.EventType.WORKITEM_ENABLED,
            Instant.now().plusSeconds(1)
        ));
        mockEventStore.addEvent(caseId, new MockWorkflowEvent(
            "evt-3", caseId, "item-1", WorkflowEvent.EventType.WORKITEM_STARTED,
            Instant.now().plusSeconds(2)
        ));
        mockEventStore.addEvent(caseId, new MockWorkflowEvent(
            "evt-4", caseId, "item-1", WorkflowEvent.EventType.CASE_COMPLETED,
            Instant.now().plusSeconds(3)
        ));

        CaseOutcomePrediction prediction = predictor.predict(caseId);

        assertNotNull(prediction);
        assertTrue(prediction.completionProbability() >= 0.0 && prediction.completionProbability() <= 1.0);
        assertTrue(prediction.riskScore() >= 0.0 && prediction.riskScore() <= 1.0);
        assertNotNull(prediction.primaryRiskFactor());
    }

    @Test
    public void testPredictWithCancellation() throws Exception {
        String caseId = "case-002";
        mockEventStore.addEvent(caseId, new MockWorkflowEvent(
            "evt-1", caseId, "item-1", WorkflowEvent.EventType.CASE_STARTED,
            Instant.now()
        ));
        mockEventStore.addEvent(caseId, new MockWorkflowEvent(
            "evt-2", caseId, "item-1", WorkflowEvent.EventType.CASE_CANCELLED,
            Instant.now().plusSeconds(5)
        ));

        CaseOutcomePrediction prediction = predictor.predict(caseId);

        assertNotNull(prediction);
        assertTrue(prediction.riskScore() > 0.5, "Risk should be elevated for cancelled cases");
        assertTrue(prediction.primaryRiskFactor().toLowerCase().contains("cancel"));
    }

    @Test
    public void testPredictThrowsOnNoEvents() {
        String caseId = "case-nonexistent";

        PIException exception = assertThrows(PIException.class, () -> {
            predictor.predict(caseId);
        });

        assertTrue(exception.getMessage().contains("No events"));
    }

    @Test
    public void testPredictionFallsBackToDnaOracleWhenOnnxUnavailable() throws Exception {
        String caseId = "case-003";
        mockEventStore.addEvent(caseId, new MockWorkflowEvent(
            "evt-1", caseId, "item-1", WorkflowEvent.EventType.CASE_STARTED,
            Instant.now()
        ));
        mockEventStore.addEvent(caseId, new MockWorkflowEvent(
            "evt-2", caseId, "item-1", WorkflowEvent.EventType.WORKITEM_STARTED,
            Instant.now().plusSeconds(10)
        ));
        mockEventStore.addEvent(caseId, new MockWorkflowEvent(
            "evt-3", caseId, "item-1", WorkflowEvent.EventType.CASE_COMPLETED,
            Instant.now().plusSeconds(20)
        ));

        CaseOutcomePrediction prediction = predictor.predict(caseId);

        assertNotNull(prediction);
        assertTrue(!prediction.fromOnnxModel(), "Should use DNA oracle when ONNX model unavailable");
    }

    /**
     * Mock implementation of WorkflowEventStore for testing.
     */
    private static class MockWorkflowEventStore extends WorkflowEventStore {
        private final List<WorkflowEvent> events = new ArrayList<>();

        void addEvent(String caseId, WorkflowEvent event) {
            events.add(event);
        }

        @Override
        public List<WorkflowEvent> loadEvents(String caseId) throws EventStoreException {
            return events.stream()
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

    /**
     * Mock implementation of WorkflowDNAOracle for testing.
     */
    private static class MockWorkflowDNAOracle extends WorkflowDNAOracle {
        MockWorkflowDNAOracle() {
            super(null);
        }
    }
}
