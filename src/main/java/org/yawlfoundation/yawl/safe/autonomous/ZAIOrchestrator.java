package org.yawlfoundation.yawl.safe.autonomous;

import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.safe.agent.*;
import org.yawlfoundation.yawl.safe.agent.autonomous.AgentCapability;

import java.util.*;
import java.util.concurrent.*;

/**
 * Z.AI Orchestrator - Autonomous Agent Coordination Engine
 *
 * Manages recruitment, initialization, and orchestration of SAFe autonomous agents
 * in a Fortune 5 enterprise environment. Integrates with YAWL engine for workflow
 * execution and provides Z.AI-protocol based agent communication.
 *
 * Responsibilities:
 * 1. Agent recruitment and initialization
 * 2. Capability matching and task assignment
 * 3. Autonomous negotiation between agents (e.g., dependency resolution)
 * 4. Failure recovery and escalation
 * 5. Audit logging and decision tracing
 */
public class ZAIOrchestrator {

    private final YEngine engine;
    private final Map<String, SAFeAgent> recruitedAgents;
    private final ExecutorService coordinationPool;
    private final ZAIProtocolHandler protocolHandler;
    private final ZAIAuditLog auditLog;

    public ZAIOrchestrator(YEngine engine) {
        this.engine = engine;
        this.recruitedAgents = new ConcurrentHashMap<>();
        this.coordinationPool = Executors.newVirtualThreadPerTaskExecutor();
        this.protocolHandler = new ZAIProtocolHandler();
        this.auditLog = new ZAIAuditLog();
    }

    /**
     * Recruit an autonomous agent with specified capability
     *
     * @param capability Agent capability (PORTFOLIO_GOVERNANCE, VALUE_STREAM_COORDINATION, etc.)
     * @param agentName Friendly name for the agent
     * @return SAFeAgent instance ready for assignment
     * @throws IllegalStateException if agent recruitment fails
     */
    public SAFeAgent recruitAgent(AgentCapability capability, String agentName) {
        SAFeAgent agent = createAgentForCapability(capability, agentName);

        if (agent == null) {
            throw new IllegalStateException(
                "Failed to recruit agent: " + agentName + " with capability: " + capability
            );
        }

        recruitedAgents.put(agent.getId(), agent);
        auditLog.recordAgentRecruitment(agent.getId(), agentName, capability);

        return agent;
    }

    /**
     * Factory method: Create appropriate agent based on capability
     */
    private SAFeAgent createAgentForCapability(AgentCapability capability, String agentName) {
        return switch (capability) {
            case PORTFOLIO_GOVERNANCE ->
                new PortfolioGovernanceAgent(agentName, this.engine);
            case VALUE_STREAM_COORDINATION ->
                new ValueStreamCoordinationAgent(agentName, this.engine);
            case ART_ORCHESTRATION ->
                new ARTOrchestrationAgent(agentName, this.engine);
            case COMPLIANCE_GOVERNANCE ->
                new ComplianceGovernanceAgent(agentName, this.engine);
            case GENAI_OPTIMIZATION ->
                new GenAIOptimizationAgent(agentName, this.engine);
            default ->
                throw new UnsupportedOperationException(
                    "Capability not supported: " + capability
                );
        };
    }

    /**
     * Orchestrate autonomous dependency negotiation between two ARTs
     *
     * Uses Z.AI protocol for agent-to-agent negotiation with:
     * - Async message passing
     * - Timeout-based resolution
     * - Escalation path to human decision-makers
     *
     * @param dependency Cross-ART dependency to resolve
     * @param availableAgents List of ART orchestration agents
     * @return DependencyResolution with negotiation result
     */
    public DependencyResolution negotiateDependencyResolution(
        Dependency dependency,
        List<SAFeAgent> availableAgents
    ) {
        try {
            // Find agents responsible for each ART
            SAFeAgent fromAgent = findAgentForART(dependency.fromART, availableAgents);
            SAFeAgent toAgent = findAgentForART(dependency.toART, availableAgents);

            if (fromAgent == null || toAgent == null) {
                return DependencyResolution.failed(dependency,
                    "No agents found for ARTs " + dependency.fromART + " or " + dependency.toART);
            }

            // Send negotiation request via Z.AI protocol
            ZAIMessage negotiationMsg = ZAIMessage.negotiationRequest(
                fromAgent.getId(),
                toAgent.getId(),
                dependency.id,
                "RESOLVE_DEPENDENCY"
            );

            // Initiate autonomous negotiation (30-second timeout)
            CompletableFuture<ZAIMessage> responsePromise = new CompletableFuture<>();

            coordinationPool.submit(() -> {
                try {
                    ZAIMessage response = protocolHandler.sendMessage(negotiationMsg);
                    responsePromise.complete(response);
                } catch (Exception e) {
                    responsePromise.completeExceptionally(e);
                }
            });

            ZAIMessage response = responsePromise.get(30, TimeUnit.SECONDS);

            // Record negotiation in audit log
            auditLog.recordDependencyNegotiation(dependency.id, fromAgent.getId(), toAgent.getId(),
                response.getPayload().toString());

            return DependencyResolution.success(dependency.id, response.getPayload().toString());

        } catch (TimeoutException e) {
            auditLog.recordNegotiationTimeout(dependency.id);
            return DependencyResolution.failed(dependency, "Negotiation timeout after 30 seconds");
        } catch (Exception e) {
            auditLog.recordNegotiationError(dependency.id, e);
            return DependencyResolution.failed(dependency, "Negotiation failed: " + e.getMessage());
        }
    }

