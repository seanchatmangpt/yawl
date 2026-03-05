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

package org.yawlfoundation.yawl.integration.autonomous.marketplace;

/**
 * SPARQL 1.1 CONSTRUCT query constants and schema graph for the agent marketplace.
 *
 * <p>All instance queries run against a graph loaded via
 * {@link MarketplaceRdfExporter} using the {@code mkt:} vocabulary.
 * The schema constants ({@link #MARKETPLACE_SCHEMA_GRAPH}, {@link #MCP_TOOL_SCHEMA_CONSTRUCT},
 * {@link #A2A_SKILL_SCHEMA_CONSTRUCT}) describe the marketplace's operations as RDF and
 * generate MCP tool / A2A skill descriptors via CONSTRUCT.</p>
 *
 * @since YAWL 6.0
 */
public final class MarketplaceConstructQueries {

    private MarketplaceConstructQueries() {
        throw new UnsupportedOperationException("Constants class — do not instantiate");
    }

    // -----------------------------------------------------------------------
    // Common prefix block (prepend to any query that needs it)
    // -----------------------------------------------------------------------

    static final String PREFIXES =
            "PREFIX mkt: <http://yawlfoundation.org/yawl/marketplace#>\n" +
            "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
            "PREFIX ops: <http://yawlfoundation.org/yawl/marketplace/ops#>\n";

    // -----------------------------------------------------------------------
    // Instance queries — run against the exported marketplace graph
    // -----------------------------------------------------------------------

    /**
     * CONSTRUCT all live agent listings with their five-dimensional properties.
     *
     * <p>Load marketplace data first via {@link OxigraphSparqlEngine#loadTurtle(String)}
     * before executing this query.</p>
     */
    public static final String CONSTRUCT_ALL_LIVE_AGENTS =
            PREFIXES +
            "CONSTRUCT {\n" +
            "  ?listing a mkt:AgentListing ;\n" +
            "           mkt:agentId ?id ;\n" +
            "           mkt:agentName ?name ;\n" +
            "           mkt:isLive ?live ;\n" +
            "           mkt:specVersion ?ver ;\n" +
            "           mkt:domainName ?domain ;\n" +
            "           mkt:basePricePerCycle ?price ;\n" +
            "           mkt:llmInferenceRatio ?ratio .\n" +
            "}\n" +
            "WHERE {\n" +
            "  ?listing a mkt:AgentListing ;\n" +
            "           mkt:agentId ?id ;\n" +
            "           mkt:agentName ?name ;\n" +
            "           mkt:isLive ?live ;\n" +
            "           mkt:specVersion ?ver ;\n" +
            "           mkt:domainName ?domain ;\n" +
            "           mkt:basePricePerCycle ?price ;\n" +
            "           mkt:llmInferenceRatio ?ratio .\n" +
            "  FILTER(?live = \"true\"^^xsd:boolean)\n" +
            "}\n";

    /**
     * CONSTRUCT live agents that declare a specific RDF namespace.
     *
     * <p>Bind {@code ?ns} to the required namespace IRI before executing, e.g.:</p>
     * <pre>{@code
     * String q = MarketplaceConstructQueries.CONSTRUCT_AGENTS_BY_NAMESPACE
     *     .replace("?ns", "<http://www.yawlfoundation.org/yawlschema#>");
     * }</pre>
     */
    public static final String CONSTRUCT_AGENTS_BY_NAMESPACE =
            PREFIXES +
            "CONSTRUCT {\n" +
            "  ?listing a mkt:AgentListing ;\n" +
            "           mkt:agentId ?id ;\n" +
            "           mkt:agentName ?name ;\n" +
            "           mkt:declaredNamespace ?ns .\n" +
            "}\n" +
            "WHERE {\n" +
            "  ?listing a mkt:AgentListing ;\n" +
            "           mkt:agentId ?id ;\n" +
            "           mkt:agentName ?name ;\n" +
            "           mkt:isLive \"true\"^^xsd:boolean ;\n" +
            "           mkt:declaredNamespace ?ns .\n" +
            "}\n";

