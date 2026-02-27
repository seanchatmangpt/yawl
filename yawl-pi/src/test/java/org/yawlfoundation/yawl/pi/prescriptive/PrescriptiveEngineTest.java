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

package org.yawlfoundation.yawl.pi.prescriptive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.yawlfoundation.yawl.integration.processmining.XesToYawlSpecGenerator;
import org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle;
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.predictive.CaseOutcomePrediction;
import org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for PrescriptiveEngine.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class PrescriptiveEngineTest {

    private PrescriptiveEngine engine;
    private PredictiveModelRegistry modelRegistry;
    private WorkflowDNAOracle dnaOracle;
    private ProcessConstraintModel constraintModel;

    @BeforeEach
    public void setUp() throws PIException {
        Path tempDir = Files.createTempDirectory("pi-models-test");
        modelRegistry = new PredictiveModelRegistry(tempDir);
        dnaOracle = new WorkflowDNAOracle(new XesToYawlSpecGenerator(1));
        constraintModel = new ProcessConstraintModel();
        engine = new PrescriptiveEngine(dnaOracle, modelRegistry, constraintModel);
    }

    @Test
    public void testRecommendReturnsNonEmptyList() throws PIException {
        String caseId = "case-001";
        CaseOutcomePrediction prediction = new CaseOutcomePrediction(
            caseId,
            0.5,
            0.3,
            "low risk",
            false,
            Instant.now()
        );

        List<ProcessAction> actions = engine.recommend(caseId, prediction);

        assertNotNull(actions);
        assertFalse(actions.isEmpty(), "Should always return at least NoOpAction");
    }

    @Test
    public void testRecommendWithLowRiskIncludesNoOp() throws PIException {
        String caseId = "case-002";
        CaseOutcomePrediction prediction = new CaseOutcomePrediction(
            caseId,
            0.8,
            0.2,
            "low risk",
            false,
            Instant.now()
        );

        List<ProcessAction> actions = engine.recommend(caseId, prediction);

        boolean hasNoOp = actions.stream().anyMatch(a -> a instanceof NoOpAction);
        assertTrue(hasNoOp, "Should include NoOpAction for low-risk cases");
    }

    @Test
    public void testRecommendWithHighRiskIncludesEscalate() throws PIException {
        String caseId = "case-003";
        CaseOutcomePrediction prediction = new CaseOutcomePrediction(
            caseId,
            0.2,
            0.8,
            "high failure risk",
            false,
            Instant.now()
        );

        List<ProcessAction> actions = engine.recommend(caseId, prediction);

        boolean hasEscalate = actions.stream().anyMatch(a -> a instanceof EscalateAction);
        assertTrue(hasEscalate, "Should include EscalateAction for high-risk cases");
    }

    @Test
    public void testRecommendWithMediumRiskIncludesReallocate() throws PIException {
        String caseId = "case-004";
        CaseOutcomePrediction prediction = new CaseOutcomePrediction(
            caseId,
            0.6,
            0.5,
            "medium risk",
            false,
            Instant.now()
        );

        List<ProcessAction> actions = engine.recommend(caseId, prediction);

        boolean hasReallocate = actions.stream().anyMatch(a -> a instanceof ReallocateResourceAction);
        assertTrue(hasReallocate, "Should include ReallocateResourceAction for medium-risk cases");
    }

    @Test
    public void testRecommendAlwaysIncludesReroute() throws PIException {
        String caseId = "case-005";
        CaseOutcomePrediction prediction = new CaseOutcomePrediction(
            caseId,
            0.5,
            0.5,
            "medium risk",
            false,
            Instant.now()
        );

        List<ProcessAction> actions = engine.recommend(caseId, prediction);

        boolean hasReroute = actions.stream().anyMatch(a -> a instanceof RerouteAction);
        assertTrue(hasReroute, "Should include RerouteAction");
    }

    @Test
    public void testRecommendActionsSortedByScore() throws PIException {
        String caseId = "case-006";
        CaseOutcomePrediction prediction = new CaseOutcomePrediction(
            caseId,
            0.1,
            0.8,
            "very high risk",
            false,
            Instant.now()
        );

        List<ProcessAction> actions = engine.recommend(caseId, prediction);

        assertNotNull(actions);
        if (actions.size() > 1) {
            boolean isOrdered = true;
            for (int i = 1; i < actions.size(); i++) {
                double prevScore = actions.get(i - 1).expectedImprovementScore();
                double currScore = actions.get(i).expectedImprovementScore();
                if (prevScore < currScore) {
                    isOrdered = false;
                    break;
                }
            }
            assertTrue(isOrdered, "Actions should be sorted by score (highest first)");
        }
    }
}
