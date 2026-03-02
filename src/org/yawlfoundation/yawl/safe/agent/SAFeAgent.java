package org.yawlfoundation.yawl.safe.agent;

import org.yawlfoundation.yawl.engine.YEngine;

import java.util.UUID;

/**
 * Base class for SAFe autonomous agents in the self-play design loop.
 *
 * <p>Provides lifecycle management, agent identity, and work execution
 * for autonomous SAFe agents operating at portfolio, ART, and team levels.
 *
 * <p>Subclasses implement domain-specific work execution:
 * - GenAIOptimizationAgent: LLM-driven WSJF prioritization
 * - PortfolioGovernanceAgent: portfolio investment decisions
 * - ARTOrchestrationAgent: ART dependency resolution
 * - ComplianceGovernanceAgent: regulatory and policy enforcement
 * - ValueStreamCoordinationAgent: value stream optimization
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public abstract class SAFeAgent {

    private final String id;
    private final String name;
    protected final YEngine engine;

    /**
     * Initialize SAFe agent with name and engine reference.
     *
     * @param name human-readable agent name
     * @param engine YAWL engine for workflow access
     */
    protected SAFeAgent(String name, YEngine engine) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Agent name must not be blank");
        }
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.engine = engine;
    }

    /**
     * Get unique agent identifier.
     *
     * @return UUID-based agent ID
     */
    public String getId() {
        return id;
    }

    /**
     * Get human-readable agent name.
     *
     * @return agent name
     */
    public String getName() {
        return name;
    }

    /**
     * Execute work request for this agent.
     *
     * <p>Subclasses interpret the work request string and return a result.
     * Work request format: "OPERATION:payload"
     *
     * @param workRequest encoded work request
     * @return result JSON string
     */
    public abstract String executeWork(String workRequest);
}
