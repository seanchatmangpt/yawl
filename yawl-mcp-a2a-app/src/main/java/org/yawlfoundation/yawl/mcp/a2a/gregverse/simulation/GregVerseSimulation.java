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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl.LeoLeojrrAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.config.GregVerseConfig;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport.AgentInteraction;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport.AgentInteraction.InteractionType;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport.AgentResult;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport.BusinessOutcomes;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport.SkillInvocation;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport.SkillTransaction;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport.TokenAnalysis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Main simulation engine for multi-agent Greg-Verse scenarios using Java 25 virtual threads.
 *
 * <p>This class orchestrates multi-agent simulations where business advisor agents
 * collaborate on scenarios, invoke skills, and produce measurable business outcomes.
 * It supports both parallel execution using virtual threads and sequential execution
 * based on configuration.</p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><strong>Virtual Thread Execution</strong>: Uses {@code Executors.newVirtualThreadPerTaskExecutor()}
 *       for lightweight concurrent agent execution</li>
 *   <li><strong>Flexible Execution Modes</strong>: Parallel or sequential execution based on config</li>
 *   <li><strong>Interaction Tracking</strong>: Records all agent-to-agent interactions</li>
 *   <li><strong>Skill Invocation Tracking</strong>: Monitors which skills each agent invokes</li>
 *   <li><strong>Business Outcome Calculation</strong>: Derives measurable outcomes from results</li>
 *   <li><strong>Graceful Error Handling</strong>: Handles timeouts and errors without crashing</li>
 *   <li><strong>Comprehensive Reporting</strong>: Generates {@link GregVerseReport} with all results</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * GregVerseConfig config = GregVerseConfig.forScenario("gvs-1-startup-idea");
 * GregVerseSimulation simulation = new GregVerseSimulation(config);
 * GregVerseReport report = simulation.run();
 * System.out.println("Successful agents: " + report.getSuccessfulAgents());
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class GregVerseSimulation {

    private static final Logger LOGGER = LoggerFactory.getLogger(GregVerseSimulation.class);

    /**
     * Registry of available Greg-Verse agent suppliers, keyed by agent ID.
     * New agents should be registered here.
     */
    private static final Map<String, AgentSupplier> AGENT_REGISTRY = Map.ofEntries(
        Map.entry("leo-leojrr", LeoLeojrrAgent::new)
        // Additional agents will be registered as they are implemented:
        // Map.entry("greg-isenberg", GregIsenbergAgent::new),
        // Map.entry("james", JamesAgent::new),
        // Map.entry("nicolas-cole", NicolasColeAgent::new),
        // Map.entry("dickie-bush", DickieBushAgent::new),
        // Map.entry("justin-welsh", JustinWelshAgent::new),
        // Map.entry("dan-romero", DanRomeroAgent::new),
        // Map.entry("blake-anderson", BlakeAndersonAgent::new)
    );

    private final GregVerseConfig config;
    private final RandomGenerator random;
    private final Collection<AgentInteraction> interactions;
    private final Collection<SkillTransaction> transactions;
    private final Map<String, AgentResult> agentResults;

    /**
     * Functional interface for creating agent instances.
     */
    @FunctionalInterface
    private interface AgentSupplier {
        GregVerseAgent get() throws Exception;
    }

    /**
     * Creates a new simulation with the specified configuration.
     *
     * @param config the simulation configuration
     * @throws IllegalArgumentException if config is null
     */
    public GregVerseSimulation(GregVerseConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.random = RandomGeneratorFactory.getDefault().create();
        this.interactions = new ConcurrentLinkedQueue<>();
        this.transactions = new ConcurrentLinkedQueue<>();
        this.agentResults = new ConcurrentHashMap<>();
    }

    /**
     * Runs the simulation and returns a comprehensive report.
     *
     * <p>Execution mode (parallel vs sequential) is determined by
     * {@link GregVerseConfig#parallelExecution()}.</p>
     *
     * @return the simulation report containing all results
     */
    public GregVerseReport run() {
        Instant startTime = Instant.now();
        LOGGER.info("Starting Greg-Verse simulation: scenario={}, parallel={}",
            config.scenarioId(), config.parallelExecution());

        List<GregVerseAgent> agents = resolveAgents();

        if (agents.isEmpty()) {
            LOGGER.warn("No agents resolved for simulation");
            return buildEmptyReport(startTime);
        }

        LOGGER.info("Resolved {} agents for simulation", agents.size());

        if (config.parallelExecution()) {
            executeAgentsParallel(agents);
        } else {
            executeAgentsSequential(agents);
        }

        GregVerseReport report = buildReport(startTime);
        LOGGER.info("Simulation complete: {} successful, {} failed, duration={}",
            report.getSuccessfulAgents(), report.getFailedAgents(), report.totalDuration());

        return report;
    }

    /**
     * Runs a single skill invocation on a specific agent.
     *
     * <p>This method is optimized for the single skill mode where one agent
     * invokes one skill with provided input.</p>
     *
     * @return the simulation report with single agent result
     */
    public GregVerseReport runSingleSkill() {
        if (!config.isSingleSkillMode()) {
            throw new IllegalStateException("runSingleSkill() called without single skill configuration");
        }

        Instant startTime = Instant.now();
        LOGGER.info("Running single skill: agent={}, skill={}",
            config.singleAgentId(), config.singleSkillId());

        GregVerseAgent agent = resolveAgent(config.singleAgentId());
        if (agent == null) {
            LOGGER.error("Agent not found: {}", config.singleAgentId());
            return buildEmptyReport(startTime);
        }

        AgentResult result = executeAgentSkill(agent, config.singleSkillId(), config.skillInput());
        agentResults.put(result.agentId(), result);

        return buildReport(startTime);
    }

    /**
     * Resolves agents based on configuration.
     *
     * <p>If specific agent IDs are configured, only those agents are loaded.
     * Otherwise, all registered agents are instantiated.</p>
     *
     * @return list of resolved agent instances
     */
    private List<GregVerseAgent> resolveAgents() {
        List<GregVerseAgent> agents = new ArrayList<>();

        if (config.hasAgentFilter()) {
            for (String agentId : config.agentIds()) {
                GregVerseAgent agent = resolveAgent(agentId);
                if (agent != null) {
                    agents.add(agent);
                } else {
                    LOGGER.warn("Requested agent not found in registry: {}", agentId);
                }
            }
        } else {
            for (Map.Entry<String, AgentSupplier> entry : AGENT_REGISTRY.entrySet()) {
                GregVerseAgent agent = resolveAgent(entry.getKey());
                if (agent != null) {
                    agents.add(agent);
                }
            }
        }

        return agents;
    }

    /**
     * Resolves a single agent by ID.
     *
     * @param agentId the agent identifier
     * @return the agent instance, or null if not found or instantiation fails
     */
    private GregVerseAgent resolveAgent(String agentId) {
        AgentSupplier supplier = AGENT_REGISTRY.get(agentId);
        if (supplier == null) {
            LOGGER.warn("No supplier registered for agent: {}", agentId);
            return null;
        }

        try {
            return supplier.get();
        } catch (Exception e) {
            LOGGER.error("Failed to instantiate agent {}: {}", agentId, e.getMessage());
            return null;
        }
    }

    /**
     * Executes agents in parallel using virtual threads.
     *
     * <p>Each agent runs on its own virtual thread. Results are collected
     * as they complete, with timeout handling per agent.</p>
     *
     * @param agents the agents to execute
     */
    private void executeAgentsParallel(List<GregVerseAgent> agents) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<AgentResult>> futures = agents.stream()
                .map(agent -> executor.submit(createAgentTask(agent)))
                .toList();

            for (int i = 0; i < futures.size(); i++) {
                String agentId = agents.get(i).getAgentId();
                try {
                    long timeoutMs = config.getTimeoutDuration().toMillis();
                    AgentResult result = futures.get(i).get(timeoutMs, TimeUnit.MILLISECONDS);
                    agentResults.put(result.agentId(), result);
                    LOGGER.debug("Agent {} completed successfully in {}ms",
                        agentId, result.duration().toMillis());
                } catch (TimeoutException e) {
                    LOGGER.warn("Agent {} timed out after {}ms", agentId, config.getTimeoutDuration().toMillis());
                    AgentResult timeoutResult = createTimeoutResult(agents.get(i));
                    agentResults.put(agentId, timeoutResult);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    LOGGER.error("Agent {} execution failed: {}", agentId, cause.getMessage());
                    AgentResult errorResult = createErrorResult(agents.get(i), cause);
                    agentResults.put(agentId, errorResult);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Agent {} execution interrupted", agentId);
                    AgentResult interruptedResult = createInterruptedResult(agents.get(i));
                    agentResults.put(agentId, interruptedResult);
                }
            }
        }
    }

    /**
     * Executes agents sequentially in order.
     *
     * <p>Each agent completes before the next one starts. Useful for
     * debugging or when ordering matters.</p>
     *
     * @param agents the agents to execute
     */
    private void executeAgentsSequential(List<GregVerseAgent> agents) {
        for (GregVerseAgent agent : agents) {
            try {
                AgentResult result = executeAgent(agent);
                agentResults.put(result.agentId(), result);
                LOGGER.debug("Agent {} completed in {}ms",
                    agent.getAgentId(), result.duration().toMillis());
            } catch (Exception e) {
                LOGGER.error("Agent {} execution failed: {}", agent.getAgentId(), e.getMessage());
                AgentResult errorResult = createErrorResult(agent, e);
                agentResults.put(errorResult.agentId(), errorResult);
            }
        }
    }

    /**
     * Creates a callable task for an agent execution.
     *
     * @param agent the agent to execute
     * @return a callable that returns the agent result
     */
    private Callable<AgentResult> createAgentTask(GregVerseAgent agent) {
        return () -> executeAgent(agent);
    }

    /**
     * Executes a single agent and returns its result.
     *
     * <p>This method simulates agent behavior including:</p>
     * <ul>
     *   <li>Processing the scenario query</li>
     *   <li>Invoking relevant skills</li>
     *   <li>Recording interactions with other agents</li>
     *   <li>Generating output</li>
     * </ul>
     *
     * @param agent the agent to execute
     * @return the execution result
     */
    private AgentResult executeAgent(GregVerseAgent agent) {
        Instant startTime = Instant.now();
        String agentId = agent.getAgentId();
        List<SkillInvocation> skillInvocations = new ArrayList<>();
        String output;
        String error = null;
        boolean success = true;

        try {
            // Build scenario-specific query based on agent expertise
            String query = buildScenarioQuery(agent);
            LOGGER.debug("Agent {} processing query: {}", agentId, query.substring(0, Math.min(100, query.length())));

            // Invoke relevant skills based on scenario and agent expertise
            List<String> relevantSkills = findRelevantSkills(agent);
            for (String skillId : relevantSkills) {
                SkillInvocation invocation = invokeSkill(agent, skillId, query);
                skillInvocations.add(invocation);

                // Simulate collaboration with other agents
                simulateAgentCollaboration(agent, skillId, invocation);
            }

            // Get final output from agent
            output = agent.processQuery(query);

        } catch (Exception e) {
            LOGGER.error("Agent {} execution error: {}", agentId, e.getMessage(), e);
            success = false;
            error = e.getMessage();
            output = null;
        }

        Instant endTime = Instant.now();

        return new AgentResult(
            agentId,
            agent.getDisplayName(),
            success,
            startTime,
            endTime,
            skillInvocations,
            output,
            error
        );
    }

    /**
     * Executes a single skill on an agent with provided input.
     *
     * @param agent the agent to execute
     * @param skillId the skill to invoke
     * @param input JSON input for the skill
     * @return the execution result
     */
    private AgentResult executeAgentSkill(GregVerseAgent agent, String skillId, String input) {
        Instant startTime = Instant.now();
        String agentId = agent.getAgentId();
        List<SkillInvocation> skillInvocations = new ArrayList<>();
        String output;
        String error = null;
        boolean success = true;

        try {
            SkillInvocation invocation = invokeSkill(agent, skillId, input != null ? input : "");
            skillInvocations.add(invocation);
            output = invocation.output();
        } catch (Exception e) {
            LOGGER.error("Agent {} skill {} execution error: {}", agentId, skillId, e.getMessage());
            success = false;
            error = e.getMessage();
            output = null;
        }

        Instant endTime = Instant.now();

        return new AgentResult(
            agentId,
            agent.getDisplayName(),
            success,
            startTime,
            endTime,
            skillInvocations,
            output,
            error
        );
    }

    /**
     * Builds a scenario-specific query for an agent.
     *
     * @param agent the agent
     * @return the query string
     */
    private String buildScenarioQuery(GregVerseAgent agent) {
        String scenarioId = config.scenarioId();

        if (scenarioId == null || scenarioId.isBlank()) {
            return "Provide your expert advice based on your background and expertise.";
        }

        return switch (scenarioId) {
            case "gvs-1-startup-idea" ->
                "A founder has a new startup idea for an AI-powered productivity tool. " +
                "Evaluate this idea based on your expertise in " + String.join(", ", agent.getExpertise()) + ". " +
                "Consider market fit, differentiation, and execution strategy.";
            case "gvs-2-content-business" ->
                "An entrepreneur wants to build a content-based business. " +
                "Advise on strategy, positioning, and monetization based on your experience.";
            case "gvs-3-api-infrastructure" ->
                "A technical founder is building API-first infrastructure. " +
                "Provide guidance on technical strategy, developer experience, and scaling.";
            case "gvs-4-skill-transaction" ->
                "Evaluate a potential skill transaction in the AI skills marketplace. " +
                "What factors should determine pricing and quality?";
            case "gvs-5-product-launch" ->
                "A team is preparing to launch a new product. " +
                "Provide launch strategy advice including positioning, channels, and timing.";
            default ->
                "Apply your expertise in " + String.join(", ", agent.getExpertise()) +
                " to advise on scenario: " + scenarioId;
        };
    }

    /**
     * Finds skills relevant to the current scenario for an agent.
     *
     * @param agent the agent
     * @return list of relevant skill IDs
     */
    private List<String> findRelevantSkills(GregVerseAgent agent) {
        List<String> specializedSkills = agent.getSpecializedSkills();
        if (specializedSkills.isEmpty()) {
            return List.of();
        }

        // Return a subset of skills based on scenario and randomness
        int skillCount = Math.min(1 + random.nextInt(2), specializedSkills.size());
        List<String> result = new ArrayList<>();
        List<String> shuffled = new ArrayList<>(specializedSkills);

        for (int i = 0; i < skillCount && !shuffled.isEmpty(); i++) {
            int index = random.nextInt(shuffled.size());
            result.add(shuffled.remove(index));
        }

        return result;
    }

    /**
     * Invokes a skill on an agent.
     *
     * @param agent the agent
     * @param skillId the skill to invoke
     * @param input the input for the skill
     * @return the skill invocation record
     */
    private SkillInvocation invokeSkill(GregVerseAgent agent, String skillId, String input) {
        Instant skillStart = Instant.now();
        String output;
        boolean success = true;

        try {
            output = agent.processSkillQuery(skillId, input);
        } catch (Exception e) {
            LOGGER.warn("Skill {} invocation failed for agent {}: {}", skillId, agent.getAgentId(), e.getMessage());
            output = "Skill invocation failed: " + e.getMessage();
            success = false;
        }

        Duration duration = Duration.between(skillStart, Instant.now());

        return new SkillInvocation(
            skillId,
            formatSkillName(skillId),
            input,
            output,
            duration,
            success
        );
    }

    /**
     * Formats a skill ID into a human-readable name.
     *
     * @param skillId the skill ID
     * @return formatted name
     */
    private String formatSkillName(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return "Unknown Skill";
        }

        StringBuilder result = new StringBuilder();
        for (String part : skillId.split("-")) {
            if (!result.isEmpty()) {
                result.append(" ");
            }
            result.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1));
        }
        return result.toString();
    }

    /**
     * Simulates collaboration between agents by recording interactions.
     *
     * @param sourceAgent the agent initiating collaboration
     * @param skillId the skill being discussed
     * @param invocation the skill invocation that triggered collaboration
     */
    private void simulateAgentCollaboration(GregVerseAgent sourceAgent, String skillId, SkillInvocation invocation) {
        // Find potential collaboration partners (other agents with relevant expertise)
        List<String> potentialPartners = AGENT_REGISTRY.keySet().stream()
            .filter(id -> !id.equals(sourceAgent.getAgentId()))
            .toList();

        if (potentialPartners.isEmpty() || random.nextDouble() > 0.3) {
            // 30% chance of collaboration
            return;
        }

        String partnerId = potentialPartners.get(random.nextInt(potentialPartners.size()));

        // Record consultation interaction
        AgentInteraction consultation = new AgentInteraction(
            sourceAgent.getAgentId(),
            partnerId,
            InteractionType.CONSULTATION,
            skillId,
            Instant.now(),
            "Consulting on skill: " + skillId
        );
        interactions.add(consultation);

        // Record skill response
        AgentInteraction response = new AgentInteraction(
            partnerId,
            sourceAgent.getAgentId(),
            InteractionType.SKILL_RESPONSE,
            skillId,
            Instant.now().plusMillis(50 + random.nextInt(200)),
            "Provided input on: " + skillId
        );
        interactions.add(response);

        // Possibly create a skill transaction
        if (random.nextDouble() < 0.2) {
            createSkillTransaction(sourceAgent.getAgentId(), partnerId, skillId);
        }
    }

    /**
     * Creates a simulated skill transaction.
     *
     * @param buyerId the buyer agent ID
     * @param sellerId the seller agent ID
     * @param skillId the skill being transacted
     */
    private void createSkillTransaction(String buyerId, String sellerId, String skillId) {
        double price = 10.0 + random.nextDouble() * 90.0; // $10-$100 range

        SkillTransaction transaction = new SkillTransaction(
            UUID.randomUUID().toString(),
            buyerId,
            sellerId,
            skillId,
            formatSkillName(skillId),
            Math.round(price * 100.0) / 100.0,
            Instant.now(),
            true
        );
        transactions.add(transaction);
        LOGGER.debug("Created transaction: {} -> {} for {} (${})",
            buyerId, sellerId, skillId, transaction.price());
    }

    /**
     * Creates a result for a timed-out agent execution.
     *
     * @param agent the agent that timed out
     * @return the timeout result
     */
    private AgentResult createTimeoutResult(GregVerseAgent agent) {
        Instant now = Instant.now();
        Duration timeout = config.getTimeoutDuration();

        return new AgentResult(
            agent.getAgentId(),
            agent.getDisplayName(),
            false,
            now.minus(timeout),
            now,
            List.of(),
            null,
            "Execution timed out after " + timeout.toSeconds() + " seconds"
        );
    }

    /**
     * Creates a result for a failed agent execution.
     *
     * @param agent the agent that failed
     * @param error the error that occurred
     * @return the error result
     */
    private AgentResult createErrorResult(GregVerseAgent agent, Throwable error) {
        Instant now = Instant.now();
        String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();

        return new AgentResult(
            agent.getAgentId(),
            agent.getDisplayName(),
            false,
            now,
            now,
            List.of(),
            null,
            message
        );
    }

    /**
     * Creates a result for an interrupted agent execution.
     *
     * @param agent the agent that was interrupted
     * @return the interrupted result
     */
    private AgentResult createInterruptedResult(GregVerseAgent agent) {
        Instant now = Instant.now();

        return new AgentResult(
            agent.getAgentId(),
            agent.getDisplayName(),
            false,
            now,
            now,
            List.of(),
            null,
            "Execution was interrupted"
        );
    }

    /**
     * Builds the final simulation report.
     *
     * @param startTime the simulation start time
     * @return the comprehensive report
     */
    private GregVerseReport buildReport(Instant startTime) {
        Instant endTime = Instant.now();
        Duration totalDuration = Duration.between(startTime, endTime);

        List<AgentResult> results = new ArrayList<>(agentResults.values());
        List<AgentInteraction> interactionList = new ArrayList<>(interactions);
        List<SkillTransaction> transactionList = new ArrayList<>(transactions);

        BusinessOutcomes outcomes = calculateBusinessOutcomes(results, interactionList, transactionList);
        TokenAnalysis tokenAnalysis = calculateTokenAnalysis(results);

        return GregVerseReport.builder()
            .scenarioId(config.scenarioId())
            .generatedAt(endTime)
            .totalDuration(totalDuration)
            .agentResults(results)
            .interactions(interactionList)
            .transactions(transactionList)
            .businessOutcomes(outcomes)
            .tokenAnalysis(tokenAnalysis)
            .build();
    }

    /**
     * Builds an empty report for failed simulations.
     *
     * @param startTime the simulation start time
     * @return an empty report
     */
    private GregVerseReport buildEmptyReport(Instant startTime) {
        return GregVerseReport.builder()
            .scenarioId(config.scenarioId())
            .generatedAt(Instant.now())
            .totalDuration(Duration.between(startTime, Instant.now()))
            .agentResults(List.of())
            .interactions(List.of())
            .transactions(List.of())
            .businessOutcomes(BusinessOutcomes.empty())
            .tokenAnalysis(new TokenAnalysis(0, 0))
            .build();
    }

    /**
     * Calculates business outcomes from simulation results.
     *
     * @param results the agent results
     * @param interactions the recorded interactions
     * @param transactions the recorded transactions
     * @return calculated business outcomes
     */
    private BusinessOutcomes calculateBusinessOutcomes(
            List<AgentResult> results,
            List<AgentInteraction> interactions,
            List<SkillTransaction> transactions) {

        int ideasQualified = (int) results.stream()
            .filter(AgentResult::success)
            .filter(r -> r.output() != null && r.output().length() > 100)
            .count();

        int mvpsBuilt = (int) results.stream()
            .filter(AgentResult::success)
            .filter(r -> r.getSkillCount() >= 1)
            .count();

        int skillsCreated = (int) results.stream()
            .flatMap(r -> r.skillInvocations().stream())
            .filter(SkillInvocation::success)
            .map(SkillInvocation::skillId)
            .distinct()
            .count();

        double revenueGenerated = transactions.stream()
            .filter(SkillTransaction::success)
            .mapToDouble(SkillTransaction::price)
            .sum();

        int partnerships = (int) interactions.stream()
            .filter(i -> i.interactionType() == InteractionType.COLLABORATION)
            .map(AgentInteraction::fromAgent)
            .distinct()
            .count();

        // Estimate time saved based on successful skill invocations
        double timeSaved = results.stream()
            .filter(AgentResult::success)
            .mapToDouble(r -> r.getSkillCount() * 0.5) // 0.5 hours per skill
            .sum();

        return new BusinessOutcomes(
            ideasQualified,
            mvpsBuilt,
            skillsCreated,
            revenueGenerated,
            partnerships,
            timeSaved
        );
    }

    /**
     * Calculates token analysis from agent results.
     *
     * @param results the agent results
     * @return token analysis
     */
    private TokenAnalysis calculateTokenAnalysis(List<AgentResult> results) {
        int yamlTokens = 0;
        int xmlTokens = 0;

        for (AgentResult result : results) {
            if (result.output() != null) {
                // Estimate tokens: ~4 characters per token for English
                int outputTokens = result.output().length() / 4;
                yamlTokens += outputTokens;
                // XML is typically ~40% larger in token count
                xmlTokens += (int) (outputTokens * 1.4);
            }

            for (SkillInvocation invocation : result.skillInvocations()) {
                if (invocation.input() != null) {
                    yamlTokens += invocation.input().length() / 4;
                    xmlTokens += (int) (invocation.input().length() / 4 * 1.4);
                }
                if (invocation.output() != null) {
                    yamlTokens += invocation.output().length() / 4;
                    xmlTokens += (int) (invocation.output().length() / 4 * 1.4);
                }
            }
        }

        return new TokenAnalysis(yamlTokens, xmlTokens);
    }

    /**
     * Returns the number of registered agents available for simulation.
     *
     * @return the count of registered agents
     */
    public static int getRegisteredAgentCount() {
        return AGENT_REGISTRY.size();
    }

    /**
     * Returns the IDs of all registered agents.
     *
     * @return unmodifiable collection of agent IDs
     */
    public static Collection<String> getRegisteredAgentIds() {
        return AGENT_REGISTRY.keySet();
    }
}