    /**
     * CONSTRUCT live agents satisfying a multi-dimensional slot query.
     *
     * <p>Filters by: required namespace, required WCP pattern, and maximum p99 latency.
     * Bind the three filter variables before use:</p>
     * <pre>{@code
     * String q = MarketplaceConstructQueries.CONSTRUCT_AGENTS_FOR_SLOT
     *     .replace("?requiredNs",       "\"http://www.yawlfoundation.org/yawlschema#\"")
     *     .replace("?requiredWcp",      "\"WCP-1\"")
     *     .replace("?maxP99",           "\"100\"^^xsd:long");
     * }</pre>
     */
    public static final String CONSTRUCT_AGENTS_FOR_SLOT =
            PREFIXES +
            "CONSTRUCT {\n" +
            "  ?listing a mkt:AgentListing ;\n" +
            "           mkt:agentId ?id ;\n" +
            "           mkt:agentName ?name ;\n" +
            "           mkt:domainName ?domain ;\n" +
            "           mkt:wcpPattern ?wcp ;\n" +
            "           mkt:p99LatencyMs ?p99 ;\n" +
            "           mkt:basePricePerCycle ?price .\n" +
            "}\n" +
            "WHERE {\n" +
            "  ?listing a mkt:AgentListing ;\n" +
            "           mkt:agentId ?id ;\n" +
            "           mkt:agentName ?name ;\n" +
            "           mkt:domainName ?domain ;\n" +
            "           mkt:isLive \"true\"^^xsd:boolean ;\n" +
            "           mkt:declaredNamespace ?requiredNs ;\n" +
            "           mkt:wcpPattern ?wcp ;\n" +
            "           mkt:p99LatencyMs ?p99 ;\n" +
            "           mkt:basePricePerCycle ?price .\n" +
            "  FILTER(?wcp = ?requiredWcp && ?p99 <= ?maxP99)\n" +
            "}\n";

    // -----------------------------------------------------------------------
    // Schema graph — static Turtle describing marketplace operations as RDF
    // -----------------------------------------------------------------------

    /**
     * Static Turtle string declaring the marketplace's five supported operations
     * as RDF using the {@code ops:} vocabulary.
     *
     * <p>Load this into a named graph (e.g. {@code mkt:schema}) and then run
     * {@link #MCP_TOOL_SCHEMA_CONSTRUCT} or {@link #A2A_SKILL_SCHEMA_CONSTRUCT}
     * against it to generate tool/skill descriptors.</p>
     */
    public static final String MARKETPLACE_SCHEMA_GRAPH =
            "@prefix ops: <http://yawlfoundation.org/yawl/marketplace/ops#> .\n" +
            "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
            "\n" +
            "ops:list_agents a ops:MarketplaceOperation ;\n" +
            "    ops:name \"marketplace_list_agents\" ;\n" +
            "    ops:description \"List all live agents in the marketplace\" ;\n" +
            "    ops:inputSchema \"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{}}\" ;\n" +
            "    ops:tag \"marketplace\" ; ops:tag \"agents\" ;\n" +
            "    ops:skillId \"marketplace-list-agents\" .\n" +
            "\n" +
            "ops:find_for_slot a ops:MarketplaceOperation ;\n" +
            "    ops:name \"marketplace_find_for_slot\" ;\n" +
            "    ops:description \"Find agents satisfying a multi-dimensional transition slot query\" ;\n" +
            "    ops:inputSchema \"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{" +
                "\\\"namespace\\\":{\\\"type\\\":\\\"string\\\"}," +
                "\\\"wcpPattern\\\":{\\\"type\\\":\\\"string\\\"}," +
                "\\\"maxP99Ms\\\":{\\\"type\\\":\\\"integer\\\"}," +
                "\\\"maxCost\\\":{\\\"type\\\":\\\"number\\\"}}}\" ;\n" +
            "    ops:tag \"marketplace\" ; ops:tag \"slot\" ; ops:tag \"wcp\" ;\n" +
            "    ops:skillId \"marketplace-find-for-slot\" .\n" +
            "\n" +
            "ops:find_by_namespace a ops:MarketplaceOperation ;\n" +
            "    ops:name \"marketplace_find_by_namespace\" ;\n" +
            "    ops:description \"Find agents that declare a specific RDF namespace\" ;\n" +
            "    ops:inputSchema \"{\\\"type\\\":\\\"object\\\",\\\"required\\\":[\\\"namespace\\\"]," +
                "\\\"properties\\\":{\\\"namespace\\\":{\\\"type\\\":\\\"string\\\"}}}\" ;\n" +
            "    ops:tag \"marketplace\" ; ops:tag \"namespace\" ; ops:tag \"ontology\" ;\n" +
            "    ops:skillId \"marketplace-find-by-namespace\" .\n" +
            "\n" +
            "ops:find_by_wcp a ops:MarketplaceOperation ;\n" +
            "    ops:name \"marketplace_find_by_wcp\" ;\n" +
            "    ops:description \"Find agents supporting a specific Workflow Control Pattern\" ;\n" +
            "    ops:inputSchema \"{\\\"type\\\":\\\"object\\\",\\\"required\\\":[\\\"wcpPattern\\\"]," +
                "\\\"properties\\\":{\\\"wcpPattern\\\":{\\\"type\\\":\\\"string\\\"}}}\" ;\n" +
            "    ops:tag \"marketplace\" ; ops:tag \"wcp\" ; ops:tag \"workflow\" ;\n" +
            "    ops:skillId \"marketplace-find-by-wcp\" .\n" +
            "\n" +
            "ops:heartbeat a ops:MarketplaceOperation ;\n" +
            "    ops:name \"marketplace_heartbeat\" ;\n" +
            "    ops:description \"Update an agent's heartbeat to mark it as live\" ;\n" +
            "    ops:inputSchema \"{\\\"type\\\":\\\"object\\\",\\\"required\\\":[\\\"agentId\\\"]," +
                "\\\"properties\\\":{\\\"agentId\\\":{\\\"type\\\":\\\"string\\\"}}}\" ;\n" +
            "    ops:tag \"marketplace\" ; ops:tag \"heartbeat\" ; ops:tag \"liveness\" ;\n" +
            "    ops:skillId \"marketplace-heartbeat\" .\n";

