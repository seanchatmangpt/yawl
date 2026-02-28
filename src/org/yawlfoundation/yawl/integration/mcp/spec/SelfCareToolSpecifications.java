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

package org.yawlfoundation.yawl.integration.mcp.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.integration.selfcare.AutonomicSelfCareEngine;
import org.yawlfoundation.yawl.integration.selfcare.BehavioralActivationPlan;
import org.yawlfoundation.yawl.integration.selfcare.GregverseSearchClient;
import org.yawlfoundation.yawl.integration.selfcare.OTDomain;
import org.yawlfoundation.yawl.integration.selfcare.SelfCareAction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool specifications for autonomic self-care actions.
 *
 * <p>Exposes two tools via the YAWL MCP server:</p>
 * <ul>
 *   <li>{@code yawl_selfcare_search} — Search Gregverse for OT workflow specs by domain
 *       or keyword. Returns a ranked list of {@link GregverseSearchClient.WorkflowSpecSummary}
 *       records.</li>
 *   <li>{@code yawl_selfcare_recommend} — Generate a behavioral activation plan for a given
 *       OT domain. Returns the plan with {@code nextAction} highlighted as the immediate
 *       "act now" entry point.</li>
 * </ul>
 *
 * <p>Both tools degrade gracefully: if Gregverse is offline, built-in starter actions
 * ensure the recommend tool always returns a usable plan.</p>
 *
 * @since YAWL 6.0
 */
public final class SelfCareToolSpecifications {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GregverseSearchClient searchClient;
    private final AutonomicSelfCareEngine engine;

    /**
     * Constructs tool specifications wired to the given search client and engine.
     *
     * @param searchClient Gregverse search client
     * @param engine       autonomic self-care engine for plan generation
     */
    public SelfCareToolSpecifications(GregverseSearchClient searchClient,
                                       AutonomicSelfCareEngine engine) {
        this.searchClient = searchClient;
        this.engine = engine;
    }

    /**
     * Creates all self-care MCP tool specifications.
     *
     * @return list of tool specs ready to register with {@code YawlMcpServer}
     */
    public List<McpServerFeatures.SyncToolSpecification> createAll() {
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
        tools.add(createSearchTool());
        tools.add(createRecommendTool());
        return tools;
    }

    // ─── yawl_selfcare_search ─────────────────────────────────────────────────

    private McpServerFeatures.SyncToolSpecification createSearchTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("domain", Map.of(
            "type", "string",
            "description", "OT performance area to search: 'self-care' (ADLs), "
                + "'productivity' (work/household), or 'leisure' (rest/recreation). "
                + "Leave blank to search all domains.",
            "enum", List.of("self-care", "productivity", "leisure")));
        props.put("keywords", Map.of(
            "type", "array",
            "items", Map.of("type", "string"),
            "description", "Optional keyword filter (e.g. [\"medication\", \"morning routine\"]). "
                + "All keywords must appear in spec name or description."));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of(), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_selfcare_search")
                .description("Search the Gregverse federated registry for Occupational Therapy (OT) "
                    + "workflow specifications. Filter by OT performance area (self-care, productivity, "
                    + "leisure) and optional keywords. Returns specs ordered by usage count — most-proven "
                    + "workflows first. Use this to discover care workflow templates before recommending "
                    + "a behavioural activation plan.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String domainStr = params.containsKey("domain")
                        ? (String) params.get("domain") : null;

                    @SuppressWarnings("unchecked")
                    List<String> keywords = params.containsKey("keywords")
                        ? (List<String>) params.get("keywords") : List.of();

                    List<GregverseSearchClient.WorkflowSpecSummary> results;

                    if (!keywords.isEmpty()) {
                        results = searchClient.searchByKeywords(keywords.toArray(new String[0]));
                    } else if (domainStr != null && !domainStr.isBlank()) {
                        OTDomain domain = OTDomain.fromString(domainStr);
                        results = searchClient.searchByDomain(domain);
                    } else {
                        results = searchClient.searchOTWorkflows();
                    }

                    List<Map<String, Object>> entries = new ArrayList<>();
                    for (GregverseSearchClient.WorkflowSpecSummary spec : results) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("specId", spec.specId());
                        entry.put("specName", spec.specName());
                        entry.put("provider", spec.provider());
                        entry.put("domain", spec.domain().sparqlValue());
                        entry.put("description", spec.description());
                        entry.put("downloadCount", spec.downloadCount());
                        entries.add(entry);
                    }

