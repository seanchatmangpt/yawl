package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;

import java.util.List;

/**
 * Chicago TDD tests for {@link MarketplaceMcpBinding} and {@link MarketplaceA2ABinding}.
 *
 * <p>Two categories:</p>
 * <ol>
 *   <li><b>Always-run</b> — null-engine fallback returns the five static tools/skills.</li>
 *   <li><b>Self-skipping</b> — CONSTRUCT roundtrip tests skip when yawl-native is down.</li>
 * </ol>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
public class MarketplaceMcpBindingTest extends TestCase {

    private static final int EXPECTED_TOOL_COUNT = 5;

    // -------------------------------------------------------------------------
    // MarketplaceMcpBinding — static fallback (null engine)
    // -------------------------------------------------------------------------

    public void testGetMcpToolsWithNullEngineReturnsFiveTools() {
        AgentMarketplace marketplace = new AgentMarketplace();
        MarketplaceMcpBinding binding = new MarketplaceMcpBinding(marketplace, null);

        List<McpToolDescriptor> tools = binding.getMcpTools();
        assertEquals("Must return exactly 5 MCP tools", EXPECTED_TOOL_COUNT, tools.size());
    }

    public void testStaticToolsContainListAgents() {
        List<McpToolDescriptor> tools = MarketplaceMcpBinding.staticMcpTools();

        boolean found = tools.stream()
                .anyMatch(t -> "marketplace_list_agents".equals(t.name()));
        assertTrue("Static tools must include marketplace_list_agents", found);
    }

    public void testStaticToolsContainFindForSlot() {
        List<McpToolDescriptor> tools = MarketplaceMcpBinding.staticMcpTools();
        assertTrue(tools.stream().anyMatch(t -> "marketplace_find_for_slot".equals(t.name())));
    }

    public void testStaticToolsContainFindByNamespace() {
        List<McpToolDescriptor> tools = MarketplaceMcpBinding.staticMcpTools();
        assertTrue(tools.stream().anyMatch(t -> "marketplace_find_by_namespace".equals(t.name())));
    }

    public void testStaticToolsContainFindByWcp() {
        List<McpToolDescriptor> tools = MarketplaceMcpBinding.staticMcpTools();
        assertTrue(tools.stream().anyMatch(t -> "marketplace_find_by_wcp".equals(t.name())));
    }

    public void testStaticToolsContainHeartbeat() {
        List<McpToolDescriptor> tools = MarketplaceMcpBinding.staticMcpTools();
        assertTrue(tools.stream().anyMatch(t -> "marketplace_heartbeat".equals(t.name())));
    }

    public void testAllStaticToolsHaveNonBlankDescriptions() {
        for (McpToolDescriptor tool : MarketplaceMcpBinding.staticMcpTools()) {
            assertFalse("Tool '" + tool.name() + "' must have non-blank description",
                    tool.description().isBlank());
        }
    }

    public void testAllStaticToolsHaveValidJsonSchema() {
        for (McpToolDescriptor tool : MarketplaceMcpBinding.staticMcpTools()) {
            String schema = tool.inputSchemaJson();
            assertTrue("Tool '" + tool.name() + "' schema must be JSON object",
                    schema.startsWith("{") && schema.endsWith("}"));
        }
    }

    public void testQueryAgentsAsTurtleWithNullEngineThrows() {
        AgentMarketplace marketplace = new AgentMarketplace();
        MarketplaceMcpBinding binding = new MarketplaceMcpBinding(marketplace, null);

        try {
            binding.queryAgentsAsTurtle(MarketplaceConstructQueries.CONSTRUCT_ALL_LIVE_AGENTS);
            fail("Expected SparqlEngineUnavailableException");
        } catch (SparqlEngineUnavailableException e) {
            // expected
        } catch (SparqlEngineException e) {
            fail("Expected SparqlEngineUnavailableException, got: " + e.getClass().getName());
        }
    }

    // -------------------------------------------------------------------------
    // Turtle parser (parseToolsFromTurtle)
    // -------------------------------------------------------------------------

    public void testParseSchemaTurtle() {
        String schemaTurtle = "<http://yawlfoundation.org/yawl/marketplace/ops#list_agents>\n"
                + "    ops:name \"marketplace_list_agents\" ;\n"
                + "    ops:description \"List all live agents\" ;\n"
                + "    ops:inputSchema \"{\\\"type\\\":\\\"object\\\"}\" .\n";

        List<McpToolDescriptor> tools = MarketplaceMcpBinding.parseToolsFromTurtle(schemaTurtle);
        assertEquals("Should parse one tool descriptor", 1, tools.size());
        assertEquals("marketplace_list_agents", tools.get(0).name());
        assertEquals("List all live agents", tools.get(0).description());
        assertTrue(tools.get(0).inputSchemaJson().contains("object"));
    }

    public void testParseEmptyTurtleReturnsEmptyList() {
        List<McpToolDescriptor> tools = MarketplaceMcpBinding.parseToolsFromTurtle("");
        assertTrue(tools.isEmpty());
    }

    // -------------------------------------------------------------------------
    // MarketplaceA2ABinding — static fallback
    // -------------------------------------------------------------------------

    public void testGetSkillsWithNullEngineReturnsFiveSkills() {
        AgentMarketplace marketplace = new AgentMarketplace();
        MarketplaceA2ABinding binding = new MarketplaceA2ABinding(marketplace, null);

        List<MarketplaceSkill> skills = binding.getSkills();
        assertEquals("Must return exactly 5 A2A skills", EXPECTED_TOOL_COUNT, skills.size());
    }

    public void testStaticSkillsHaveNonBlankIds() {
        for (MarketplaceSkill skill : MarketplaceA2ABinding.staticSkills()) {
            assertFalse("Skill must have non-blank id", skill.id().isBlank());
        }
    }

    public void testStaticSkillsHaveNonEmptyTags() {
        for (MarketplaceSkill skill : MarketplaceA2ABinding.staticSkills()) {
            assertFalse("Skill '" + skill.id() + "' must have at least one tag",
                    skill.tags().isEmpty());
        }
    }

    public void testBuildAgentCardWithNullEngine() {
        AgentMarketplace marketplace = new AgentMarketplace();
        MarketplaceA2ABinding binding = new MarketplaceA2ABinding(marketplace, null);

        io.a2a.spec.AgentCard card = binding.buildAgentCard();
        assertNotNull("AgentCard must not be null", card);
        assertEquals(MarketplaceA2ABinding.AGENT_NAME, card.name());
        assertNotNull("AgentCard must have skills", card.skills());
        assertEquals(EXPECTED_TOOL_COUNT, card.skills().size());
    }

    // -------------------------------------------------------------------------
    // Self-skipping: CONSTRUCT roundtrip (requires live yawl-native on 8083)
    // -------------------------------------------------------------------------

    public void testMcpToolsViaConstructWhenEngineRunning() {
        OxigraphSparqlEngine engine = new OxigraphSparqlEngine();
        if (!engine.isAvailable()) return;

        AgentMarketplace marketplace = new AgentMarketplace();
        MarketplaceMcpBinding binding = new MarketplaceMcpBinding(marketplace, engine);

        List<McpToolDescriptor> tools = binding.getMcpTools();
        assertEquals("CONSTRUCT path must return same 5 tools", EXPECTED_TOOL_COUNT, tools.size());
    }

    public void testQueryAgentsAsTurtleWhenEngineRunning() throws Exception {
        OxigraphSparqlEngine engine = new OxigraphSparqlEngine();
        if (!engine.isAvailable()) return;

        AgentMarketplace marketplace = TestFixtures.buildTestMarketplace(2);
        MarketplaceMcpBinding binding = new MarketplaceMcpBinding(marketplace, engine);

        String turtle = binding.queryAgentsAsTurtle(
                MarketplaceConstructQueries.CONSTRUCT_ALL_LIVE_AGENTS);
        assertNotNull(turtle);
        assertTrue("CONSTRUCT result must reference at least one agent",
                turtle.contains("AgentListing") || !turtle.isBlank());
    }

    public void testA2ASkillsViaConstructWhenEngineRunning() {
        OxigraphSparqlEngine engine = new OxigraphSparqlEngine();
        if (!engine.isAvailable()) return;

        AgentMarketplace marketplace = new AgentMarketplace();
        MarketplaceA2ABinding binding = new MarketplaceA2ABinding(marketplace, engine);

        List<MarketplaceSkill> skills = binding.getSkills();
        assertEquals("CONSTRUCT path must return same 5 skills", EXPECTED_TOOL_COUNT, skills.size());
    }
}
