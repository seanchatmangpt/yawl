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

package org.yawlfoundation.yawl.mcp.a2a.gregverse;

import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Base test class for GregVerse agent functionality.
 *
 * <p>This class provides common test patterns for all GregVerse agent implementations,
 * testing the core functionality defined in AbstractGregVerseAgent.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@ExtendWith(MockitoExtension.class)
public abstract class AbstractGregVerseAgentTest {

    protected static final String TEST_API_KEY = "test-api-key-12345";
    protected static final String TEST_AGENT_ID = "test-agent";
    protected static final String TEST_DISPLAY_NAME = "Test Agent";
    protected static final String TEST_BIO = "A test agent for unit testing";
    protected static final List<String> TEST_SKILLS = List.of("test-skill", "another-skill");

    @Mock
    protected ZaiService mockZaiService;

    protected GregVerseAgent agent;

    /**
     * Sets up the test agent before each test.
     * Subclasses should override this method to create their specific agent.
     */
    protected abstract GregVerseAgent createAgent();

    @BeforeEach
    void setUp() {
        agent = createAgent();
    }

    @Test
    @DisplayName("Agent should be created with API key")
    void agentCreationWithApiKey() {
        assertThat(agent).isNotNull();
        assertThat(agent.getAgentId()).isEqualTo(TEST_AGENT_ID);
        assertThat(agent.getDisplayName()).isEqualTo(TEST_DISPLAY_NAME);
        assertThat(agent.getBio()).isEqualTo(TEST_BIO);
    }

