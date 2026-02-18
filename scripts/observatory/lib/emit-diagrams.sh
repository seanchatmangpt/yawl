#!/usr/bin/env bash
# ==========================================================================
# emit-diagrams.sh — Generates all Mermaid diagram files
#
# Each diagram is regenerated from scratch using data from $FACTS_DIR.
# Sources util.sh for constants and helpers.
# ==========================================================================

# ── 10-maven-reactor.mmd ─────────────────────────────────────────────────
emit_reactor_diagram() {
    local out="$DIAGRAMS_DIR/10-maven-reactor.mmd"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting diagrams/10-maven-reactor.mmd ..."

    local reactor_json="$FACTS_DIR/reactor.json"

    {
        echo "%%{ init: { 'theme': 'base', 'themeVariables': { 'primaryColor': '#2d3748' } } }%%"
        echo "graph TD"
        echo "    classDef parent fill:#4a5568,stroke:#2d3748,color:#fff"
        echo "    classDef fullshared fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef pkgscoped fill:#3182ce,stroke:#2b6cb0,color:#fff"
        echo "    classDef infra fill:#38a169,stroke:#2f855a,color:#fff"
        echo ""
        echo "    PARENT[yawl-parent<br/>6.0.0-Alpha]:::parent"
        echo ""

        local modules
        modules=$(discover_modules)

        while IFS= read -r mod; do
            [[ -z "$mod" ]] && continue
            local pom="$REPO_ROOT/$mod/pom.xml"
            local src_dir
            src_dir=$(sed -n 's/.*<sourceDirectory>\([^<]*\)<\/sourceDirectory>.*/\1/p' "$pom" 2>/dev/null | head -1 || echo "")
            local class="pkgscoped"
            if [[ "$src_dir" == "../src" ]]; then
                class="fullshared"
            fi
            local display="${mod}"
            echo "    ${mod//[-]/_}[${display}]:::${class}"
        done <<< "$modules"

        echo ""
        echo "    %% Reactor ordering (parent -> all modules)"
        while IFS= read -r mod; do
            [[ -z "$mod" ]] && continue
            echo "    PARENT --> ${mod//[-]/_}"
        done <<< "$modules"

        echo ""
        echo "    %% Inter-module dependencies"
        if [[ -f "$reactor_json" ]]; then
            grep '"from"' "$reactor_json" 2>/dev/null | while IFS= read -r line; do
                local from to
                from=$(echo "$line" | sed -n 's/.*"from":"\([^"]*\)".*/\1/p')
                to=$(echo "$line" | sed -n 's/.*"to":"\([^"]*\)".*/\1/p')
                [[ -z "$from" || -z "$to" ]] && continue
                echo "    ${from//[-]/_} --> ${to//[-]/_}"
            done
        fi

        echo ""
        echo "    %% Legend"
        echo "    subgraph Legend"
        echo "        L1[Full Shared ../src]:::fullshared"
        echo "        L2[Package Scoped]:::pkgscoped"
        echo "    end"
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_reactor_diagram" "$op_elapsed"
}

# ── 60-mcp-architecture.mmd ───────────────────────────────────────────────
emit_mcp_architecture_diagram() {
    local out="$DIAGRAMS_DIR/60-mcp-architecture.mmd"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting diagrams/60-mcp-architecture.mmd ..."

    {
        echo "%%{ init: { 'theme': 'base', 'themeVariables': { 'primaryColor': '#3182ce' } } }%%"
        echo "graph TB"
        echo "    classDef client fill:#4a5568,stroke:#2d3748,color:#fff"
        echo "    classDef server fill:#3182ce,stroke:#2b6cb0,color:#fff"
        echo "    classDef transport fill:#805ad5,stroke:#6b46c1,color:#fff"
        echo "    classDef capability fill:#38a169,stroke:#2f855a,color:#fff"
        echo "    classDef engine fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef zai fill:#d69e2e,stroke:#b7791f,color:#fff"
        echo ""

        echo "    subgraph Clients[\"MCP Clients\"]"
        echo "        CLAUDE[Claude Desktop]:::client"
        echo "        CURSOR[Cursor IDE]:::client"
        echo "        CUSTOM[Custom MCP Client]:::client"
        echo "    end"
        echo ""

        echo "    subgraph Transport[\"Transport Layer\"]"
        echo "        STDIO[STDIO Transport<br/>Official MCP SDK]:::transport"
        echo "        JSONRPC[JSON-RPC 2.0<br/>Message Framing]:::transport"
        echo "    end"
        echo ""

        echo "    subgraph MCPServer[\"YawlMcpServer v5.2\"]"
        echo "        SERVER[YawlMcpServer<br/>MCP SDK 0.17.2]:::server"
        echo "        CAPABILITIES[ServerCapabilities]:::capability"
        echo "    end"
        echo ""

        echo "    subgraph Capabilities[\"MCP Capabilities\"]"
        echo "        TOOLS[\"15+ Tools<br/>Workflow Operations\"]:::capability"
        echo "        RESOURCES[\"3 Resources<br/>yawl:// URIs\"]:::capability"
        echo "        PROMPTS[\"4 Prompts<br/>Analysis Guides\"]:::capability"
        echo "        COMPLETIONS[\"3 Completions<br/>ID Autocomplete\"]:::capability"
        echo "    end"
        echo ""

        echo "    subgraph YAWLEngine[\"YAWL Engine\"]"
        echo "        INTERFACEB[InterfaceB<br/>Workflow Operations]:::engine"
        echo "        ENGINE[YEngine<br/>Petri Net Runtime]:::engine"
        echo "    end"
        echo ""

        echo "    subgraph ZAI[\"Z.AI Integration\"]"
        echo "        ZAISERVICE[ZaiFunctionService<br/>GLM-4]:::zai"
        echo "        ZAIENV[ZHIPU_API_KEY]:::zai"
        echo "    end"
        echo ""

        echo "    CLAUDE --> STDIO"
        echo "    CURSOR --> STDIO"
        echo "    CUSTOM --> STDIO"
        echo "    STDIO --> JSONRPC"
        echo "    JSONRPC --> SERVER"
        echo "    SERVER --> CAPABILITIES"
        echo "    CAPABILITIES --> TOOLS"
        echo "    CAPABILITIES --> RESOURCES"
        echo "    CAPABILITIES --> PROMPTS"
        echo "    CAPABILITIES --> COMPLETIONS"
        echo "    TOOLS --> INTERFACEB"
        echo "    RESOURCES --> INTERFACEB"
        echo "    INTERFACEB --> ENGINE"
        echo "    ZAIENV -.->|configures| ZAISERVICE"
        echo "    ZAISERVICE -.->|enhances| TOOLS"

    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_mcp_architecture_diagram" "$op_elapsed"
}

# ── 65-a2a-topology.mmd ─────────────────────────────────────────────────────
emit_a2a_topology_diagram() {
    local out="$DIAGRAMS_DIR/65-a2a-topology.mmd"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting diagrams/65-a2a-topology.mmd ..."

    {
        echo "%%{ init: { 'theme': 'base', 'themeVariables': { 'primaryColor': '#805ad5' } } }%%"
        echo "graph TB"
        echo "    classDef agent fill:#805ad5,stroke:#6b46c1,color:#fff"
        echo "    classDef yawl fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef client fill:#3182ce,stroke:#2b6cb0,color:#fff"
        echo "    classDef auth fill:#d69e2e,stroke:#b7791f,color:#fff"
        echo "    classDef transport fill:#38a169,stroke:#2f855a,color:#fff"
        echo ""

        echo "    subgraph ExternalAgents[\"A2A Agent Ecosystem\"]"
        echo "        AGENT1[Planning Agent]:::client"
        echo "        AGENT2[Execution Agent]:::client"
        echo "        AGENT3[Monitoring Agent]:::client"
        echo "        AGENT4[Human Agent]:::client"
        echo "    end"
        echo ""

        echo "    subgraph A2ATransport[\"A2A REST Protocol\"]"
        echo "        HTTP[HTTP/1.1 Server<br/>Port 8081]:::transport"
        echo "        REST[REST Handler<br/>io.a2a.transport.rest]:::transport"
        echo "    end"
        echo ""

        echo "    subgraph YawlA2A[\"YawlA2AServer v5.2\"]"
        echo "        A2ASERVER[YawlA2AServer]:::agent"
        echo "        EXECUTOR[YawlAgentExecutor]:::agent"
        echo "        ADAPTER[YawlEngineAdapter]:::agent"
        echo "    end"
        echo ""

        echo "    subgraph Discovery[\"Agent Discovery\"]"
        echo "        AGENTCARD[AgentCard<br/>JSON-LD]:::agent"
        echo "        WELLKNOWN[/.well-known/agent.json]:::transport"
        echo "    end"
        echo ""

        echo "    subgraph Skills[\"A2A Skills\"]"
        echo "        SKILL_LAUNCH[launch_workflow]:::yawl"
        echo "        SKILL_QUERY[query_workflows]:::yawl"
        echo "        SKILL_MANAGE[manage_workitems]:::yawl"
        echo "        SKILL_CANCEL[cancel_workflow]:::yawl"
        echo "    end"
        echo ""

        echo "    subgraph Authentication[\"Authentication\"]"
        echo "        AUTHPROV[CompositeAuthenticationProvider]:::auth"
        echo "        MTLS[mTLS/SPIFFE]:::auth"
        echo "        JWT[JWT Bearer]:::auth"
        echo "        APIKEY[API Key]:::auth"
        echo "    end"
        echo ""

        echo "    subgraph YAWLEngine[\"YAWL Engine\"]"
        echo "        INTERFACEB2[InterfaceB]:::yawl"
        echo "        ENGINE2[YEngine]:::yawl"
        echo "    end"
        echo ""

        echo "    AGENT1 --> HTTP"
        echo "    AGENT2 --> HTTP"
        echo "    AGENT3 --> HTTP"
        echo "    AGENT4 --> HTTP"
        echo "    HTTP --> REST"
        echo "    REST --> A2ASERVER"
        echo "    WELLKNOWN --> AGENTCARD"
        echo "    AGENTCARD -.->|advertises| A2ASERVER"
        echo "    A2ASERVER --> AUTHPROV"
        echo "    AUTHPROV --> MTLS"
        echo "    AUTHPROV --> JWT"
        echo "    AUTHPROV --> APIKEY"
        echo "    A2ASERVER --> EXECUTOR"
        echo "    EXECUTOR --> ADAPTER"
        echo "    EXECUTOR --> SKILL_LAUNCH"
        echo "    EXECUTOR --> SKILL_QUERY"
        echo "    EXECUTOR --> SKILL_MANAGE"
        echo "    EXECUTOR --> SKILL_CANCEL"
        echo "    SKILL_LAUNCH --> INTERFACEB2"
        echo "    SKILL_QUERY --> INTERFACEB2"
        echo "    SKILL_MANAGE --> INTERFACEB2"
        echo "    SKILL_CANCEL --> INTERFACEB2"
        echo "    INTERFACEB2 --> ENGINE2"

    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_a2a_topology_diagram" "$op_elapsed"
}

# ── 70-agent-capabilities.mmd ───────────────────────────────────────────────
emit_agent_capabilities_diagram() {
    local out="$DIAGRAMS_DIR/70-agent-capabilities.mmd"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting diagrams/70-agent-capabilities.mmd ..."

    {
        echo "%%{ init: { 'theme': 'base', 'themeVariables': { 'primaryColor': '#d69e2e' } } }%%"
        echo "graph LR"
        echo "    classDef mcp fill:#3182ce,stroke:#2b6cb0,color:#fff"
        echo "    classDef a2a fill:#805ad5,stroke:#6b46c1,color:#fff"
        echo "    classDef autonomous fill:#d69e2e,stroke:#b7791f,color:#fff"
        echo "    classDef yawl fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef shared fill:#38a169,stroke:#2f855a,color:#fff"
        echo ""

        echo "    subgraph MCPCapabilities[\"MCP Capabilities\"]"
        echo "        MCP_TOOLS[15+ Tools]:::mcp"
        echo "        MCP_RESOURCES[3 Resources]:::mcp"
        echo "        MCP_PROMPTS[4 Prompts]:::mcp"
        echo "        MCP_COMPLETE[3 Completions]:::mcp"
        echo "    end"
        echo ""

        echo "    subgraph A2ACapabilities[\"A2A Capabilities\"]"
        echo "        A2A_SKILLS[4 Skills]:::a2a"
        echo "        A2A_DISCOVERY[Agent Card]:::a2a"
        echo "        A2A_AUTH[Multi-Scheme Auth]:::a2a"
        echo "        A2A_REST[REST Endpoints]:::a2a"
        echo "    end"
        echo ""

        echo "    subgraph SharedCapabilities[\"Shared YAWL Engine\"]"
        echo "        ENGINE_API[InterfaceB API]:::shared"
        echo "        SPEC_MGMT[Spec Management]:::shared"
        echo "        CASE_MGMT[Case Management]:::shared"
        echo "        WORKITEM_MGMT[Work Item Mgmt]:::shared"
        echo "    end"
        echo ""

        echo "    subgraph ZAICapabilities[\"Z.AI Reasoning\"]"
        echo "        ZAI_FUNCTIONS[Function Calling]:::autonomous"
        echo "        ZAI_NLU[Natural Language]:::autonomous"
        echo "        ZAI_CONTEXT[Context Mgmt]:::autonomous"
        echo "    end"
        echo ""

        echo "    MCP_TOOLS --> ENGINE_API"
        echo "    MCP_RESOURCES --> CASE_MGMT"
        echo "    A2A_SKILLS --> ENGINE_API"
        echo "    A2A_DISCOVERY -.->|advertises| SPEC_MGMT"
        echo "    ZAI_FUNCTIONS -.->|enhances| MCP_TOOLS"
        echo "    ZAI_NLU -.->|enhances| A2A_SKILLS"

    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_agent_capabilities_diagram" "$op_elapsed"
}

# ── 75-protocol-sequences.mmd ───────────────────────────────────────────────
emit_protocol_sequences_diagram() {
    local out="$DIAGRAMS_DIR/75-protocol-sequences.mmd"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting diagrams/75-protocol-sequences.mmd ..."

    {
        echo "%%{ init: { 'theme': 'base' } }%%"
        echo "sequenceDiagram"
        echo "    autonumber"
        echo "    participant C as MCP/A2A Client"
        echo "    participant M as YawlMcpServer"
        echo "    participant A as YawlA2AServer"
        echo "    participant Z as ZaiService"
        echo "    participant Y as YAWL Engine"
        echo ""

        echo "    rect rgb(49, 130, 206, 0.1)"
        echo "        Note over C,Y: MCP Tool Invocation"
        echo "        C->>M: initialize request"
        echo "        M->>Y: connect(session)"
        echo "        Y-->>M: sessionHandle"
        echo "        M-->>C: capabilities"
        echo "        C->>M: tools/call launch_case"
        echo "        M->>Y: launchCase(specID)"
        echo "        Y-->>M: caseId"
        echo "        M-->>C: result"
        echo "    end"
        echo ""

        echo "    rect rgb(128, 90, 213, 0.1)"
        echo "        Note over C,Y: A2A Message Flow"
        echo "        C->>A: GET /.well-known/agent.json"
        echo "        A-->>C: AgentCard"
        echo "        C->>A: POST / (message)"
        echo "        Note right of A: Validate Auth"
        echo "        A->>Y: Execute operation"
        echo "        Y-->>A: Result"
        echo "        A-->>C: Response"
        echo "    end"
        echo ""

        echo "    rect rgb(214, 158, 46, 0.1)"
        echo "        Note over C,Y: Z.AI Enhanced Flow"
        echo "        C->>M: tools/call (NL)"
        echo "        M->>Z: processWithFunctions()"
        echo "        Z->>Y: Call operation"
        echo "        Y-->>Z: Result"
        echo "        Z-->>M: Formatted"
        echo "        M-->>C: Human-readable"
        echo "    end"

    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_protocol_sequences_diagram" "$op_elapsed"
}

# ── 50-risk-surfaces.mmd (FMEA Risk Analysis) ─────────────────────────────
emit_fmea_risk_diagram() {
    local out="$DIAGRAMS_DIR/50-risk-surfaces.mmd"
    log_info "Emitting diagrams/50-risk-surfaces.mmd ..."

    {
        echo "%%{ init: { 'theme': 'base', 'themeVariables': { 'primaryColor': '#e53e3e' } } }%%"
        echo "graph TD"
        echo "    classDef risk fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef warning fill:#d69e2e,stroke:#b7791f,color:#fff"
        echo "    classDef safe fill:#38a169,stroke:#2f855a,color:#fff"
        echo ""

        # Risk Categories
        echo "    subgraph RiskCategories[\"Risk Surface Analysis\"]"
        echo "        R1[Build System<br/>Maven Dependencies]:::risk"
        echo "        R2[Code Quality<br/>Static Analysis]:::risk"
        echo "        R3[Test Coverage<br/>JaCoCo Metrics]:::risk"
        echo "        R4[Integration<br/>MCP/A2A Protocols]:::risk"
        echo "        R5[Performance<br/>Memory/Time]:::warning"
        echo "    end"
        echo ""

        # Assessment Factors
        echo "    subgraph AssessmentFactors[\"Assessment Factors\"]"
        echo "        F1[Maven Version Conflicts]:::risk"
        echo "        F2[Deprecated API Usage]:::risk"
        echo "        F3[Uncovered Code Paths]:::risk"
        echo "        F4[Protocol Compatibility]:::warning"
        echo "        F5[Memory Leaks]:::risk"
        echo "        F6[Response Time]:::warning"
        echo "    end"
        echo ""

        # Risk Levels
        echo "    subgraph RiskLevels[\"Risk Levels (FMEA Scale)\"]"
        echo "        C1[Critical (9-10)<br/>System Failure]:::risk"
        echo "        C2[High (7-8)<br/>Feature Breakage]:::risk"
        echo "        C3[Medium (5-6)<br/>Degradation]:::warning"
        echo "        C4[Low (1-4)<br/>Minor Issues]:::safe"
        echo "    end"
        echo ""

        # Connections based on current state
        echo "    %% Current risk assessment"
        echo "    R1 --> F1"
        echo "    R2 --> F2"
        echo "    R3 --> F3"
        echo "    R4 --> F4"
        echo "    R5 --> F5"
        echo "    R5 --> F6"
        echo ""

        echo "    %% Risk level assignments"
        echo "    F1 --> C1"
        echo "    F2 --> C2"
        echo "    F3 --> C2"
        echo "    F4 --> C3"
        echo "    F5 --> C1"
        echo "    F6 --> C3"
        echo ""

        # Annotations
        echo "    %% FMEA Scale: Severity × Occurrence × Detection"
        echo "    %% Critical: 9-10 (Prevents system operation)"
        echo "    %% High: 7-8 (Major functionality impaired)"
        echo "    %% Medium: 5-6 (Partial functionality degraded)"
        echo "    %% Low: 1-4 (Minor cosmetic issues)"
        echo ""
        echo "    %% Last Updated: $(date '+%Y-%m-%d')"
        echo "    %% Data Source: Observatory v6 Analysis"

    } > "$out"
}

# ── Main dispatcher ──────────────────────────────────────────────────────
emit_all_diagrams() {
    timer_start
    record_memory "diagrams_start"
    emit_reactor_diagram
    emit_fmea_risk_diagram
    emit_mcp_architecture_diagram
    emit_a2a_topology_diagram
    emit_agent_capabilities_diagram
    emit_protocol_sequences_diagram
    DIAGRAMS_ELAPSED=$(timer_elapsed_ms)
    record_phase_timing "diagrams" "$DIAGRAMS_ELAPSED"
    log_ok "All diagrams emitted in ${DIAGRAMS_ELAPSED}ms"
}
