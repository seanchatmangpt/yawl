package org.yawlfoundation.yawl.safe.agent.autonomous;

/**
 * Agent capability types for SAFe autonomous agent recruitment.
 *
 * <p>Maps to the 5 SAFe enterprise agent roles that participate
 * in the self-play design loop via ZAIOrchestrator.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public enum AgentCapability {

    /** GenAI optimization: LLM-driven WSJF prioritization and delay prediction */
    GENAI_OPTIMIZATION,

    /** Portfolio governance: investment allocation and epic prioritization */
    PORTFOLIO_GOVERNANCE,

    /** ART orchestration: dependency resolution and PI objective negotiation */
    ART_ORCHESTRATION,

    /** Compliance governance: regulatory, security, and policy enforcement */
    COMPLIANCE_GOVERNANCE,

    /** Value stream coordination: flow optimization and feedback loops */
    VALUE_STREAM_COORDINATION
}
