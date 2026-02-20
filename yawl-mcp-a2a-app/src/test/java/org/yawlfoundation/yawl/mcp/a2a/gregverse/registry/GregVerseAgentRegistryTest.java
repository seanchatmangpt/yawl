/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.registry;

import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseConfiguration.GregVerseAgentRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for GregVerseAgentRegistry covering registration, discovery, and
 * concurrent safety using real GregVerseAgent implementations (no mocks).
 *
 * <p>Chicago TDD: all collaborators are real. The registry holds real agents
 * built from a test-local lightweight implementation that does not call out
 * to external services.</p>
 */
class GregVerseAgentRegistryTest {

    /**
     * A concrete, minimal GregVerseAgent that is fully self-contained and
     * does not depend on ZaiService or any external service.
     */
    private static final class StubExpert implements GregVerseAgent {
        private final String id;
        private final List<String> skills;
        private final List<String> expertise;

        StubExpert(String id, List<String> skills, List<String> expertise) {
            this.id = id;
            this.skills = List.copyOf(skills);
            this.expertise = List.copyOf(expertise);
        }

        @Override public String getAgentId()          { return id; }
        @Override public String getDisplayName()      { return "Agent-" + id; }
        @Override public String getBio()              { return "Bio for " + id; }
        @Override public List<String> getSpecializedSkills() { return skills; }
        @Override public String getSystemPrompt()     { return "System prompt for " + id; }
        @Override public String getCommunicationStyle() { return "Direct"; }
        @Override public List<String> getExpertise()  { return expertise; }
        @Override public String getResponseFormat()   { return "Plain text"; }
        @Override public List<AgentSkill> createAgentSkills() { return List.of(); }
        @Override public AgentCard createAgentCard(int port, String basePath) {
            throw new UnsupportedOperationException("Not needed in unit tests");
        }
        @Override public String processQuery(String query)                    { return "Response to: " + query; }
        @Override public String processSkillQuery(String skillId, String query) { return skillId + ": " + query; }
        @Override public String provideAdvice(String topic, String context)   { return "Advice on " + topic; }
    }

    private GregVerseAgentRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GregVerseAgentRegistry();
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    @Test
    void registerAddsAgentAndMakesItRetrievable() {
        GregVerseAgent agent = new StubExpert("alpha", List.of("skill-a"), List.of("Strategy"));
        registry.register(agent);

        GregVerseAgent retrieved = registry.getAgent("alpha");
        assertNotNull(retrieved);
        assertEquals("alpha", retrieved.getAgentId());
    }

    @Test
    void registerOverwritesExistingAgentWithSameId() {
        registry.register(new StubExpert("x", List.of("s1"), List.of("E1")));
        registry.register(new StubExpert("x", List.of("s2"), List.of("E2")));

        assertEquals(1, registry.getAgentCount());
        assertEquals(List.of("s2"), registry.getAgent("x").getSpecializedSkills());
    }

    @Test
    void getAgentReturnsNullForUnknownId() {
        assertNull(registry.getAgent("nobody"));
    }

    @Test
    void getAgentCountReturnsZeroOnEmptyRegistry() {
        assertEquals(0, registry.getAgentCount());
    }

    @Test
    void getAgentCountReflectsMultipleRegistrations() {
        registry.register(new StubExpert("a", List.of(), List.of()));
        registry.register(new StubExpert("b", List.of(), List.of()));
        registry.register(new StubExpert("c", List.of(), List.of()));

        assertEquals(3, registry.getAgentCount());
    }

    // -------------------------------------------------------------------------
    // hasAgent
    // -------------------------------------------------------------------------

    @Test
    void hasAgentReturnsTrueAfterRegistration() {
        registry.register(new StubExpert("present", List.of(), List.of()));
        assertTrue(registry.hasAgent("present"));
    }

    @Test
    void hasAgentReturnsFalseForAbsentId() {
        assertFalse(registry.hasAgent("absent"));
    }

    // -------------------------------------------------------------------------
    // getAllAgents / getAllMetadata
    // -------------------------------------------------------------------------

