package org.yawlfoundation.yawl.integration.selfplay.model;

/**
 * The 7 known gaps in YAWL v6 that autonomous agents will design solutions for in v7.
 *
 * <p>Identified from codebase analysis:
 * <ol>
 *   <li>ASYNC_A2A_GOSSIP — sync HandoffRequestService is a throughput bottleneck</li>
 *   <li>MCP_SERVERS_SLACK_GITHUB_OBS — 3 MCP servers missing (Slack, GitHub, Observability)</li>
 *   <li>DETERMINISTIC_REPLAY_BLAKE3 — no replay-proof hash chain for decisions</li>
 *   <li>THREADLOCAL_YENGINE_PARALLELIZATION — ThreadLocal/ScopedValue YEngine for 30% test speedup</li>
 *   <li>SHACL_COMPLIANCE_SHAPES — SOX/GDPR/HIPAA compliance as RDF SHACL shapes</li>
 *   <li>BYZANTINE_CONSENSUS — pluggable Raft/Paxos for multi-agent consensus</li>
 *   <li>BURIED_ENGINES_MCP_A2A_WIRING — 4 discovered engines need MCP/A2A wiring</li>
 * </ol>
 */
public enum V7Gap {

    ASYNC_A2A_GOSSIP(
        "Async A2A gossip protocol",
        "Replace sync HandoffRequestService with async gossip-based A2A messaging"
    ),

    MCP_SERVERS_SLACK_GITHUB_OBS(
        "Missing MCP servers: Slack, GitHub, Observability",
        "Implement 3 new MCP servers for real-time case monitoring and CI/CD integration"
    ),

    DETERMINISTIC_REPLAY_BLAKE3(
        "Deterministic decision replay with Blake3 hash chain",
        "Add Blake3 receipt chain to all engine decisions for deterministic replay and audit"
    ),

    THREADLOCAL_YENGINE_PARALLELIZATION(
        "ThreadLocal YEngine for test parallelization",
        "Use Java 25 ScopedValue to bind YEngine per-thread, enabling 30% test speedup"
    ),

    SHACL_COMPLIANCE_SHAPES(
        "SHACL compliance shapes for SOX/GDPR/HIPAA",
        "Encode compliance rules as W3C SHACL shapes on top of existing RDF event model"
    ),

    BYZANTINE_CONSENSUS(
        "Byzantine consensus as pluggable strategy",
        "Add Raft/Paxos pluggable strategy for multi-agent agreement under Byzantine faults"
    ),

    BURIED_ENGINES_MCP_A2A_WIRING(
        "Buried blue-ocean engines need MCP/A2A wiring",
        "Wire TemporalForkEngine, EventDrivenAdaptationEngine, FootprintExtractor, OcedBridgeFactory to MCP/A2A"
    );

    public final String title;
    public final String description;

    V7Gap(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