                    String json = MAPPER.writeValueAsString(Map.of(
                        "results", entries,
                        "total", entries.size(),
                        "message", entries.isEmpty()
                            ? "No specs found in Gregverse for the given criteria. "
                                + "Use yawl_selfcare_recommend to get built-in starter actions."
                            : entries.size() + " spec(s) found in Gregverse."
                    ));

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(json)), false, null, null);

                } catch (IllegalArgumentException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Invalid input: " + e.getMessage())),
                        true, null, null);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Search failed: " + e.getMessage())),
                        true, null, null);
                }
            });
    }

    // ─── yawl_selfcare_recommend ──────────────────────────────────────────────

    private McpServerFeatures.SyncToolSpecification createRecommendTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("domain", Map.of(
            "type", "string",
            "description", "OT performance area: 'self-care', 'productivity', or 'leisure'",
            "enum", List.of("self-care", "productivity", "leisure")));
        props.put("count", Map.of(
            "type", "integer",
            "description", "Number of actions to include in the plan (default: 3, max: 10)",
            "minimum", 1,
            "maximum", 10));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("domain"), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_selfcare_recommend")
                .description("Generate a behavioral activation plan for a given Occupational Therapy "
                    + "performance area. Actions are ordered easiest-first. The 'nextAction' field "
                    + "is the immediate entry point — no deliberation required before starting. "
                    + "Principle: 'You can act your way into right action but you can't think your "
                    + "way into right actions.' Built-in starter actions are always available even "
                    + "when Gregverse is offline.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String domainStr = (String) params.get("domain");
                    int count = params.containsKey("count")
                        ? ((Number) params.get("count")).intValue()
                        : 3;

                    if (count < 1 || count > 10) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("count must be between 1 and 10")),
                            true, null, null);
                    }

                    OTDomain domain = OTDomain.fromString(domainStr);
                    BehavioralActivationPlan plan = engine.generatePlan(domain, count);

                    // Serialise plan
                    Map<String, Object> planMap = new LinkedHashMap<>();
                    planMap.put("planId", plan.planId());
                    planMap.put("domain", domain.sparqlValue());
                    planMap.put("generatedAt", plan.generatedAt().toString());
                    planMap.put("rationale", plan.rationale());
                    planMap.put("totalDurationMinutes", plan.totalDuration().toMinutes());

                    // nextAction — the immediate "act now" entry point
                    planMap.put("nextAction", serialiseAction(plan.nextAction()));

                    // All actions in order
                    List<Map<String, Object>> actionsList = new ArrayList<>();
                    for (SelfCareAction action : plan.actions()) {
                        actionsList.add(serialiseAction(action));
                    }
                    planMap.put("actions", actionsList);

                    String json = MAPPER.writeValueAsString(planMap);
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(json)), false, null, null);

                } catch (IllegalArgumentException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Invalid input: " + e.getMessage())),
                        true, null, null);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Recommend failed: " + e.getMessage())),
                        true, null, null);
                }
            });
    }

    // ─── Serialisation ────────────────────────────────────────────────────────

    private static Map<String, Object> serialiseAction(SelfCareAction action) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", action.id());
        map.put("title", action.title());
        map.put("description", action.description());
        map.put("estimatedMinutes", action.estimated().toMinutes() == 0
            ? 1L : action.estimated().toMinutes());
        map.put("domain", action.domain().sparqlValue());
        map.put("type", switch (action) {
            case SelfCareAction.DailyLivingAction a -> {
                map.put("specId", a.specId());
                yield "daily-living";
            }
            case SelfCareAction.PhysicalActivity a -> {
                map.put("intensityLevel", a.intensityLevel());
                yield "physical";
            }
            case SelfCareAction.CognitiveActivity a -> {
                map.put("cognitiveTarget", a.cognitiveTarget());
                yield "cognitive";
            }
            case SelfCareAction.SocialEngagement a -> {
                map.put("groupSize", a.groupSize());
                yield "social";
            }
        });
        return map;
    }
}
