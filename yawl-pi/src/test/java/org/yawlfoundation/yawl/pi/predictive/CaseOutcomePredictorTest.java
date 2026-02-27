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
import org.yawlfoundation.yawl.integration.processmining.XesToYawlSpecGenerator;
import org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle;
import org.yawlfoundation.yawl.pi.PIException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private WorkflowEventStore eventStore;
    private WorkflowDNAOracle dnaOracle;

    @BeforeEach
    public void setUp() throws Exception {
        Path tempDir = Files.createTempDirectory("pi-models-test");
        modelRegistry = new PredictiveModelRegistry(tempDir);
        eventStore = createTestEventStore();
        dnaOracle = new WorkflowDNAOracle(new XesToYawlSpecGenerator(1));
        predictor = new CaseOutcomePredictor(modelRegistry, eventStore, dnaOracle);
    }

    @Test
    public void testPredictReturnsValidPrediction() throws Exception {
        String caseId = "case-001";
        eventStore.appendNext(new WorkflowEvent(
            WorkflowEvent.EventType.CASE_STARTED, "test-spec", caseId, "item-1",
            Map.of()
        ));
        eventStore.appendNext(new WorkflowEvent(
            WorkflowEvent.EventType.WORKITEM_ENABLED, "test-spec", caseId, "item-1",
            Map.of()
        ));
        eventStore.appendNext(new WorkflowEvent(
            WorkflowEvent.EventType.WORKITEM_STARTED, "test-spec", caseId, "item-1",
            Map.of()
        ));
        eventStore.appendNext(new WorkflowEvent(
            WorkflowEvent.EventType.CASE_COMPLETED, "test-spec", caseId, "item-1",
            Map.of()
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
        eventStore.appendNext(new WorkflowEvent(
            WorkflowEvent.EventType.CASE_STARTED, "test-spec", caseId, "item-1",
            Map.of()
        ));
        eventStore.appendNext(new WorkflowEvent(
            WorkflowEvent.EventType.CASE_CANCELLED, "test-spec", caseId, "item-1",
            Map.of()
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
        eventStore.appendNext(new WorkflowEvent(
            WorkflowEvent.EventType.CASE_STARTED, "test-spec", caseId, "item-1",
            Map.of()
        ));
        eventStore.appendNext(new WorkflowEvent(
            WorkflowEvent.EventType.WORKITEM_STARTED, "test-spec", caseId, "item-1",
            Map.of()
        ));
        eventStore.appendNext(new WorkflowEvent(
            WorkflowEvent.EventType.CASE_COMPLETED, "test-spec", caseId, "item-1",
            Map.of()
        ));

        CaseOutcomePrediction prediction = predictor.predict(caseId);

        assertNotNull(prediction);
        assertTrue(!prediction.fromOnnxModel(), "Should use DNA oracle when ONNX model unavailable");
    }

    private static WorkflowEventStore createTestEventStore() throws Exception {
        // Create H2 in-memory datasource
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test-case-outcome-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        // Create real store (it initializes its own schema on first use)
        return new WorkflowEventStore(ds);
    }
}
