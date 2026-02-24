#!/usr/bin/env bash
set -euo pipefail
# ==========================================================================
# emit-integration-diagrams.sh — MCP/A2A/ZAI integration diagram generators
#
# Generates Mermaid diagrams for:
#   - 60-mcp-architecture.mmd    — Model Context Protocol architecture
#   - 65-a2a-topology.mmd        — Agent-to-Agent topology visualization
#   - 70-agent-capabilities.mmd  — Agent capability maps
#   - 75-protocol-sequences.mmd  — Protocol sequence diagrams
#
# Sources util.sh for constants and helpers.
# Part of: YAWL V6 Code Analysis Observatory
# ==========================================================================

# Ensure util.sh is sourced
if [[ -z "${REPO_ROOT:-}" ]]; then
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    source "${SCRIPT_DIR}/util.sh"
fi

# ── Directory constants for integration analysis ─────────────────────────────
INTEGRATION_ROOT="${REPO_ROOT}/src/org/yawlfoundation/yawl/integration"
MCP_ROOT="${INTEGRATION_ROOT}/mcp"
A2A_ROOT="${INTEGRATION_ROOT}/a2a"
ZAI_ROOT="${INTEGRATION_ROOT}/zai"
AUTONOMOUS_ROOT="${INTEGRATION_ROOT}/autonomous"