    @Test
    @DisplayName("Agent should throw exception for null API key")
    void agentCreationWithNullApiKeyThrowsException() {
        assertThatThrownBy(() -> createTestAgent(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API key cannot be null or empty");
    }

    @Test
    @DisplayName("Agent should throw exception for empty API key")
    void agentCreationWithEmptyApiKeyThrowsException() {
        assertThatThrownBy(() -> createTestAgent(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API key cannot be null or empty");
    }

    @Test
    @DisplayName("Agent should create valid agent card")
    void createAgentCard() {
        AgentCard card = agent.createAgentCard(8080, "/api");

        assertThat(card).isNotNull();
        assertThat(card.name()).isEqualTo(TEST_AGENT_ID);
        assertThat(card.description()).isEqualTo(TEST_BIO);
        assertThat(card.version()).isEqualTo("6.0.0");
        assertThat(card.capabilities().streaming()).isTrue();
        assertThat(card.capabilities().pushNotifications()).isFalse();
        assertThat(card.skills()).hasSize(TEST_SKILLS.size());
        assertThat(card.supportedInterfaces()).hasSize(1);
        assertThat(card.supportedInterfaces().get(0)).isNotNull();
    }

    @Test
    @DisplayName("Agent should have specialized skills")
    void getSpecializedSkills() {
        List<String> skills = agent.getSpecializedSkills();

        assertThat(skills).isNotNull();
        assertThat(skills).isNotEmpty();
        assertThat(skills).containsAll(TEST_SKILLS);
    }

    @Test
    @DisplayName("Agent should process query with ZaiService")
    void processQuery() {
        String testQuery = "What is your analysis of this business idea?";
        String expectedResponse = "As a test agent, I think this idea has potential.";

        when(mockZaiService.chat(testQuery)).thenReturn(expectedResponse);

        String response = agent.processQuery(testQuery);

        assertThat(response).isEqualTo(expectedResponse);
        verify(mockZaiService).chat(testQuery);
        verify(mockZaiService).setSystemPrompt(agent.getSystemPrompt());
    }

    @Test
    @DisplayName("Agent should process skill query with context")
    void processSkillQuery() {
        String skillId = "market-analysis";
        String testQuery = "Analyze this market opportunity";
        String expectedResponse = "Market analysis shows strong growth potential.";

        when(mockZaiService.chat(any(String.class))).thenReturn(expectedResponse);

        String response = agent.processSkillQuery(skillId, testQuery);

        assertThat(response).isEqualTo(expectedResponse);
        verify(mockZaiService).chat(
            String.format("Using your expertise in %s, please advise on the following:\n\n%s",
                skillId, testQuery)
        );
    }

    @Test
    @DisplayName("Agent should provide advice with context")
    void provideAdvice() {
        String topic = "business strategy";
        String context = "We're building a SaaS platform";
        String expectedResponse = "My advice on business strategy for your SaaS platform...";

        when(mockZaiService.chat(any(String.class))).thenReturn(expectedResponse);

        String response = agent.provideAdvice(topic, context);

        assertThat(response).isEqualTo(expectedResponse);
        verify(mockZaiService).chat(
            String.format("As %s, provide your expert advice on the following topic.\n\n" +
                         "Topic: %s\n\n" +
                         "Context: %s\n\n" +
                         "Format your response in your signature style: %s\n" +
                         "Response format: %s",
                agent.getDisplayName(), topic, context, agent.getCommunicationStyle(), agent.getResponseFormat())
        );
    }

    @Test
    @DisplayName("Agent should clear conversation history")
    void clearHistory() {
        agent.clearHistory();

        verify(mockZaiService).clearHistory();
    }

    @Test
    @DisplayName("Agent should have communication style")
    void getCommunicationStyle() {
        String style = agent.getCommunicationStyle();

        assertThat(style).isNotNull();
        assertThat(style).isNotBlank();
    }

    @Test
    @DisplayName("Agent should have response format")
    void getResponseFormat() {
        String format = agent.getResponseFormat();

        assertThat(format).isNotNull();
        assertThat(format).isNotBlank();
    }

    @Test
    @DisplayName("Agent should return expertise areas")
    void getExpertise() {
        List<String> expertise = agent.getExpertise();

        assertThat(expertise).isNotNull();
        assertThat(expertise).isNotEmpty();
    }

    @Test
    @DisplayName("Agent should create agent skills")
    void createAgentSkills() {
        List<AgentSkill> skills = agent.createAgentSkills();

        assertThat(skills).isNotNull();
        assertThat(skills).isNotEmpty();
        assertThat(skills).hasSize(TEST_SKILLS.size());

        // Verify each skill has proper structure
        skills.forEach(skill -> {
            assertThat(skill.name()).isNotEmpty();
            assertThat(skill.description()).isNotEmpty();
            // Tags may be empty but not null
            assertThat(skill.tags()).isNotNull();
        });
    }

    /**
     * Helper method to create a test agent with specific API key.
     */
    protected GregVerseAgent createTestAgent(String apiKey) {
        return new TestGregVerseAgent(apiKey);
    }

    /**
     * Implementation of GregVerseAgent for testing purposes.
     */
    private static class TestGregVerseAgent extends AbstractGregVerseAgent {

        public TestGregVerseAgent(String apiKey) {
            super(apiKey);
        }

        @Override
        public String getAgentId() {
            return TEST_AGENT_ID;
        }

        @Override
        public String getDisplayName() {
            return TEST_DISPLAY_NAME;
        }

        @Override
        public String getBio() {
            return TEST_BIO;
        }

        @Override
        public String getSystemPrompt() {
            return "You are a test agent for unit testing.";
        }

        @Override
        public List<String> getSpecializedSkills() {
            return TEST_SKILLS;
        }

        @Override
        public List<String> getExpertise() {
            return List.of("business", "strategy", "testing");
        }

        @Override
        public String getCommunicationStyle() {
            return "Professional and analytical";
        }

        @Override
        public String getResponseFormat() {
            return "Structured response with bullet points";
        }

        @Override
        public List<AgentSkill> createAgentSkills() {
            return TEST_SKILLS.stream()
                .map(skill -> AgentSkill.builder()
                    .name(skill)
                    .description("Test skill: " + skill)
                    .tags(List.of("test"))
                    .build())
                .toList();
        }
    }
}