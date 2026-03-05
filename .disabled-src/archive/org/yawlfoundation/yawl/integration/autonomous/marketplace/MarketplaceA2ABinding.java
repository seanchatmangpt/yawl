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

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentProvider;
import io.a2a.spec.AgentSkill;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exposes the agent marketplace as an A2A agent card with CONSTRUCT-derived skills.
 *
 * <p>When a {@link SparqlEngine} is provided and available, skills are derived by
 * loading the {@link MarketplaceConstructQueries#MARKETPLACE_SCHEMA_GRAPH} into the
 * engine and running {@link MarketplaceConstructQueries#A2A_SKILL_SCHEMA_CONSTRUCT}.
 * The Turtle result is parsed into {@link MarketplaceSkill} objects and then
 * converted to {@code io.a2a.spec.AgentSkill} instances for the card.</p>
 *
 * <p>Without an engine (or if the engine is unavailable), the same five skills are
 * returned from Java constants — identical to the CONSTRUCT output.</p>
 *
 * @since YAWL 6.0
 */
public final class MarketplaceA2ABinding {

    static final String AGENT_NAME    = "YAWL Agent Marketplace";
    static final String AGENT_VERSION = "6.0.0";
    static final String AGENT_DESC    =
            "YAWL five-dimensional agent marketplace. Discover, query, and register "
            + "CONSTRUCT-native agents by ontological coverage, workflow transition "
            + "compatibility, economic cost, and latency profile.";

    // Matches: ops:skillId, ops:name, ops:description, ops:tag with literal value
    private static final Pattern PRED_LITERAL = Pattern.compile(
            "(?:<[^>]+/ops#|ops:)(\\w+)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern SUBJECT_IRI = Pattern.compile(
            "^\\s*<([^>]+)>\\s*(?:a\\s+\\S+\\s*[;.])?", Pattern.MULTILINE);

    private final AgentMarketplace marketplace;
    private final SparqlEngine engine; // may be null

    /**
     * Creates a binding with an optional SPARQL engine.
     *
     * @param marketplace the marketplace to expose; must not be null
     * @param engine      a live SPARQL engine, or null to use Java-constant fallback
     */
    public MarketplaceA2ABinding(AgentMarketplace marketplace, SparqlEngine engine) {
        this.marketplace = Objects.requireNonNull(marketplace, "marketplace must not be null");
        this.engine = engine;
    }

    /**
     * Returns A2A skills for all five marketplace operations.
     *
     * <p>If an engine is present and available, skills are derived by SPARQL CONSTRUCT
     * against the schema graph. Otherwise the Java-constant fallback is used.</p>
     *
     * @return unmodifiable list of five {@link MarketplaceSkill}s; never null
     */
    public List<MarketplaceSkill> getSkills() {
        if (engine != null && engine.isAvailable()) {
            try {
                return buildSkillsViaConstruct();
            } catch (SparqlEngineException ignored) {
                // Fall through to static fallback
            }
        }
        return staticSkills();
    }

    /**
     * Build a complete A2A {@link AgentCard} for the marketplace agent.
     *
     * <p>Skills are derived via {@link #getSkills()} (CONSTRUCT or static fallback)
     * and converted to {@code io.a2a.spec.AgentSkill} instances.</p>
     *
     * @return a fully-populated {@link AgentCard}; never null
     */
    public AgentCard buildAgentCard() {
        List<AgentSkill> a2aSkills = getSkills().stream()
                .map(s -> AgentSkill.builder()
                        .id(s.id())
                        .name(s.name())
                        .description(s.description())
                        .tags(s.tags())
                        .inputModes(List.of("text"))
                        .outputModes(List.of("text"))
                        .build())
                .toList();

        return AgentCard.builder()
                .name(AGENT_NAME)
                .description(AGENT_DESC)
                .version(AGENT_VERSION)
                .provider(new AgentProvider("YAWL Foundation", "https://yawlfoundation.github.io"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(a2aSkills)
                .supportedInterfaces(List.of())
                .build();
    }

    // -----------------------------------------------------------------------
    // Internal — CONSTRUCT-based skill derivation
    // -----------------------------------------------------------------------

    private List<MarketplaceSkill> buildSkillsViaConstruct() throws SparqlEngineException {
        if (engine instanceof OxigraphSparqlEngine oxEngine) {
            oxEngine.loadTurtle(MarketplaceConstructQueries.MARKETPLACE_SCHEMA_GRAPH);
        }
        String result = engine.constructToTurtle(
                MarketplaceConstructQueries.A2A_SKILL_SCHEMA_CONSTRUCT);
        List<MarketplaceSkill> skills = parseSkillsFromTurtle(result);
        if (skills.isEmpty()) {
            throw new SparqlEngineException(
                    "A2A_SKILL_SCHEMA_CONSTRUCT returned no results; schema graph may not be loaded");
        }
        return skills;
    }

    /**
     * Parse Turtle output from {@link MarketplaceConstructQueries#A2A_SKILL_SCHEMA_CONSTRUCT}
     * into {@link MarketplaceSkill} objects.
     *
     * <p>Extracts {@code ops:skillId}, {@code ops:name}, {@code ops:description},
     * and all {@code ops:tag} values, grouped by subject IRI.</p>
     */
    static List<MarketplaceSkill> parseSkillsFromTurtle(String turtle) {
        Map<String, Map<String, Object>> subjects = new LinkedHashMap<>();
        String currentSubject = null;

        for (String line : turtle.split("\n")) {
            Matcher subjMatcher = SUBJECT_IRI.matcher(line);
            if (subjMatcher.find() && line.stripLeading().startsWith("<")) {
                currentSubject = subjMatcher.group(1);
                subjects.computeIfAbsent(currentSubject, k -> new LinkedHashMap<>());
            }
            if (currentSubject == null) continue;

            Matcher pred = PRED_LITERAL.matcher(line);
            while (pred.find()) {
                String predName = pred.group(1);
                String value = unescape(pred.group(2));
                Map<String, Object> props = subjects.get(currentSubject);
                if ("tag".equals(predName)) {
                    @SuppressWarnings("unchecked")
                    List<String> tags = (List<String>) props.computeIfAbsent("tag",
                            k -> new ArrayList<String>());
                    tags.add(value);
                } else {
                    props.put(predName, value);
                }
            }
        }

        List<MarketplaceSkill> skills = new ArrayList<>();
        for (Map<String, Object> props : subjects.values()) {
            String skillId = (String) props.get("skillId");
            String name    = (String) props.get("name");
            String desc    = (String) props.get("description");
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) props.getOrDefault("tag", List.of());
            if (skillId != null && name != null && desc != null) {
                skills.add(new MarketplaceSkill(skillId, name, desc, tags));
            }
        }
        return Collections.unmodifiableList(skills);
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\\", "\\");
    }

    // -----------------------------------------------------------------------
    // Static fallback — same 5 skills, always available
    // -----------------------------------------------------------------------

    static List<MarketplaceSkill> staticSkills() {
        return List.of(
            new MarketplaceSkill("marketplace-list-agents",
                "List Marketplace Agents",
                "List all live agents in the marketplace",
                List.of("marketplace", "agents")),
            new MarketplaceSkill("marketplace-find-for-slot",
                "Find Agents For Slot",
                "Find agents satisfying a multi-dimensional transition slot query",
                List.of("marketplace", "slot", "wcp")),
            new MarketplaceSkill("marketplace-find-by-namespace",
                "Find Agents By Namespace",
                "Find agents that declare a specific RDF namespace",
                List.of("marketplace", "namespace", "ontology")),
            new MarketplaceSkill("marketplace-find-by-wcp",
                "Find Agents By WCP",
                "Find agents supporting a specific Workflow Control Pattern",
                List.of("marketplace", "wcp", "workflow")),
            new MarketplaceSkill("marketplace-heartbeat",
                "Marketplace Heartbeat",
                "Update an agent's heartbeat to mark it as live",
                List.of("marketplace", "heartbeat", "liveness"))
        );
    }
}
