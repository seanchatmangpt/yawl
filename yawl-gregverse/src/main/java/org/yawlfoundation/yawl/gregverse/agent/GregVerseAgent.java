package org.yawlfoundation.yawl.gregverse.agent;

import java.util.List;

/**
 * Interface for Greg-Verse AI business advisors.
 *
 * <p>Greg-Verse is a collection of AI agents embodying expertise from
 * renowned business strategists, creators, and entrepreneurs. Each agent
 * provides specialized advice in their domain of expertise.</p>
 *
 * @author YAWL Foundation
 * @since 6.0.0
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
     * Initializes the agent after registration.
     */
    void initialize();

    /**
     * Processes a user query and returns the agent's response.
     *
     * @param query the user's question or request
     * @return the agent's response based on their expertise
     */
    String processQuery(String query);

    /**
     * Provides advice formatted in the agent's signature style.
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
}