# ── 60-mcp-architecture.mmd ─────────────────────────────────────────────────
emit_mcp_architecture_diagram() {
    local out="$DIAGRAMS_DIR/60-mcp-architecture.mmd"
    log_info "Emitting diagrams/60-mcp-architecture.mmd ..."

    # Scan MCP directory for components
    local mcp_tools="" mcp_resources="" mcp_prompts="" mcp_completions=""

    if [[ -d "$MCP_ROOT/spec" ]]; then
        # Detect tool specifications
        mcp_tools=$(grep -l "Tool\|tool" "$MCP_ROOT/spec"/*.java 2>/dev/null | wc -l | tr -d ' ')
        # Detect prompt specifications
        mcp_prompts=$(grep -l "Prompt\|prompt" "$MCP_ROOT/spec"/*.java 2>/dev/null | wc -l | tr -d ' ')
    fi

    if [[ -d "$MCP_ROOT/resource" ]]; then
        mcp_resources=$(find "$MCP_ROOT/resource" -name "*.java" 2>/dev/null | wc -l | tr -d ' ')
    fi

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

        # Client layer
        echo "    subgraph Clients[\"MCP Clients\"]"
        echo "        CLAUDE[Claude Desktop]:::client"
        echo "        CURSOR[Cursor IDE]:::client"
        echo "        CUSTOM[Custom MCP Client]:::client"
        echo "    end"
        echo ""

        # Transport layer
        echo "    subgraph Transport[\"Transport Layer\"]"
        echo "        STDIO[STDIO Transport<br/>com.sun.net.httpserver]:::transport"
        echo "        JSONRPC[JSON-RPC 2.0<br/>Message Framing]:::transport"
        echo "    end"
        echo ""

        # MCP Server
        echo "    subgraph MCPServer[\"YawlMcpServer v5.2\"]"
        echo "        SERVER[YawlMcpServer<br/>Official MCP SDK 0.17.2]:::server"
        echo "        CAPABILITIES[ServerCapabilities<br/>tools,resources,prompts,completions,logging]:::capability"
        echo "    end"
        echo ""

        # Capabilities
        echo "    subgraph Capabilities[\"MCP Capabilities\"]"
        echo "        TOOLS[\"Tools (${mcp_tools:-15})<br/>launch_case, cancel_case,<br/>get_workitems, complete_workitem\"]:::capability"
        echo "        RESOURCES[\"Resources (${mcp_resources:-3})<br/>yawl://specifications,<br/>yawl://cases, yawl://workitems\"]:::capability"
        echo "        PROMPTS[\"Prompts (${mcp_prompts:-4})<br/>workflow_analysis,<br/>task_completion_guide\"]:::capability"
        echo "        COMPLETIONS[\"Completions (3)<br/>spec/workitem/case IDs\"]:::capability"
        echo "        LOGGING[\"Logging<br/>Structured MCP Notifications\"]:::capability"
        echo "    end"
        echo ""

        # YAWL Engine interface
        echo "    subgraph YAWLEngine[\"YAWL Engine\"]"
        echo "        INTERFACEB[InterfaceB_EnvironmentBasedClient<br/>Workflow Operations]:::engine"
        echo "        INTERFACEA[InterfaceA_EnvironmentBasedClient<br/>Specification Management]:::engine"
        echo "        ENGINE[YEngine<br/>Stateful Workflow Engine]:::engine"
        echo "    end"
        echo ""

        # Z.AI Integration (optional)
        echo "    subgraph ZAI[\"Z.AI Integration\"]"
        echo "        ZAISERVICE[ZaiFunctionService<br/>GLM-4 Function Calling]:::zai"
        echo "        ZAIHTTP[ZaiHttpClient<br/>HTTP/2 REST Client]:::zai"
        echo "        ZAIENV[ZHIPU_API_KEY<br/>Environment Variable]:::zai"
        echo "    end"
        echo ""

        # Connections: Clients -> Transport
        echo "    %% Client connections"
        echo "    CLAUDE --> STDIO"
        echo "    CURSOR --> STDIO"
        echo "    CUSTOM --> STDIO"
        echo ""

        # Connections: Transport -> Server
        echo "    STDIO --> JSONRPC"
        echo "    JSONRPC --> SERVER"
        echo ""

        # Connections: Server -> Capabilities
        echo "    SERVER --> CAPABILITIES"
        echo "    CAPABILITIES --> TOOLS"
        echo "    CAPABILITIES --> RESOURCES"
        echo "    CAPABILITIES --> PROMPTS"
        echo "    CAPABILITIES --> COMPLETIONS"
        echo "    CAPABILITIES --> LOGGING"
        echo ""

        # Connections: Capabilities -> Engine
        echo "    TOOLS --> INTERFACEB"
        echo "    TOOLS --> INTERFACEA"
        echo "    RESOURCES --> INTERFACEB"
        echo "    INTERFACEB --> ENGINE"
        echo "    INTERFACEA --> ENGINE"
        echo ""

        # Connections: Z.AI (conditional)
        echo "    %% Z.AI integration (optional via ZHIPU_API_KEY)"
        echo "    ZAIENV -.->|configures| ZAISERVICE"
        echo "    ZAISERVICE --> ZAIHTTP"
        echo "    ZAISERVICE -.->|enhances| TOOLS"
        echo ""

        # Protocol flow annotation
        echo "    %% Protocol: MCP 2024-11-05 Specification"
        echo "    %% Transport: STDIO with newline-delimited JSON-RPC"
        echo "    %% Auth: None (transport-level auth assumed)"

    } > "$out"
}

# ── 65-a2a-topology.mmd ─────────────────────────────────────────────────────
emit_a2a_topology_diagram() {
    local out="$DIAGRAMS_DIR/65-a2a-topology.mmd"
    log_info "Emitting diagrams/65-a2a-topology.mmd ..."

    # Scan for A2A skills and capabilities
    local a2a_skills=""
    if [[ -f "$A2A_ROOT/YawlA2AServer.java" ]]; then
        a2a_skills=$(grep -o 'id("[^"]*")' "$A2A_ROOT/YawlA2AServer.java" 2>/dev/null | \
            sed 's/id("//; s/")//' | tr '\n' ', ' | sed 's/,$//')
    fi

    # Scan auth providers
    local auth_providers=""
    if [[ -d "$A2A_ROOT/auth" ]]; then
        auth_providers=$(find "$A2A_ROOT/auth" -name "*Provider.java" 2>/dev/null | \
            xargs -I {} basename {} .java | tr '\n' ', ' | sed 's/,$//')
    fi

    {
        echo "%%{ init: { 'theme': 'base', 'themeVariables': { 'primaryColor': '#805ad5' } } }%%"
        echo "graph TB"
        echo "    classDef agent fill:#805ad5,stroke:#6b46c1,color:#fff"
        echo "    classDef yawl fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef client fill:#3182ce,stroke:#2b6cb0,color:#fff"
        echo "    classDef auth fill:#d69e2e,stroke:#b7791f,color:#fff"
        echo "    classDef transport fill:#38a169,stroke:#2f855a,color:#fff"
        echo ""

        # External A2A Agents
        echo "    subgraph ExternalAgents[\"A2A Agent Ecosystem\"]"
        echo "        AGENT1[Planning Agent<br/>workflow design]:::client"
        echo "        AGENT2[Execution Agent<br/>task automation]:::client"
        echo "        AGENT3[Monitoring Agent<br/>state tracking]:::client"
        echo "        AGENT4[Human Agent<br/>approval workflows]:::client"
        echo "    end"
        echo ""

        # Transport Layer
        echo "    subgraph A2ATransport[\"A2A REST Protocol\"]"
        echo "        HTTP[HTTP/1.1<br/>com.sun.net.httpserver]:::transport"
        echo "        REST[REST Handler<br/>io.a2a.transport.rest]:::transport"
        echo "    end"
        echo ""

        # YAWL A2A Server
        echo "    subgraph YawlA2A[\"YawlA2AServer v5.2\"]"
        echo "        A2ASERVER[YawlA2AServer<br/>Port 8081]:::agent"
        echo "        EXECUTOR[YawlAgentExecutor<br/>Message Processing]:::agent"
        echo "        ADAPTER[YawlEngineAdapter<br/>Protocol Translation]:::agent"
        echo "    end"
        echo ""

        # Agent Card (Discovery)
        echo "    subgraph Discovery[\"Agent Discovery\"]"
        echo "        AGENTCARD[AgentCard<br/>JSON-LD Descriptor]:::agent"
        echo "        WELLKNOWN[/.well-known/agent.json<br/>No Auth Required]:::transport"
        echo "    end"
        echo ""

        # Skills exposed
        echo "    subgraph Skills[\"A2A Skills\"]"
        echo "        SKILL_LAUNCH[launch_workflow<br/>Start workflow case]:::yawl"
        echo "        SKILL_QUERY[query_workflows<br/>List specs and cases]:::yawl"
        echo "        SKILL_MANAGE[manage_workitems<br/>Get/complete work items]:::yawl"
        echo "        SKILL_CANCEL[cancel_workflow<br/>Stop running case]:::yawl"
        echo "    end"
        echo ""

        # Authentication
        echo "    subgraph Authentication[\"Authentication Layer\"]"
        echo "        AUTHPROV[CompositeAuthenticationProvider]:::auth"
        echo "        MTLS[mTLS/SPIFFE<br/>X.509 SVID]:::auth"
        echo "        JWT[JWT Bearer<br/>HS256]:::auth"
        echo "        APIKEY[API Key<br/>HMAC-SHA256]:::auth"
        echo "    end"
        echo ""

        # YAWL Engine
        echo "    subgraph YAWLEngine[\"YAWL Engine\"]"
        echo "        INTERFACEB2[InterfaceB<br/>Workflow Operations]:::yawl"
        echo "        ENGINE2[YEngine<br/>Petri Net Runtime]:::yawl"
        echo "    end"
        echo ""

        # Z.AI Integration
        echo "    subgraph ZAI2[\"Z.AI Reasoning\"]"
        echo "        ZAI2SERVICE[ZaiFunctionService<br/>Natural Language Processing]:::auth"
        echo "    end"
        echo ""

        # Connections
        echo "    %% Agent connections to transport"
        echo "    AGENT1 --> HTTP"
        echo "    AGENT2 --> HTTP"
        echo "    AGENT3 --> HTTP"
        echo "    AGENT4 --> HTTP"
        echo ""

        echo "    HTTP --> REST"
        echo "    REST --> A2ASERVER"
        echo ""

        # Discovery flow
        echo "    %% Discovery (no auth)"
        echo "    WELLKNOWN --> AGENTCARD"
        echo "    AGENTCARD -.->|advertises| A2ASERVER"
        echo ""

        # Auth flow
        echo "    %% Authentication flow"
        echo "    A2ASERVER --> AUTHPROV"
        echo "    AUTHPROV --> MTLS"
        echo "    AUTHPROV --> JWT"
        echo "    AUTHPROV --> APIKEY"
        echo ""

        # Executor flow
        echo "    %% Execution flow"
        echo "    A2ASERVER --> EXECUTOR"
        echo "    EXECUTOR --> ADAPTER"
        echo "    EXECUTOR -.->|optional| ZAI2SERVICE"
        echo ""

        # Skills to engine
        echo "    %% Skills to engine"
        echo "    EXECUTOR --> SKILL_LAUNCH"
        echo "    EXECUTOR --> SKILL_QUERY"
        echo "    EXECUTOR --> SKILL_MANAGE"
        echo "    EXECUTOR --> SKILL_CANCEL"
        echo ""

        echo "    SKILL_LAUNCH --> INTERFACEB2"
        echo "    SKILL_QUERY --> INTERFACEB2"
        echo "    SKILL_MANAGE --> INTERFACEB2"
        echo "    SKILL_CANCEL --> INTERFACEB2"
        echo "    INTERFACEB2 --> ENGINE2"
        echo ""

        # Protocol annotation
        echo "    %% Protocol: A2A (Agent-to-Agent) v0.2.0"
        echo "    %% Transport: HTTP REST with JSON payloads"
        echo "    %% Auth: Multi-scheme (mTLS/JWT/API Key)"

    } > "$out"
}

# ── 70-agent-capabilities.mmd ───────────────────────────────────────────────
emit_agent_capabilities_diagram() {
    local out="$DIAGRAMS_DIR/70-agent-capabilities.mmd"
    log_info "Emitting diagrams/70-agent-capabilities.mmd ..."

    # Scan autonomous agents
    local agent_count=0
    local -a agent_names=()
    if [[ -d "$AUTONOMOUS_ROOT" ]]; then
        while IFS= read -r agent_file; do
            local name
            name=$(basename "$agent_file" .java)
            agent_names+=("$name")
            agent_count=$((agent_count + 1))
        done < <(find "$AUTONOMOUS_ROOT" -name "*Agent*.java" -type f 2>/dev/null | head -10)
    fi

    {
        echo "%%{ init: { 'theme': 'base', 'themeVariables': { 'primaryColor': '#d69e2e' } } }%%"
        echo "graph LR"
        echo "    classDef mcp fill:#3182ce,stroke:#2b6cb0,color:#fff"
        echo "    classDef a2a fill:#805ad5,stroke:#6b46c1,color:#fff"
        echo "    classDef autonomous fill:#d69e2e,stroke:#b7791f,color:#fff"
        echo "    classDef yawl fill:#e53e3e,stroke:#c53030,color:#fff"
        echo "    classDef shared fill:#38a169,stroke:#2f855a,color:#fff"
        echo ""

        # MCP Agent Capabilities
        echo "    subgraph MCPCapabilities[\"MCP Server Capabilities\"]"
        echo "        MCP_TOOLS[15+ Tools<br/>Workflow Operations]:::mcp"
        echo "        MCP_RESOURCES[3 Static Resources<br/>specs, cases, workitems]:::mcp"
        echo "        MCP_TEMPLATES[3 Resource Templates<br/>parameterized access]:::mcp"
        echo "        MCP_PROMPTS[4 Prompts<br/>Analysis Guides]:::mcp"
        echo "        MCP_COMPLETE[3 Completions<br/>ID Autocomplete]:::mcp"
        echo "    end"
        echo ""

        # A2A Agent Capabilities
        echo "    subgraph A2ACapabilities[\"A2A Server Capabilities\"]"
        echo "        A2A_SKILLS[4 Skills<br/>Workflow Actions]:::a2a"
        echo "        A2A_DISCOVERY[Agent Card<br/>JSON-LD Discovery]:::a2a"
        echo "        A2A_AUTH[Multi-Scheme Auth<br/>mTLS/JWT/API Key]:::a2a"
        echo "        A2A_REST[REST Endpoints<br/>/tasks, /message]:::a2a"
        echo "    end"
        echo ""

        # Autonomous Agents
        echo "    subgraph AutonomousAgents[\"Autonomous Agents (${agent_count})\"]"
        if [[ ${#agent_names[@]} -gt 0 ]]; then
            for agent in "${agent_names[@]}"; do
                local safe_name="${agent//[-]/_}"
                echo "        ${safe_name}[${agent}]:::autonomous"
            done
        else
            echo "        ORCHESTRATOR[WorkflowOrchestrator]:::autonomous"
            echo "        MONITOR[CaseMonitor]:::autonomous"
            echo "        SCHEDULER[TaskScheduler]:::autonomous"
        fi
        echo "    end"
        echo ""

        # Shared Capabilities (via YAWL Engine)
        echo "    subgraph SharedCapabilities[\"Shared YAWL Engine\"]"
        echo "        ENGINE_API[InterfaceB API<br/>Workflow Operations]:::shared"
        echo "        SPEC_MGMT[Specification Management<br/>Load/Unload/Query]:::shared"
        echo "        CASE_MGMT[Case Management<br/>Launch/Cancel/Query]:::shared"
        echo "        WORKITEM_MGMT[Work Item Management<br/>Checkout/Complete]:::shared"
        echo "    end"
        echo ""

        # Z.AI Reasoning
        echo "    subgraph ZAICapabilities[\"Z.AI Reasoning\"]"
        echo "        ZAI_FUNCTIONS[Function Calling<br/>GLM-4 Tool Use]:::autonomous"
        echo "        ZAI_NLU[Natural Language<br/>Intent Recognition]:::autonomous"
        echo "        ZAI_CONTEXT[Context Management<br/>Session Memory]:::autonomous"
        echo "    end"
        echo ""

        # Capability mappings
        echo "    %% MCP capabilities map to YAWL"
        echo "    MCP_TOOLS --> ENGINE_API"
        echo "    MCP_RESOURCES --> CASE_MGMT"
        echo "    MCP_TEMPLATES --> WORKITEM_MGMT"
        echo ""

        echo "    %% A2A capabilities map to YAWL"
        echo "    A2A_SKILLS --> ENGINE_API"
        echo "    A2A_DISCOVERY -.->|advertises| SPEC_MGMT"
        echo ""

        echo "    %% Autonomous agents use both protocols"
        if [[ ${#agent_names[@]} -gt 0 ]]; then
            for agent in "${agent_names[@]}"; do
                local safe_name="${agent//[-]/_}"
                echo "    ${safe_name} --> ENGINE_API"
            done
        else
            echo "    ORCHESTRATOR --> ENGINE_API"
            echo "    MONITOR --> CASE_MGMT"
            echo "    SCHEDULER --> WORKITEM_MGMT"
        fi
        echo ""

        echo "    %% Z.AI enhances all capabilities"
        echo "    ZAI_FUNCTIONS -.->|enhances| MCP_TOOLS"
        echo "    ZAI_NLU -.->|enhances| A2A_SKILLS"
        echo "    ZAI_CONTEXT -.->|enhances| AutonomousAgents"

    } > "$out"
}

# ── 75-protocol-sequences.mmd ───────────────────────────────────────────────
emit_protocol_sequences_diagram() {
    local out="$DIAGRAMS_DIR/75-protocol-sequences.mmd"
    log_info "Emitting diagrams/75-protocol-sequences.mmd ..."

    {
        echo "%%{ init: { 'theme': 'base' } }%%"
        echo "sequenceDiagram"
        echo "    autonumber"
        echo "    participant C as MCP/A2A Client"
        echo "    participant M as YawlMcpServer"
        echo "    participant A as YawlA2AServer"
        echo "    participant Z as ZaiFunctionService"
        echo "    participant Y as YAWL Engine"
        echo "    participant E as InterfaceB"
        echo ""

        # MCP Tool Invocation Sequence
        echo "    rect rgb(49, 130, 206, 0.1)"
        echo "        Note over C,E: MCP Tool Invocation Sequence"
        echo "        C->>M: initialize request"
        echo "        M->>E: connect(username, password)"
        echo "        E-->>M: sessionHandle"
        echo "        M-->>C: initialize response + capabilities"
        echo "        "
        echo "        C->>M: tools/call launch_case"
        echo "        M->>E: launchCase(specID, data, handle)"
        echo "        E->>Y: Create case instance"
        echo "        Y-->>E: caseId"
        echo "        E-->>M: caseId XML"
        echo "        M-->>C: tool result (caseId)"
        echo "    end"
        echo ""

        # A2A Message Sequence
        echo "    rect rgb(128, 90, 213, 0.1)"
        echo "        Note over C,E: A2A Message Sequence"
        echo "        C->>A: GET /.well-known/agent.json"
        echo "        A-->>C: AgentCard (no auth)"
        echo "        "
        echo "        C->>A: POST / (message)"
        echo "        Note right of A: Validate auth (mTLS/JWT/API Key)"
        echo "        A->>A: Authenticate request"
        echo "        A->>E: Execute workflow operation"
        echo "        E->>Y: Process request"
        echo "        Y-->>E: Result"
        echo "        E-->>A: Result"
        echo "        A-->>C: A2A response message"
        echo "    end"
        echo ""

        # Z.AI Enhanced Sequence
        echo "    rect rgb(214, 158, 46, 0.1)"
        echo "        Note over C,E: Z.AI Enhanced Sequence"
        echo "        C->>M: tools/call (natural language)"
        echo "        M->>Z: processWithFunctions(text)"
        echo "        Z->>Z: Extract intent + entities"
        echo "        Z->>Z: Select appropriate function"
        echo "        Z->>E: Call YAWL operation"
        echo "        E-->>Z: Result"
        echo "        Z->>Z: Format natural response"
        echo "        Z-->>M: Formatted response"
        echo "        M-->>C: tool result (human-readable)"
        echo "    end"
        echo ""

        # Error Handling Sequence
        echo "    rect rgb(229, 62, 62, 0.1)"
        echo "        Note over C,E: Error Handling Sequence"
        echo "        C->>M: tools/call invalid_case"
        echo "        M->>E: getCaseState(invalidId, handle)"
        echo "        E-->>M: failure XML"
        echo "        M->>M: Log error via MCP logging"
        echo "        M-->>C: tool error (isError: true)"
        echo "        "
        echo "        C->>A: POST / (expired JWT)"
        echo "        A->>A: Validate JWT"
        echo "        A-->>C: HTTP 401 + WWW-Authenticate"
        echo "    end"
        echo ""

        # Annotations
        echo "    %% Protocol Details:"
        echo "    %% MCP: JSON-RPC 2.0 over STDIO"
        echo "    %% A2A: HTTP REST with JSON payloads"
        echo "    %% Auth: MCP=transport-level, A2A=mTLS/JWT/API Key"

    } > "$out"
}

# ── 80-integration-facts.json ───────────────────────────────────────────────
emit_integration_facts() {
    local out="$FACTS_DIR/integration-facts.json"
    log_info "Emitting facts/integration-facts.json ..."

    # Count components
    local mcp_classes=0 a2a_classes=0 zai_classes=0 autonomous_classes=0

    [[ -d "$MCP_ROOT" ]] && mcp_classes=$(find "$MCP_ROOT" -name "*.java" -type f 2>/dev/null | wc -l | tr -d ' ')
    [[ -d "$A2A_ROOT" ]] && a2a_classes=$(find "$A2A_ROOT" -name "*.java" -type f 2>/dev/null | wc -l | tr -d ' ')
    [[ -d "$ZAI_ROOT" ]] && zai_classes=$(find "$ZAI_ROOT" -name "*.java" -type f 2>/dev/null | wc -l | tr -d ' ')
    [[ -d "$AUTONOMOUS_ROOT" ]] && autonomous_classes=$(find "$AUTONOMOUS_ROOT" -name "*.java" -type f 2>/dev/null | wc -l | tr -d ' ')

    # Detect MCP tools from YawlToolSpecifications.java
    # Tools are defined with .name("yawl_xxx") pattern in McpSchema.Tool.builder()
    local -a mcp_tools=()
    if [[ -f "$MCP_ROOT/spec/YawlToolSpecifications.java" ]]; then
        while IFS= read -r tool; do
            [[ -n "$tool" ]] && mcp_tools+=("$tool")
        done < <(grep -oE '\.name\("yawl_[a-z_]+"\)' "$MCP_ROOT/spec/YawlToolSpecifications.java" 2>/dev/null | \
                 sed 's/.*name("//; s/").*//' | sort -u)
    fi

    # Detect A2A skills
    local -a a2a_skills=()
    if [[ -f "$A2A_ROOT/YawlA2AServer.java" ]]; then
        while IFS= read -r skill; do
            [[ -n "$skill" ]] && a2a_skills+=("$skill")
        done < <(grep -oE 'id\("[^"]*")' "$A2A_ROOT/YawlA2AServer.java" 2>/dev/null | sed 's/id("//; s/")//')
    fi

    # Detect auth providers
    local -a auth_providers=()
    if [[ -d "$A2A_ROOT/auth" ]]; then
        while IFS= read -r provider; do
            [[ -n "$provider" ]] && auth_providers+=("$provider")
        done < <(find "$A2A_ROOT/auth" -name "*Provider.java" -exec basename {} .java \; 2>/dev/null)
    fi

    # Detect autonomous agents (classes implementing AutonomousAgent interface or ending in Agent.java)
    local -a autonomous_agents=()
    if [[ -d "$AUTONOMOUS_ROOT" ]]; then
        while IFS= read -r agent; do
            [[ -n "$agent" ]] && autonomous_agents+=("$agent")
        done < <(find "$AUTONOMOUS_ROOT" -maxdepth 1 -name "*Agent*.java" -type f 2>/dev/null | \
                 xargs -I {} basename {} .java | sort -u)
    fi
    # Also scan for agents in integration/orderfulfillment
    if [[ -d "$INTEGRATION_ROOT/orderfulfillment" ]]; then
        while IFS= read -r agent; do
            [[ -n "$agent" ]] && autonomous_agents+=("$agent")
        done < <(find "$INTEGRATION_ROOT/orderfulfillment" -name "*Agent*.java" -type f 2>/dev/null | \
                 xargs -I {} basename {} .java | sort -u)
    fi

    {
        printf '{\n'
        printf '  "mcp": {\n'
        printf '    "classes": %d,\n' "${mcp_classes:-0}"
        printf '    "server": "YawlMcpServer",\n'
        printf '    "version": "5.2.0",\n'
        printf '    "sdk_version": "0.17.2",\n'
        printf '    "transport": "STDIO",\n'
        printf '    "protocol": "MCP 2024-11-05",\n'
        printf '    "tools_count": %d,\n' "${#mcp_tools[@]}"
        printf '    "tools": [\n'
        local first=true
        for tool in "${mcp_tools[@]}"; do
            $first || printf ',\n'
            first=false
            printf '      "%s"' "$tool"
        done
        printf '\n    ],\n'
        printf '    "resources": ["yawl://specifications", "yawl://cases", "yawl://workitems"],\n'
        printf '    "prompts": ["workflow_analysis", "task_completion_guide", "case_troubleshooting", "workflow_design_review"]\n'
        printf '  },\n'
        printf '  "a2a": {\n'
        printf '    "classes": %d,\n' "${a2a_classes:-0}"
        printf '    "server": "YawlA2AServer",\n'
        printf '    "version": "5.2.0",\n'
        printf '    "default_port": 8081,\n'
        printf '    "transport": "HTTP REST",\n'
        printf '    "protocol": "A2A v0.2.0",\n'
        printf '    "skills_count": %d,\n' "${#a2a_skills[@]}"
        printf '    "skills": [\n'
        first=true
        for skill in "${a2a_skills[@]}"; do
            $first || printf ',\n'
            first=false
            printf '      "%s"' "$skill"
        done
        printf '\n    ],\n'
        printf '    "auth_providers": [\n'
        first=true
        for provider in "${auth_providers[@]}"; do
            $first || printf ',\n'
            first=false
            printf '      "%s"' "$provider"
        done
        printf '\n    ]\n'
        printf '  },\n'
        printf '  "zai": {\n'
        printf '    "classes": %d,\n' "${zai_classes:-0}"
        printf '    "service": "ZaiFunctionService",\n'
        printf '    "model": "GLM-4",\n'
        printf '    "api_key_env": "ZHIPU_API_KEY",\n'
        printf '    "capabilities": ["function_calling", "natural_language", "reasoning"]\n'
        printf '  },\n'
        printf '  "autonomous": {\n'
        printf '    "classes": %d,\n' "${autonomous_classes:-0}"
        printf '    "agents_count": %d,\n' "${#autonomous_agents[@]}"
        printf '    "agents": [\n'
        first=true
        for agent in "${autonomous_agents[@]}"; do
            $first || printf ',\n'
            first=false
            printf '      "%s"' "$agent"
        done
        printf '\n    ]\n'
        printf '  }\n'
        printf '}\n'
    } > "$out"
}

# ── Main dispatcher ──────────────────────────────────────────────────────────
emit_all_integration_diagrams() {
    timer_start
    record_memory "integration_start"

    log_info "Emitting integration diagrams with incremental cache support..."

    # Phase 1: Integration architecture diagrams (use emit_if_stale for caching)
    # Each diagram checks staleness before emitting for 90%+ speedup on cached runs
    emit_if_stale "diagrams/60-mcp-architecture.mmd" emit_mcp_architecture_diagram
    emit_if_stale "diagrams/65-a2a-topology.mmd" emit_a2a_topology_diagram
    emit_if_stale "diagrams/70-agent-capabilities.mmd" emit_agent_capabilities_diagram
    emit_if_stale "diagrams/75-protocol-sequences.mmd" emit_protocol_sequences_diagram

    # Phase 2: Integration facts
    emit_if_stale "facts/integration-facts.json" emit_integration_facts

    INTEGRATION_ELAPSED=$(timer_elapsed_ms)
    record_phase_timing "integration" "$INTEGRATION_ELAPSED"

    # Log cache summary for debugging
    if declare -f print_cache_summary >/dev/null 2>&1; then
        print_cache_summary | while read -r line; do log_info "$line"; done
    fi

    log_ok "All integration diagrams and facts emitted in ${INTEGRATION_ELAPSED}ms"
}
