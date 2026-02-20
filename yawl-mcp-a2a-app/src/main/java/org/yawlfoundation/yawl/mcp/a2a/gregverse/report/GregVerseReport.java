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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.report;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data model for Greg-Verse simulation reports.
 *
 * <p>This record captures all aspects of a multi-agent simulation run,
 * including agent interactions, skill invocations, transactions,
 * and business outcomes.</p>
 *
 * @param scenarioId the scenario that was executed
 * @param generatedAt timestamp when the report was generated
 * @param totalDuration total execution duration
 * @param agentResults results from each participating agent
 * @param interactions list of agent-to-agent interactions
 * @param transactions skill marketplace transactions
 * @param networkGraph collaboration network structure
 * @param businessOutcomes measurable business outcomes
 * @param tokenAnalysis token savings analysis
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record GregVerseReport(
    String scenarioId,
    Instant generatedAt,
    Duration totalDuration,
    List<AgentResult> agentResults,
    List<AgentInteraction> interactions,
    List<SkillTransaction> transactions,
    CollaborationNetwork networkGraph,
    BusinessOutcomes businessOutcomes,
    TokenAnalysis tokenAnalysis
) {

    /**
     * Create an empty report builder.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the number of successful agent executions.
     *
     * @return successful agent count
     */
    public int getSuccessfulAgents() {
        return (int) agentResults.stream()
            .filter(AgentResult::success)
            .count();
    }

    /**
     * Get the number of failed agent executions.
     *
     * @return failed agent count
     */
    public int getFailedAgents() {
        return (int) agentResults.stream()
            .filter(r -> !r.success())
            .count();
    }

    /**
     * Get the total number of interactions.
     *
     * @return interaction count
     */
    public int getTotalInteractions() {
        return interactions.size();
    }

    /**
     * Get the total number of transactions.
     *
     * @return transaction count
     */
    public int getTotalTransactions() {
        return transactions.size();
    }

    /**
     * Get the total revenue from transactions.
     *
     * @return total revenue in credits
     */
    public double getTotalRevenue() {
        return transactions.stream()
            .mapToDouble(SkillTransaction::price)
            .sum();
    }

    /**
     * Builder for constructing Greg-Verse reports.
     */
    public static class Builder {
        private String scenarioId;
        private Instant generatedAt = Instant.now();
        private Duration totalDuration = Duration.ZERO;
        private final List<AgentResult> agentResults = new ArrayList<>();
        private final List<AgentInteraction> interactions = new ArrayList<>();
        private final List<SkillTransaction> transactions = new ArrayList<>();
        private CollaborationNetwork networkGraph;
        private BusinessOutcomes businessOutcomes;
        private TokenAnalysis tokenAnalysis;

        public Builder scenarioId(String scenarioId) {
            this.scenarioId = scenarioId;
            return this;
        }

        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public Builder totalDuration(Duration totalDuration) {
            this.totalDuration = totalDuration;
            return this;
        }

        public Builder addAgentResult(AgentResult result) {
            this.agentResults.add(result);
            return this;
        }

        public Builder agentResults(List<AgentResult> results) {
            this.agentResults.clear();
            this.agentResults.addAll(results);
            return this;
        }

        public Builder addInteraction(AgentInteraction interaction) {
            this.interactions.add(interaction);
            return this;
        }

        public Builder interactions(List<AgentInteraction> interactions) {
            this.interactions.clear();
            this.interactions.addAll(interactions);
            return this;
        }

        public Builder addTransaction(SkillTransaction transaction) {
            this.transactions.add(transaction);
            return this;
        }

        public Builder transactions(List<SkillTransaction> transactions) {
            this.transactions.clear();
            this.transactions.addAll(transactions);
            return this;
        }

        public Builder networkGraph(CollaborationNetwork networkGraph) {
            this.networkGraph = networkGraph;
            return this;
        }

        public Builder businessOutcomes(BusinessOutcomes businessOutcomes) {
            this.businessOutcomes = businessOutcomes;
            return this;
        }

        public Builder tokenAnalysis(TokenAnalysis tokenAnalysis) {
            this.tokenAnalysis = tokenAnalysis;
            return this;
        }

        public GregVerseReport build() {
            if (networkGraph == null) {
                networkGraph = CollaborationNetwork.fromInteractions(interactions);
            }
            if (businessOutcomes == null) {
                businessOutcomes = BusinessOutcomes.empty();
            }
            if (tokenAnalysis == null) {
                tokenAnalysis = new TokenAnalysis(0, 0);
            }
            return new GregVerseReport(
                scenarioId,
                generatedAt,
                totalDuration,
                List.copyOf(agentResults),
                List.copyOf(interactions),
                List.copyOf(transactions),
                networkGraph,
                businessOutcomes,
                tokenAnalysis
            );
        }
    }

    /**
     * Result from a single agent's execution.
     *
     * @param agentId the agent's unique identifier
     * @param displayName the agent's display name
     * @param success whether the execution was successful
     * @param startTime execution start time
     * @param endTime execution end time
     * @param skillInvocations skills invoked by this agent
     * @param output the agent's output/response
     * @param error error message if failed
     */
    public record AgentResult(
        String agentId,
        String displayName,
        boolean success,
        Instant startTime,
        Instant endTime,
        List<SkillInvocation> skillInvocations,
        String output,
        String error
    ) {
        /**
         * Get the execution duration.
         *
         * @return duration between start and end
         */
        public Duration duration() {
            return Duration.between(startTime, endTime);
        }

        /**
         * Get the number of skills invoked.
         *
         * @return skill invocation count
         */
        public int getSkillCount() {
            return skillInvocations != null ? skillInvocations.size() : 0;
        }
    }

    /**
     * Record of a skill invocation.
     *
     * @param skillId the skill identifier
     * @param skillName the skill display name
     * @param input JSON input to the skill
     * @param output output from the skill
     * @param duration execution duration
     * @param success whether the invocation succeeded
     */
    public record SkillInvocation(
        String skillId,
        String skillName,
        String input,
        String output,
        Duration duration,
        boolean success
    ) {}

    /**
     * Record of an agent-to-agent interaction.
     *
     * @param fromAgent source agent ID
     * @param toAgent target agent ID
     * @param interactionType type of interaction
     * @param skillId skill involved (if any)
     * @param timestamp when the interaction occurred
     * @param content interaction content/summary
     */
    public record AgentInteraction(
        String fromAgent,
        String toAgent,
        InteractionType interactionType,
        String skillId,
        Instant timestamp,
        String content
    ) {
        /**
         * Types of agent interactions.
         */
        public enum InteractionType {
            /** Request for advice or input */
            CONSULTATION,
            /** Skill invocation request */
            SKILL_REQUEST,
            /** Skill response */
            SKILL_RESPONSE,
            /** Negotiation message */
            NEGOTIATION,
            /** Task delegation */
            DELEGATION,
            /** Progress update */
            STATUS_UPDATE,
            /** Collaboration on shared task */
            COLLABORATION
        }
    }

    /**
     * Record of a skill marketplace transaction.
     *
     * @param transactionId unique transaction ID
     * @param buyerAgentId agent purchasing the skill
     * @param sellerAgentId agent providing the skill
     * @param skillId skill being transacted
     * @param skillName skill display name
     * @param price transaction price in credits
     * @param timestamp when the transaction occurred
     * @param success whether the transaction succeeded
     */
    public record SkillTransaction(
        String transactionId,
        String buyerAgentId,
        String sellerAgentId,
        String skillId,
        String skillName,
        double price,
        Instant timestamp,
        boolean success
    ) {}

    /**
     * Collaboration network graph structure.
     *
     * @param nodes agent nodes in the network
     * @param edges connections between agents
     */
    public record CollaborationNetwork(
        List<NetworkNode> nodes,
        List<NetworkEdge> edges
    ) {
        /**
         * Build a network from interaction records.
         *
         * @param interactions list of interactions
         * @return constructed network
         */
        public static CollaborationNetwork fromInteractions(List<AgentInteraction> interactions) {
            Map<String, NetworkNode> nodeMap = new HashMap<>();
            Map<String, NetworkEdge> edgeMap = new HashMap<>();

            for (AgentInteraction interaction : interactions) {
                String from = interaction.fromAgent();
                String to = interaction.toAgent();

                nodeMap.computeIfAbsent(from, k -> new NetworkNode(k, k, 0));
                nodeMap.computeIfAbsent(to, k -> new NetworkNode(k, k, 0));

                // Increment interaction counts
                NetworkNode fromNode = nodeMap.get(from);
                nodeMap.put(from, new NetworkNode(from, from, fromNode.interactionCount() + 1));

                // Create or update edge
                String edgeKey = from + "->" + to;
                NetworkEdge existing = edgeMap.get(edgeKey);
                if (existing == null) {
                    edgeMap.put(edgeKey, new NetworkEdge(from, to, 1, interaction.interactionType().name()));
                } else {
                    edgeMap.put(edgeKey, new NetworkEdge(from, to, existing.weight() + 1, interaction.interactionType().name()));
                }
            }

            return new CollaborationNetwork(
                new ArrayList<>(nodeMap.values()),
                new ArrayList<>(edgeMap.values())
            );
        }
    }

    /**
     * Network node representing an agent.
     *
     * @param id node identifier
     * @param label display label
     * @param interactionCount number of interactions
     */
    public record NetworkNode(String id, String label, int interactionCount) {}

    /**
     * Network edge representing a connection.
     *
     * @param source source node ID
     * @param target target node ID
     * @param weight connection weight (interaction count)
     * @param type interaction type
     */
    public record NetworkEdge(String source, String target, int weight, String type) {}

    /**
     * Business outcomes from a simulation.
     *
     * @param ideasQualified number of ideas that passed qualification
     * @param mvpsBuilt number of MVPs built
     * @param skillsCreated number of skills created
     * @param revenueGenerated simulated revenue
     * @param partnerships number of partnerships formed
     * @param timeSaved estimated time saved in hours
     */
    public record BusinessOutcomes(
        int ideasQualified,
        int mvpsBuilt,
        int skillsCreated,
        double revenueGenerated,
        int partnerships,
        double timeSaved
    ) {
        /**
         * Create empty business outcomes.
         *
         * @return empty outcomes
         */
        public static BusinessOutcomes empty() {
            return new BusinessOutcomes(0, 0, 0, 0.0, 0, 0.0);
        }
    }

    /**
     * Token analysis for comparing formats.
     *
     * @param yamlTokens estimated YAML token count
     * @param xmlTokens estimated XML token count
     */
    public record TokenAnalysis(int yamlTokens, int xmlTokens) {
        /**
         * Get the savings percentage.
         *
         * @return percentage savings from YAML vs XML
         */
        public double getSavingsPercentage() {
            if (xmlTokens == 0) return 0;
            return ((double) (xmlTokens - yamlTokens) / xmlTokens) * 100;
        }

        /**
         * Get the compression ratio.
         *
         * @return compression ratio (XML/YAML)
         */
        public double getCompressionRatio() {
            if (yamlTokens == 0) return 1;
            return (double) xmlTokens / yamlTokens;
        }
    }
}