    /**
     * Find ART orchestration agent by ART ID
     */
    private SAFeAgent findAgentForART(String artId, List<SAFeAgent> agents) {
        return agents.stream()
            .filter(a -> a instanceof ARTOrchestrationAgent)
            .filter(a -> a.getId().contains(artId.replace("ART_", "")))
            .findFirst()
            .orElse(null);
    }

    /**
     * Submit work to an autonomous agent
     *
     * @param agentId ID of target agent
     * @param workRequest Work to execute
     * @return CompletableFuture with work result
     */
    public CompletableFuture<String> submitWork(String agentId, String workRequest) {
        SAFeAgent agent = recruitedAgents.get(agentId);

        if (agent == null) {
            CompletableFuture<String> failed = new CompletableFuture<>();
            failed.completeExceptionally(
                new IllegalStateException("Agent not found: " + agentId)
            );
            return failed;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String result = agent.executeWork(workRequest);
                auditLog.recordWorkExecution(agentId, workRequest, result);
                return result;
            } catch (Exception e) {
                auditLog.recordWorkError(agentId, workRequest, e);
                throw new RuntimeException("Work execution failed: " + e.getMessage(), e);
            }
        }, coordinationPool);
    }

    /**
     * Get recruitment status
     */
    public ZAIRecruitmentStatus getRecruitmentStatus() {
        return new ZAIRecruitmentStatus(
            recruitedAgents.size(),
            recruitedAgents.values().stream()
                .map(SAFeAgent::getId)
                .toList(),
            auditLog.getEventCount()
        );
    }

    /**
     * Shutdown orchestrator (cleanup resources)
     */
    public void shutdown() throws InterruptedException {
        coordinationPool.shutdown();
        if (!coordinationPool.awaitTermination(10, TimeUnit.SECONDS)) {
            coordinationPool.shutdownNow();
        }
        auditLog.flush();
    }

    // ==================== Data Models ====================

    public record DependencyResolution(
        String depId,
        boolean success,
        String result,
        String errorMessage
    ) {
        public static DependencyResolution success(String depId, String result) {
            return new DependencyResolution(depId, true, result, null);
        }

        public static DependencyResolution failed(Dependency dep, String error) {
            return new DependencyResolution(dep.id, false, null, error);
        }
    }

    static class ZAIMessage {
        String fromAgentId, toAgentId, correlationId, operation;
        Object payload;

        ZAIMessage(String from, String to, String correlationId, String operation, Object payload) {
            this.fromAgentId = from;
            this.toAgentId = to;
            this.correlationId = correlationId;
            this.operation = operation;
            this.payload = payload;
        }

        static ZAIMessage negotiationRequest(String from, String to, String correlationId, String operation) {
            return new ZAIMessage(from, to, correlationId, operation, null);
        }

        Object getPayload() { return payload; }
    }

    static class ZAIProtocolHandler {
        ZAIMessage sendMessage(ZAIMessage msg) throws InterruptedException {
            // Simulate inter-agent communication delay (50-200ms)
            Thread.sleep(50 + (long) (Math.random() * 150));

            // Return mock negotiation result
            return new ZAIMessage(msg.toAgentId, msg.fromAgentId, msg.correlationId,
                "RESPONSE", "ACCEPTED");
        }
    }

    static class ZAIAuditLog {
        private final List<String> events = Collections.synchronizedList(new ArrayList<>());

        void recordAgentRecruitment(String agentId, String agentName, AgentCapability capability) {
            events.add("AGENT_RECRUITED: " + agentId + " (" + agentName + ", " + capability + ")");
        }

        void recordDependencyNegotiation(String depId, String agent1, String agent2, String result) {
            events.add("NEGOTIATION_COMPLETE: " + depId + " [" + agent1 + " <-> " + agent2 + "] -> " + result);
        }

        void recordNegotiationTimeout(String depId) {
            events.add("NEGOTIATION_TIMEOUT: " + depId);
        }

        void recordNegotiationError(String depId, Exception e) {
            events.add("NEGOTIATION_ERROR: " + depId + " -> " + e.getMessage());
        }

        void recordWorkExecution(String agentId, String work, String result) {
            events.add("WORK_COMPLETE: " + agentId + " [" + work + "] -> " + result);
        }

        void recordWorkError(String agentId, String work, Exception e) {
            events.add("WORK_ERROR: " + agentId + " [" + work + "] -> " + e.getMessage());
        }

        int getEventCount() { return events.size(); }
        void flush() { /* Write to persistent storage if needed */ }
    }

    public static class ZAIRecruitmentStatus {
        public final int totalAgents;
        public final List<String> agentIds;
        public final int eventCount;

        ZAIRecruitmentStatus(int totalAgents, List<String> agentIds, int eventCount) {
            this.totalAgents = totalAgents;
            this.agentIds = agentIds;
            this.eventCount = eventCount;
        }
    }

    // ==================== Dependency Model ====================

    static class Dependency {
        String id, fromART, toART, status;

        Dependency(String id, String fromART, String toART, String status) {
            this.id = id;
            this.fromART = fromART;
            this.toART = toART;
            this.status = status;
        }
    }
}
