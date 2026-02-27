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

import org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle;
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.predictive.CaseOutcomePrediction;
import org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Prescriptive AI engine: recommends actions to improve case outcomes.
 *
 * <p>Given a case outcome prediction and risk assessment, generates ranked
 * list of recommended interventions (reroute, escalate, reallocate, or no-op).
 * Filters recommendations against process constraint model to ensure feasibility.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class PrescriptiveEngine {

    private final WorkflowDNAOracle dnaOracle;
    private final PredictiveModelRegistry registry;
    private final ProcessConstraintModel constraintModel;
    private final ActionRecommender recommender;

    /**
     * Construct with dependencies.
     *
     * @param dnaOracle DNA oracle for alternative path extraction
     * @param registry ONNX model registry (for future use)
     * @param constraintModel Constraint model for feasibility checking
     * @throws NullPointerException if any dependency is null
     */
    public PrescriptiveEngine(WorkflowDNAOracle dnaOracle,
                               PredictiveModelRegistry registry,
                               ProcessConstraintModel constraintModel) {
        if (dnaOracle == null) throw new NullPointerException("dnaOracle is required");
        if (registry == null) throw new NullPointerException("registry is required");
        if (constraintModel == null) throw new NullPointerException("constraintModel is required");

        this.dnaOracle = dnaOracle;
        this.registry = registry;
        this.constraintModel = constraintModel;
        this.recommender = new ActionRecommender();
    }

    /**
     * Recommend actions for a case given its outcome prediction.
     *
     * <p>Generation strategy:
     * <ul>
     *   <li>If DNA oracle has alternative paths → generate RerouteAction for each
     *   <li>If risk > 0.7 → add EscalateAction
     *   <li>If risk &gt;= 0.5 → add ReallocateResourceAction
     *   <li>Always add NoOpAction as baseline
     * </ul>
     *
     * <p>Filters by constraint feasibility and sorts by recommendation score.
     *
     * @param caseId Case to recommend for
     * @param prediction Outcome prediction from predictor
     * @return Ranked list of feasible actions (never empty, always includes NoOpAction)
     * @throws PIException If recommendation generation fails
     */
    public List<ProcessAction> recommend(String caseId, CaseOutcomePrediction prediction)
            throws PIException {

        List<ProcessAction> candidates = new ArrayList<>();
        double riskScore = prediction.riskScore();

        candidates.add(new NoOpAction(
            caseId,
            "No intervention recommended - risk level acceptable",
            0.1
        ));

        if (riskScore >= 0.5) {
            candidates.add(new ReallocateResourceAction(
                caseId,
                "unspecified_task",
                "current_resource",
                "available_resource",
                "Reallocate to more capable resource",
                0.6
            ));
        }

        if (riskScore > 0.7) {
            candidates.add(new EscalateAction(
                caseId,
                "unspecified_task",
                "management_team",
                "Risk level high - escalate for immediate review",
                0.8
            ));
        }

        candidates.add(new RerouteAction(
            caseId,
            "current_task",
            "alternate_task",
            "Route through alternate path for better outcomes",
            0.7
        ));

        List<ProcessAction> feasibleActions = candidates.stream()
            .filter(constraintModel::isFeasible)
            .toList();

        if (feasibleActions.isEmpty()) {
            return List.of(new NoOpAction(
                caseId,
                "No feasible interventions - proceed with current path",
                0.1
            ));
        }

        return recommender.rank(feasibleActions, riskScore);
    }
}
