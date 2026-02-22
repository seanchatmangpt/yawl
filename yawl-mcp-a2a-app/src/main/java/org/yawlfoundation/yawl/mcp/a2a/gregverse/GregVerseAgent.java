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

import java.util.List;

/**
 * Interface for Greg-Verse AI business advisors.
 *
 * <p>Greg-Verse is a collection of AI agents embodying expertise from
 * renowned business strategists, creators, and entrepreneurs. Each agent
 * provides specialized advice in their domain of expertise.</p>
 *
 * <h2>Available Agents</h2>
 * <ul>
 *   <li><strong>Greg Isenberg</strong> - AI skills strategy, product vision, startup advisory</li>
 *   <li><strong>James</strong> - SEO analysis, positioning, newsletter, conversion copy</li>
 *   <li><strong>Nicolas Cole</strong> - Skill creation, digital writing, personal brand</li>
 *   <li><strong>Dickie Bush</strong> - Creator economy, newsletter growth, cohort courses</li>
 *   <li><strong>Leo</strong> - App development, curation strategy, filtering</li>
 *   <li><strong>Justin Welsh</strong> - Solopreneurship, B2B consulting, LinkedIn strategy</li>
 *   <li><strong>Dan Romero</strong> - Agent internet, API-first design, protocol thinking</li>
 *   <li><strong>Blake Anderson</strong> - Gamification, life optimization, quest design</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public interface GregVerseAgent {

    /**
     * Returns the agent's unique identifier.
     *
     * @return the agent ID (e.g., "greg-isenberg", "nicolas-cole")
     */
    String getAgentId();

    /**
     * Returns the agent's display name.
     *
     * @return the human-readable name
     */
    String getDisplayName();

    /**
     * Returns the agent's bio/background.
     *
     * @return a brief description of the agent's expertise and background
     */
    String getBio();

    /**
     * Returns the agent's specialized skills.
     *
     * @return list of skill identifiers this agent specializes in
     */
    List<String> getSpecializedSkills();

    /**
     * Returns the system prompt that defines the agent's personality and expertise.
     *
     * @return the full system prompt for the LLM
     */
    String getSystemPrompt();

    /**
     * Returns the agent's communication style.
     *
     * @return description of how the agent communicates (e.g., "Concise, data-driven")
     */
    String getCommunicationStyle();

    /**
     * Returns the agent's areas of expertise.
     *
     * @return list of expertise areas
     */
    List<String> getExpertise();

    /**
     * Creates an A2A AgentCard for this agent.
     *
     * @param port the server port for the agent endpoint
     * @param basePath the base path for the A2A endpoint
     * @return the configured AgentCard
     */
    AgentCard createAgentCard(int port, String basePath);

    /**
     * Creates A2A AgentSkill objects for this agent.
     *
     * @return list of AgentSkill definitions
     */
    List<AgentSkill> createAgentSkills();

    /**
     * Processes a user query and returns the agent's response.
     *
     * @param query the user's question or request
     * @return the agent's response based on their expertise
     */
    String processQuery(String query);

    /**
     * Processes a query related to a specific skill.
     *
     * @param skillId the skill being invoked
     * @param query the user's question or request
     * @return the agent's response
     */
    String processSkillQuery(String skillId, String query);

    /**
     * Returns advice formatted in the agent's signature style.
     *
     * @param topic the topic to advise on
     * @param context additional context for the advice
     * @return formatted advice
     */
    String provideAdvice(String topic, String context);

    /**
     * Returns the agent's typical response format.
     *
     * @return description of response format (e.g., "Bullet points with action items")
     */
    String getResponseFormat();

    /**
     * Clears the conversation history for this agent.
     */
    void clearHistory();
}