    // -----------------------------------------------------------------------
    // Schema-level CONSTRUCT queries — derive MCP tools and A2A skills from RDF
    // -----------------------------------------------------------------------

    /**
     * CONSTRUCT MCP tool descriptors from the {@link #MARKETPLACE_SCHEMA_GRAPH}.
     *
     * <p>Run against the schema graph loaded into the engine. Each result triple
     * encodes one tool: {@code ops:name}, {@code ops:description},
     * {@code ops:inputSchema}.</p>
     */
    public static final String MCP_TOOL_SCHEMA_CONSTRUCT =
            PREFIXES +
            "PREFIX ops: <http://yawlfoundation.org/yawl/marketplace/ops#>\n" +
            "CONSTRUCT {\n" +
            "  ?op ops:name ?name ;\n" +
            "      ops:description ?desc ;\n" +
            "      ops:inputSchema ?schema .\n" +
            "}\n" +
            "WHERE {\n" +
            "  ?op a ops:MarketplaceOperation ;\n" +
            "      ops:name ?name ;\n" +
            "      ops:description ?desc ;\n" +
            "      ops:inputSchema ?schema .\n" +
            "}\n";

    /**
     * CONSTRUCT A2A skill descriptors from the {@link #MARKETPLACE_SCHEMA_GRAPH}.
     *
     * <p>Each result triple encodes one skill: {@code ops:skillId}, {@code ops:name},
     * {@code ops:description}, and associated {@code ops:tag} values.</p>
     */
    public static final String A2A_SKILL_SCHEMA_CONSTRUCT =
            PREFIXES +
            "PREFIX ops: <http://yawlfoundation.org/yawl/marketplace/ops#>\n" +
            "CONSTRUCT {\n" +
            "  ?op ops:skillId ?skillId ;\n" +
            "      ops:name ?name ;\n" +
            "      ops:description ?desc ;\n" +
            "      ops:tag ?tag .\n" +
            "}\n" +
            "WHERE {\n" +
            "  ?op a ops:MarketplaceOperation ;\n" +
            "      ops:skillId ?skillId ;\n" +
            "      ops:name ?name ;\n" +
            "      ops:description ?desc ;\n" +
            "      ops:tag ?tag .\n" +
            "}\n";
}
