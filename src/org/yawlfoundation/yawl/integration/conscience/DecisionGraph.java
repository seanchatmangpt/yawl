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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.conscience;

import org.yawlfoundation.yawl.integration.autonomous.marketplace.OxigraphSparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;

import java.time.Instant;
import java.util.Objects;

/**
 * Agent Conscience Graph: persistent storage and recall of agent decisions.
 *
 * <p>Manages a SPARQL-queryable RDF store of {@link AgentDecision} records.
 * The graph enables autonomous agents to explain their decisions, audit decision patterns,
 * and recall similar past decisions for learning-informed future choices.</p>
 *
 * <p>The conscience graph is optional — all operations fail gracefully if the underlying
 * SPARQL engine is unavailable. The graph never raises exceptions on record operations,
 * only on explicit queries.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   OxigraphSparqlEngine engine = new OxigraphSparqlEngine();
 *   DecisionGraph conscience = new DecisionGraph(engine);
 *
 *   // Record a decision (always succeeds, even if engine is down)
 *   AgentDecision decision = AgentDecision.builder()
 *       .agentId("agent-1")
 *       .sessionId("session-xyz")
 *       .taskType("routing")
 *       .choiceKey("marketplace_find_for_slot")
 *       .rationale("Selected agent with lowest latency")
 *       .confidence(0.95)
 *       .withContext("selected_agent", "agent-42")
 *       .build();
 *
 *   conscience.record(decision);  // Silently skips if engine unavailable
 *
 *   // Query the graph
 *   if (conscience.isAvailable()) {
 *       String routingDecisions = conscience.recallSimilar("routing", 10);
 *       // Process Turtle RDF result...
 *   }
 * </pre>
 * </p>
 *
 * @since YAWL 6.0
 */
public final class DecisionGraph {

    private final OxigraphSparqlEngine engine;
    private final DecisionRdfSerializer serializer;

    /**
     * Creates a new DecisionGraph backed by the given SPARQL engine.
     *
     * @param engine the Oxigraph SPARQL engine (must not be null)
     */
    public DecisionGraph(OxigraphSparqlEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.serializer = new DecisionRdfSerializer();
    }

    /**
     * Record a decision to the conscience graph.
     *
     * <p>This operation is non-blocking and never throws. If the SPARQL engine
     * is unavailable, the decision is silently skipped.</p>
     *
     * @param decision the decision to record (must not be null)
     */
    public void record(AgentDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");

        if (!isAvailable()) {
            return;  // Engine unavailable; silently skip
        }

        try {
            String turtle = serializer.serialize(decision);
            engine.loadTurtle(turtle);
        } catch (SparqlEngineException e) {
            // Engine became unavailable during the operation; skip silently
        }
    }

    /**
     * Recall decisions of a given task type from the conscience graph.
     *
     * <p>Executes a SPARQL CONSTRUCT query and returns the result as Turtle RDF.
     * Results are unordered.</p>
     *
     * @param taskType the task type to filter by (e.g., "routing", "selection", "rejection")
     * @param limit    maximum number of results to return (unused in current query)
     * @return Turtle RDF string containing matching decision triples; never null
     * @throws SparqlEngineException if the SPARQL engine is unavailable or the query fails
     */
    public String recallSimilar(String taskType, int limit) throws SparqlEngineException {
        Objects.requireNonNull(taskType, "taskType must not be null");

        if (!isAvailable()) {
            throw new SparqlEngineException(
                "SPARQL engine is unavailable; cannot recall similar decisions"
            );
        }

        String query = String.format(ConscienceQueryLibrary.RECALL_SIMILAR_DECISIONS, taskType);
        return engine.constructToTurtle(query);
    }

    /**
     * Explain routing decisions made by a given agent.
     *
     * <p>Executes a SPARQL CONSTRUCT query filtering by agent ID and returns
     * the result as Turtle RDF.</p>
     *
     * @param agentId identifier of the agent to filter by
     * @param since   lower bound on decision timestamp (may be ignored by current query)
     * @return Turtle RDF string containing matching decision triples; never null
     * @throws SparqlEngineException if the SPARQL engine is unavailable or the query fails
     */
    public String explainRouting(String agentId, Instant since) throws SparqlEngineException {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(since, "since must not be null");

        if (!isAvailable()) {
            throw new SparqlEngineException(
                "SPARQL engine is unavailable; cannot explain routing"
            );
        }

        String query = String.format(ConscienceQueryLibrary.EXPLAIN_ROUTING, agentId);
        return engine.constructToTurtle(query);
    }

    /**
     * Retrieve all recent decisions from the conscience graph.
     *
     * <p>Executes a SPARQL CONSTRUCT query and returns the result as Turtle RDF.
     * Results are ordered by timestamp (newest first) and limited to the specified count.</p>
     *
     * @param limit maximum number of decisions to return
     * @return Turtle RDF string containing matching decision triples; never null
     * @throws SparqlEngineException if the SPARQL engine is unavailable or the query fails
     */
    public String allRecentDecisions(int limit) throws SparqlEngineException {
        if (!isAvailable()) {
            throw new SparqlEngineException(
                "SPARQL engine is unavailable; cannot retrieve recent decisions"
            );
        }

        String query = String.format(ConscienceQueryLibrary.ALL_RECENT_DECISIONS, limit);
        return engine.constructToTurtle(query);
    }

    /**
     * Returns true if the SPARQL engine is currently reachable.
     *
     * <p>This method never throws — it returns {@code false} on any failure.</p>
     *
     * @return true if the engine is available; false otherwise
     */
    public boolean isAvailable() {
        return engine.isAvailable();
    }
}
