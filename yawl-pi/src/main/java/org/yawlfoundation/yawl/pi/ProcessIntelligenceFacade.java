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

package org.yawlfoundation.yawl.pi;

import org.yawlfoundation.yawl.pi.bridge.OcedBridge;
import org.yawlfoundation.yawl.pi.bridge.OcedBridgeFactory;
import org.yawlfoundation.yawl.pi.bridge.OcedSchema;
import org.yawlfoundation.yawl.pi.optimization.AssignmentProblem;
import org.yawlfoundation.yawl.pi.optimization.AssignmentSolution;
import org.yawlfoundation.yawl.pi.optimization.ResourceOptimizer;
import org.yawlfoundation.yawl.pi.predictive.CaseOutcomePredictor;
import org.yawlfoundation.yawl.pi.predictive.CaseOutcomePrediction;
import org.yawlfoundation.yawl.pi.prescriptive.PrescriptiveEngine;
import org.yawlfoundation.yawl.pi.prescriptive.ProcessAction;
import org.yawlfoundation.yawl.pi.rag.NaturalLanguageQueryEngine;
import org.yawlfoundation.yawl.pi.rag.NlQueryRequest;
import org.yawlfoundation.yawl.pi.rag.NlQueryResponse;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Unified entry point for all five Process Intelligence connections.
 *
 * <p>Coordinates across predictive, prescriptive, optimization, RAG (retrieval-augmented
 * generation), and data preparation systems. Provides thread-safe delegation to each engine.
 *
 * <p>The five connections are:
 * <ul>
 *   <li>Predictive: case outcome probability and risk scoring (ONNX + DNA oracle)</li>
 *   <li>Prescriptive: recommended interventions (reroute, escalate, reallocate)</li>
 *   <li>Optimization: resource assignment via Hungarian algorithm</li>
 *   <li>RAG: natural language query over process knowledge base</li>
 *   <li>Data Preparation: convert CSV/JSON/XML to OCEL2 format</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ProcessIntelligenceFacade {

    private final CaseOutcomePredictor predictor;
    private final PrescriptiveEngine prescriptive;
    private final ResourceOptimizer optimizer;
    private final NaturalLanguageQueryEngine nlEngine;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Construct ProcessIntelligenceFacade with all required engines.
     *
     * @param predictor case outcome prediction engine (required)
     * @param prescriptive prescriptive recommendation engine (required)
     * @param optimizer resource assignment optimizer (required)
     * @param nlEngine natural language query engine (required)
     * @throws IllegalArgumentException if any parameter is null
     */
    public ProcessIntelligenceFacade(
        CaseOutcomePredictor predictor,
        PrescriptiveEngine prescriptive,
        ResourceOptimizer optimizer,
        NaturalLanguageQueryEngine nlEngine) {

        if (predictor == null) {
            throw new IllegalArgumentException("predictor is required");
        }
        if (prescriptive == null) {
            throw new IllegalArgumentException("prescriptive is required");
        }
        if (optimizer == null) {
            throw new IllegalArgumentException("optimizer is required");
        }
        if (nlEngine == null) {
            throw new IllegalArgumentException("nlEngine is required");
        }

        this.predictor = predictor;
        this.prescriptive = prescriptive;
        this.optimizer = optimizer;
        this.nlEngine = nlEngine;
    }

    /**
     * Predict case outcome using the predictive engine.
     *
     * <p>Returns probability of successful completion and risk assessment.
     * Uses ONNX model if available, falls back to DNA oracle heuristics.
     *
     * @param caseId workflow case identifier (required)
     * @return case outcome prediction with completion probability and risk score
     * @throws PIException if prediction fails
     * @throws IllegalArgumentException if caseId is null or empty
     */
    public CaseOutcomePrediction predictOutcome(String caseId) throws PIException {
        if (caseId == null || caseId.isEmpty()) {
            throw new IllegalArgumentException("caseId is required");
        }

        lock.lock();
        try {
            return predictor.predict(caseId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Recommend actions to improve case outcome.
     *
     * <p>Given a case prediction, generates ranked list of feasible actions
     * that could improve outcomes (e.g., reroute, escalate, reallocate resources).
     *
     * @param caseId workflow case identifier (required)
     * @param prediction case outcome prediction (required)
     * @return ranked list of recommended actions (never empty, always includes NoOpAction)
     * @throws PIException if recommendation generation fails
     * @throws IllegalArgumentException if any parameter is null or empty
     */
    public List<ProcessAction> recommendActions(String caseId, CaseOutcomePrediction prediction)
            throws PIException {
        if (caseId == null || caseId.isEmpty()) {
            throw new IllegalArgumentException("caseId is required");
        }
        if (prediction == null) {
            throw new IllegalArgumentException("prediction is required");
        }

        lock.lock();
        try {
            return prescriptive.recommend(caseId, prediction);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Optimize resource assignments using the Hungarian algorithm.
     *
     * <p>Solves the assignment problem optimally: assigns work items to resources
     * to minimize total cost. Useful for load balancing and resource allocation.
     *
     * @param problem assignment problem definition (required)
     * @return optimal assignment with total cost and solve time
     * @throws PIException if optimization fails
     * @throws IllegalArgumentException if problem is null
     */
    public AssignmentSolution optimizeResources(AssignmentProblem problem) throws PIException {
        if (problem == null) {
            throw new IllegalArgumentException("problem is required");
        }

        lock.lock();
        try {
            return optimizer.solve(problem);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Answer a natural language query using RAG.
     *
     * <p>Retrieves relevant process facts from knowledge base and uses
     * large language model to generate grounded answer. Falls back to
     * raw facts if LLM is unavailable.
     *
     * @param request natural language query request (required)
     * @return natural language response with source facts and confidence
     * @throws PIException if query processing fails
     * @throws IllegalArgumentException if request is null
     */
    public NlQueryResponse ask(NlQueryRequest request) throws PIException {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }

        lock.lock();
        try {
            return nlEngine.query(request);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Prepare event data by converting to OCEL2 format.
     *
     * <p>Auto-detects format (CSV, JSON, XML) from content, infers schema,
     * and converts to standardized OCEL2 v2.0 JSON for process mining.
     *
     * @param rawData raw event data content (required)
     * @return OCEL2 v2.0 JSON string ready for process mining
     * @throws PIException if preparation fails
     * @throws IllegalArgumentException if rawData is null or empty
     */
    public String prepareEventData(String rawData) throws PIException {
        if (rawData == null || rawData.isEmpty()) {
            throw new IllegalArgumentException("rawData is required");
        }

        lock.lock();
        try {
            OcedBridge bridge = OcedBridgeFactory.autoDetect(rawData);
            OcedSchema schema = bridge.inferSchema(rawData);
            return bridge.convert(rawData, schema);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Prepare event data for a specific format.
     *
     * <p>Similar to prepareEventData, but allows explicit format specification.
     * Useful when auto-detection might be ambiguous.
     *
     * @param rawData raw event data content (required)
     * @param format format name: "csv", "json", or "xml" (required)
     * @return OCEL2 v2.0 JSON string
     * @throws PIException if preparation fails
     * @throws IllegalArgumentException if rawData or format is null/empty
     */
    public String prepareEventData(String rawData, String format) throws PIException {
        if (rawData == null || rawData.isEmpty()) {
            throw new IllegalArgumentException("rawData is required");
        }
        if (format == null || format.isEmpty()) {
            throw new IllegalArgumentException("format is required");
        }

        lock.lock();
        try {
            OcedBridge bridge = OcedBridgeFactory.forFormat(format);
            OcedSchema schema = bridge.inferSchema(rawData);
            return bridge.convert(rawData, schema);
        } finally {
            lock.unlock();
        }
    }
}
