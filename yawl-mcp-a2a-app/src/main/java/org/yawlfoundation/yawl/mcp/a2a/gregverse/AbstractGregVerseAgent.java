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

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.util.List;

/**
 * Abstract base class for Greg-Verse agents providing common functionality.
 *
 * <p>This class implements the shared logic for all Greg-Verse agents including
 * LLM integration via ZaiService, agent card creation, and skill management.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public abstract class AbstractGregVerseAgent implements GregVerseAgent {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ZaiService zaiService;
    protected final String apiKey;

    /**
     * Creates a new Greg-Verse agent with the provided API key.
     *
     * @param apiKey the Z.AI API key for LLM interactions
     * @throws IllegalArgumentException if apiKey is null or empty
     */
    protected AbstractGregVerseAgent(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        this.apiKey = apiKey;
        this.zaiService = new ZaiService(apiKey);
        this.zaiService.setSystemPrompt(getSystemPrompt());
        logger.info("Initialized {} agent", getDisplayName());
    }

    /**
     * Creates a new Greg-Verse agent using the ZAI_API_KEY environment variable.
     *
     * @throws IllegalStateException if ZAI_API_KEY environment variable is not set
     */
    protected AbstractGregVerseAgent() {
        this.apiKey = System.getenv("ZAI_API_KEY");
        if (this.apiKey == null || this.apiKey.trim().isEmpty()) {
            throw new IllegalStateException(
                "ZAI_API_KEY environment variable is required. " +
                "Set it in your shell: export ZAI_API_KEY=\"your-api-key\""
            );
        }
        this.zaiService = new ZaiService(this.apiKey);
        this.zaiService.setSystemPrompt(getSystemPrompt());
        logger.info("Initialized {} agent", getDisplayName());
    }

    @Override
    public AgentCard createAgentCard(int port, String basePath) {
        String endpointUrl = String.format("http://localhost:%d%s/%s", port, basePath, getAgentId());

        return AgentCard.builder()
                .name(getAgentId())
                .description(getBio())
                .version("6.0.0")
                .provider(new AgentProvider("YAWL Foundation - Greg-Verse", "https://yawlfoundation.github.io"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(createAgentSkills())
                .supportedInterfaces(List.of(new AgentInterface("a2a-rest", endpointUrl)))
                .build();
    }

    @Override
    public String processQuery(String query) {
        logger.debug("{} processing query: {}", getDisplayName(), query);
        String response = zaiService.chat(query);
        logger.debug("{} response length: {} chars", getDisplayName(), response.length());
        return response;
    }

    @Override
    public String processSkillQuery(String skillId, String query) {
        logger.debug("{} processing skill '{}' query: {}", getDisplayName(), skillId, query);

        String skillPrompt = String.format(
            "Using your expertise in %s, please advise on the following:\n\n%s",
            skillId, query
        );

        return zaiService.chat(skillPrompt);
    }

    @Override
    public String provideAdvice(String topic, String context) {
        logger.debug("{} providing advice on: {}", getDisplayName(), topic);

        String advicePrompt = String.format(
            "As %s, provide your expert advice on the following topic.\n\n" +
            "Topic: %s\n\n" +
            "Context: %s\n\n" +
            "Format your response in your signature style: %s\n" +
            "Response format: %s",
            getDisplayName(),
            topic,
            context,
            getCommunicationStyle(),
            getResponseFormat()
        );

        return zaiService.chat(advicePrompt);
    }

    /**
     * Clears the conversation history for this agent.
     */
    public void clearHistory() {
        zaiService.clearHistory();
        logger.debug("{} conversation history cleared", getDisplayName());
    }

    /**
     * Returns the underlying ZaiService for advanced operations.
     *
     * @return the ZaiService instance
     */
    protected ZaiService getZaiService() {
        return zaiService;
    }

    /**
     * Sends a raw chat message without any formatting.
     *
     * @param message the message to send
     * @return the raw LLM response
     */
    protected String rawChat(String message) {
        return zaiService.chat(message);
    }
}