    @Test
    void getAllAgentsReturnsImmutableSnapshot() {
        registry.register(new StubExpert("m1", List.of(), List.of()));
        registry.register(new StubExpert("m2", List.of(), List.of()));

        Map<String, GregVerseAgent> all = registry.getAllAgents();
        assertEquals(2, all.size());
        assertTrue(all.containsKey("m1"));
        assertTrue(all.containsKey("m2"));
    }

    @Test
    void getAllMetadataContainsMatchingMetadata() {
        registry.register(new StubExpert("meta-agent",
                List.of("coding", "design"),
                List.of("Architecture")));

        var metadata = registry.getAllMetadata();
        assertTrue(metadata.containsKey("meta-agent"));

        var m = metadata.get("meta-agent");
        assertEquals("meta-agent", m.agentId());
        assertEquals("Agent-meta-agent", m.displayName());
        assertEquals(List.of("coding", "design"), m.specializedSkills());
        assertEquals(List.of("Architecture"), m.expertise());
    }

    // -------------------------------------------------------------------------
    // findAgentsBySkill
    // -------------------------------------------------------------------------

    @Test
    void findAgentsBySkillReturnsOnlyMatchingAgents() {
        registry.register(new StubExpert("a1", List.of("seo", "copy"), List.of()));
        registry.register(new StubExpert("a2", List.of("copy", "email"), List.of()));
        registry.register(new StubExpert("a3", List.of("design"), List.of()));

        List<GregVerseAgent> seoAgents = registry.findAgentsBySkill("seo");
        assertEquals(1, seoAgents.size());
        assertEquals("a1", seoAgents.get(0).getAgentId());

        List<GregVerseAgent> copyAgents = registry.findAgentsBySkill("copy");
        assertEquals(2, copyAgents.size());
    }

    @Test
    void findAgentsBySkillReturnsEmptyListForUnknownSkill() {
        registry.register(new StubExpert("x", List.of("alpha"), List.of()));

        List<GregVerseAgent> result = registry.findAgentsBySkill("omega");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findAgentsBySkillReturnsEmptyListOnEmptyRegistry() {
        assertTrue(registry.findAgentsBySkill("any-skill").isEmpty());
    }

    // -------------------------------------------------------------------------
    // findAgentsByExpertise
    // -------------------------------------------------------------------------

    @Test
    void findAgentsByExpertiseCaseInsensitiveMatch() {
        registry.register(new StubExpert("e1", List.of(), List.of("Machine Learning")));
        registry.register(new StubExpert("e2", List.of(), List.of("Deep Learning")));
        registry.register(new StubExpert("e3", List.of(), List.of("Software Engineering")));

        List<GregVerseAgent> result = registry.findAgentsByExpertise("learning");
        assertEquals(2, result.size());
    }

    @Test
    void findAgentsByExpertiseReturnsEmptyListWhenNoMatch() {
        registry.register(new StubExpert("e1", List.of(), List.of("Python")));

        List<GregVerseAgent> result = registry.findAgentsByExpertise("Java");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // clear
    // -------------------------------------------------------------------------

    @Test
    void clearRemovesAllAgentsAndMetadata() {
        registry.register(new StubExpert("c1", List.of(), List.of()));
        registry.register(new StubExpert("c2", List.of(), List.of()));

        registry.clear();

        assertEquals(0, registry.getAgentCount());
        assertTrue(registry.getAllAgents().isEmpty());
        assertTrue(registry.getAllMetadata().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Concurrent registration safety
    // -------------------------------------------------------------------------

    @Test
    void concurrentRegistrationsAllSucceed() throws InterruptedException {
        int agentCount = 20;
        Thread[] threads = new Thread[agentCount];

        for (int i = 0; i < agentCount; i++) {
            final String agentId = "concurrent-agent-" + i;
            threads[i] = Thread.ofVirtual().unstarted(
                    () -> registry.register(new StubExpert(agentId, List.of(), List.of())));
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals(agentCount, registry.getAgentCount());
    }
}
