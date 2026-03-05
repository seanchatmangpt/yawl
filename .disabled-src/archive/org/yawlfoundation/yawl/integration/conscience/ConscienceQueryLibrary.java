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

/**
 * Library of SPARQL 1.1 CONSTRUCT query templates for querying the Agent Conscience.
 *
 * <p>Each query is a string template that can be instantiated via {@link String#format(String, Object...)}
 * with placeholder values (e.g., task type, agent ID, timestamp).</p>
 *
 * <p>All queries target the {@code dec:} vocabulary at
 * {@code <http://yawlfoundation.org/yawl/conscience#>}.</p>
 *
 * @since YAWL 6.0
 */
public final class ConscienceQueryLibrary {

    private ConscienceQueryLibrary() {
        throw new UnsupportedOperationException("ConscienceQueryLibrary is a utility class");
    }

    /**
     * CONSTRUCT query: recall all decisions of a given task type.
     *
     * <p>Usage: {@code String.format(RECALL_SIMILAR_DECISIONS, "routing")}</p>
     *
     * <p>Returns all decision triples where {@code dec:taskType} matches the given value.
     * Results are unordered.</p>
     */
    public static final String RECALL_SIMILAR_DECISIONS = """
        PREFIX dec: <http://yawlfoundation.org/yawl/conscience#>
        CONSTRUCT { ?d ?p ?o }
        WHERE { ?d a dec:AgentDecision ; dec:taskType ?t ; ?p ?o .
                FILTER(?t = "%s") }
        """;

    /**
     * CONSTRUCT query: explain routing decisions by agent.
     *
     * <p>Usage: {@code String.format(EXPLAIN_ROUTING, "agent-1", "2024-02-23T14:32:15Z")}</p>
     *
     * <p>Returns all decision triples where {@code dec:agentId} matches the given value
     * and {@code dec:timestamp} is at or after the specified lower bound.
     * Useful for auditing agent behavior and decision rationales.</p>
     */
    public static final String EXPLAIN_ROUTING = """
        PREFIX dec: <http://yawlfoundation.org/yawl/conscience#>
        CONSTRUCT { ?d ?p ?o }
        WHERE { ?d a dec:AgentDecision ; dec:agentId ?aid ; dec:timestamp ?ts ; ?p ?o .
                FILTER(?aid = "%s")
                FILTER(?ts >= "%s") }
        """;

    /**
     * CONSTRUCT query: retrieve all decisions of a specific task type.
     *
     * <p>Usage: {@code String.format(DECISIONS_BY_TASK, "selection")}</p>
     *
     * <p>Returns all decision triples where {@code dec:taskType} matches.
     * Equivalent to {@link #RECALL_SIMILAR_DECISIONS} for clarity.</p>
     */
    public static final String DECISIONS_BY_TASK = """
        PREFIX dec: <http://yawlfoundation.org/yawl/conscience#>
        CONSTRUCT { ?d ?p ?o }
        WHERE { ?d a dec:AgentDecision ; dec:taskType ?t ; ?p ?o .
                FILTER(?t = "%s") }
        """;

    /**
     * CONSTRUCT query: retrieve all recent decisions (up to 50 most recent).
     *
     * <p>Usage: {@code String.format(ALL_RECENT_DECISIONS, 50)}</p>
     *
     * <p>Returns all decision triples, ordered by {@code dec:timestamp} in descending order
     * (newest first), limited to the specified count.</p>
     */
    public static final String ALL_RECENT_DECISIONS = """
        PREFIX dec: <http://yawlfoundation.org/yawl/conscience#>
        CONSTRUCT { ?d ?p ?o }
        WHERE { ?d a dec:AgentDecision ; dec:timestamp ?timestamp ; ?p ?o }
        ORDER BY DESC(?timestamp)
        LIMIT %d
        """;

    /**
     * SELECT query: decision frequency by agent.
     *
     * <p>Returns agent IDs with their decision counts, ordered by most active first.</p>
     */
    public static final String DECISION_FREQUENCY_BY_AGENT = """
        PREFIX dec: <http://yawlfoundation.org/yawl/conscience#>
        SELECT ?agentId (COUNT(?d) AS ?count)
        WHERE { ?d a dec:AgentDecision ; dec:agentId ?agentId }
        GROUP BY ?agentId
        ORDER BY DESC(?count)
        """;

    /**
     * SELECT query: confidence distribution across all decisions.
     *
     * <p>Returns decisions grouped into confidence buckets:
     * low (0-0.25), medium-low (0.25-0.5), medium-high (0.5-0.75), high (0.75-1.0).</p>
     */
    public static final String CONFIDENCE_DISTRIBUTION = """
        PREFIX dec: <http://yawlfoundation.org/yawl/conscience#>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
        SELECT ?bucket (COUNT(?d) AS ?count)
        WHERE {
            ?d a dec:AgentDecision ; dec:confidence ?conf .
            BIND(
                IF(?conf < 0.25, "low",
                IF(?conf < 0.5, "medium-low",
                IF(?conf < 0.75, "medium-high", "high")))
                AS ?bucket)
        }
        GROUP BY ?bucket
        ORDER BY ?bucket
        """;

    /**
     * SELECT query: decisions below a confidence threshold.
     *
     * <p>Usage: {@code String.format(LOW_CONFIDENCE_DECISIONS, 0.5)}</p>
     *
     * <p>Returns agent ID, task type, choice key, rationale, and confidence
     * for all decisions below the specified threshold.</p>
     */
    public static final String LOW_CONFIDENCE_DECISIONS = """
        PREFIX dec: <http://yawlfoundation.org/yawl/conscience#>
        SELECT ?agentId ?taskType ?choiceKey ?rationale ?confidence
        WHERE {
            ?d a dec:AgentDecision ;
               dec:agentId ?agentId ;
               dec:taskType ?taskType ;
               dec:choiceKey ?choiceKey ;
               dec:rationale ?rationale ;
               dec:confidence ?confidence .
            FILTER(?confidence < %f)
        }
        ORDER BY ASC(?confidence)
        """;
}
