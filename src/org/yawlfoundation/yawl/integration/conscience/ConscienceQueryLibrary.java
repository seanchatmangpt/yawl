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
     * <p>Usage: {@code String.format(EXPLAIN_ROUTING, "agent-1")}</p>
     *
     * <p>Returns all decision triples where {@code dec:agentId} matches the given value.
     * Useful for auditing agent behavior and decision rationales.</p>
     */
    public static final String EXPLAIN_ROUTING = """
        PREFIX dec: <http://yawlfoundation.org/yawl/conscience#>
        CONSTRUCT { ?d ?p ?o }
        WHERE { ?d a dec:AgentDecision ; dec:agentId ?aid ; ?p ?o .
                FILTER(?aid = "%s") }
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
        WHERE { ?d a dec:AgentDecision ; ?p ?o }
        ORDER BY DESC(?timestamp)
        LIMIT %d
        """;
}
