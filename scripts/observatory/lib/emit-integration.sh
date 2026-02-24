#!/usr/bin/env bash
set -euo pipefail
# ==========================================================================
# emit-integration.sh — Library for MCP and A2A integration diagram generation
#
# Provides functions to generate:
#   - Mermaid sequence diagrams for MCP (Model Context Protocol) calls
#   - Mermaid diagrams for A2A (Agent-to-Agent) protocol flows
#   - YAWL XML specifications for integration workflows
#
# MCP = Model Context Protocol - AI tool integration (Claude Desktop, Cursor IDE)
# A2A = Agent-to-Agent Protocol - Autonomous agent communication
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

# ── emit_mcp_flow_mermaid ────────────────────────────────────────────────────
# Generates a Mermaid sequence diagram for MCP (Model Context Protocol) calls.
#
# The MCP protocol follows this flow:
# 1. Client initializes connection
# 2. Server returns capabilities (tools, resources, prompts)
# 3. Client invokes tools (launch_case, get_workitems, etc.)
# 4. Server executes via YAWL InterfaceB
# 5. Results returned as JSON-RPC responses
#
# Arguments:
#   $1 - output file path (optional, defaults to $DIAGRAMS_DIR/mcp-flow.mmd)
#
# Output:
#   Writes Mermaid sequence diagram to specified file
# ─────────────────────────────────────────────────────────────────────────────
emit_mcp_flow_mermaid() {
    local out="${1:-${DIAGRAMS_DIR}/mcp-flow.mmd}"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting MCP flow Mermaid diagram to $out ..."

    mkdir -p "$(dirname "$out")"

    local timestamp
    timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)

    cat > "$out" << 'MERMAID'
%%{ init: { 'theme': 'base', 'themeVariables': { 'primaryColor': '#3182ce', 'sequenceNumberColor': '#fff' } } }%%
---
title: MCP (Model Context Protocol) Sequence Flow
description: AI tool integration via JSON-RPC 2.0 over STDIO
generated: TIMESTAMP_PLACEHOLDER
commit: COMMIT_PLACEHOLDER
---
sequenceDiagram
    autonumber
    participant Client as MCP Client<br/>(Claude/Cursor)
    participant Transport as STDIO Transport
    participant Server as YawlMcpServer
    participant Registry as ToolRegistry
    participant InterfaceB as InterfaceB Client
    participant Engine as YEngine

    Note over Client,Engine: Phase 1 - Initialization Handshake

    rect rgb(49, 130, 206, 0.15)
        Client->>Transport: stdin: {"jsonrpc":"2.0","method":"initialize","params":{...}}
        Transport->>Server: Parse JSON-RPC message
        Server->>Server: Create McpServer instance
        Server->>InterfaceB: connect(username, password)
        InterfaceB->>Engine: Authenticate session
        Engine-->>InterfaceB: sessionHandle
        InterfaceB-->>Server: sessionHandle
        Server->>Registry: registerTools()
        Registry-->>Server: 15 tools registered
        Server-->>Transport: stdout: {"result":{"capabilities":{...}}}
        Transport-->>Client: Initialize response + capabilities
    end

    Note over Client,Engine: Phase 2 - Tool Discovery

    rect rgb(128, 90, 213, 0.15)
        Client->>Transport: {"method":"tools/list"}
        Transport->>Server: Forward request
        Server->>Registry: getToolDefinitions()
        Registry-->>Server: Tool schemas (inputSchema, outputSchema)
        Server-->>Transport: {"result":{"tools":[...]}}
        Transport-->>Client: 15 tools available
    end

    Note over Client,Engine: Phase 3 - Tool Invocation (launch_case)

    rect rgb(56, 161, 105, 0.15)
        Client->>Transport: {"method":"tools/call","params":{"name":"launch_case","arguments":{...}}}
        Transport->>Server: Forward tool call
        Server->>Server: Validate arguments against inputSchema
        Server->>InterfaceB: launchCase(specID, caseData, sessionHandle)
        InterfaceB->>Engine: Create case instance
        Engine->>Engine: Initialize YNetRunner
        Engine->>Engine: Execute inputCondition
        Engine-->>InterfaceB: caseId XML
        InterfaceB-->>Server: <caseId>Case_12345</caseId>
        Server->>Server: Format as JSON result
        Server-->>Transport: {"content":[{"type":"text","text":"..."}]}
        Transport-->>Client: Tool result with caseId
    end

    Note over Client,Engine: Phase 4 - Resource Access

    rect rgb(214, 158, 46, 0.15)
        Client->>Transport: {"method":"resources/read","params":{"uri":"yawl://cases"}}
        Transport->>Server: Forward resource request
        Server->>InterfaceB: getCases(sessionHandle)
        InterfaceB->>Engine: Query running cases
        Engine-->>InterfaceB: Case list XML
        InterfaceB-->>Server: Case data
        Server-->>Transport: {"contents":[{"uri":"yawl://cases","text":"..."}]}
        Transport-->>Client: Resource content (JSON)
    end

    Note over Client,Engine: Phase 5 - Prompt Rendering

    rect rgb(229, 62, 62, 0.15)
        Client->>Transport: {"method":"prompts/get","params":{"name":"workflow_analysis"}}
        Transport->>Server: Forward prompt request
        Server->>Server: Render prompt template
        Server-->>Transport: {"messages":[{"role":"user","content":{...}}]}
        Transport-->>Client: Prompt messages for LLM context
    end

    Note over Client,Engine: Phase 6 - Completion Suggestions

    rect rgb(160, 174, 192, 0.15)
        Client->>Transport: {"method":"completion/complete","params":{"ref":{"type":"ref/resource","uri":"yawl://specifications"}}}
        Transport->>Server: Forward completion request
        Server->>InterfaceB: getSpecificationIDs(sessionHandle)
        InterfaceB->>Engine: Query spec IDs
        Engine-->>InterfaceB: Spec ID list
        InterfaceB-->>Server: Spec IDs
        Server-->>Transport: {"completion":{"values":[...],"total":N}}
        Transport-->>Client: Autocomplete suggestions
    end

    Note over Client,Engine: Phase 7 - Error Handling

    rect rgb(245, 101, 101, 0.15)
        Client->>Transport: {"method":"tools/call","params":{"name":"invalid_tool"}}
        Transport->>Server: Forward request
        Server->>Server: Tool not found
        Server-->>Transport: {"error":{"code":-32601,"message":"Method not found"}}
        Transport-->>Client: JSON-RPC error response

        Client->>Transport: {"method":"tools/call","params":{"name":"launch_case","arguments":{"bad":"arg"}}}
        Transport->>Server: Forward request
        Server->>Server: Validate arguments - FAIL
        Server-->>Transport: {"error":{"code":-32602,"message":"Invalid params"}}
        Transport-->>Client: Validation error
    end

    Note over Client,Engine: Phase 8 - Shutdown

    rect rgb(74, 85, 104, 0.15)
        Client->>Transport: {"method":"shutdown"}
        Transport->>Server: Graceful shutdown request
        Server->>InterfaceB: disconnect(sessionHandle)
        InterfaceB->>Engine: Invalidate session
        Engine-->>InterfaceB: Session closed
        InterfaceB-->>Server: Disconnected
        Server-->>Transport: {"result":null}
        Transport-->>Client: Shutdown acknowledged
        Client->>Transport: {"method":"exit"}
        Server->>Server: Close STDIO streams
    end

    %% Protocol Details:
    %% - Transport: STDIO with newline-delimited JSON-RPC 2.0
    %% - Protocol Version: MCP 2024-11-05
    %% - Auth: Transport-level (process isolation)
    %% - Capabilities: tools, resources, prompts, completions, logging
MERMAID

    # Replace placeholders
    sed -i.bak \
        -e "s|TIMESTAMP_PLACEHOLDER|${timestamp}|g" \
        -e "s|COMMIT_PLACEHOLDER|$(git_commit)|g" \
        "$out" && rm -f "${out}.bak"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_mcp_flow_mermaid" "$op_elapsed"
    log_ok "MCP flow diagram written to $out"
}

# ── emit_a2a_flow_mermaid ────────────────────────────────────────────────────
# Generates a Mermaid diagram for A2A (Agent-to-Agent) protocol flows.
#
# The A2A protocol follows this flow:
# 1. Agent discovery via /.well-known/agent.json (no auth)
# 2. Authentication via mTLS/JWT/API Key
# 3. Message exchange via REST endpoints
# 4. Skill execution via YAWL engine
#
# Arguments:
#   $1 - output file path (optional, defaults to $DIAGRAMS_DIR/a2a-flow.mmd)
#   $2 - topology type: "sequence" or "graph" (optional, defaults to "sequence")
#
# Output:
#   Writes Mermaid diagram to specified file
# ─────────────────────────────────────────────────────────────────────────────
emit_a2a_flow_mermaid() {
    local out="${1:-${DIAGRAMS_DIR}/a2a-flow.mmd}"
    local topology="${2:-sequence}"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting A2A flow Mermaid diagram ($topology) to $out ..."

    mkdir -p "$(dirname "$out")"

    local timestamp
    timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)

    if [[ "$topology" == "graph" ]]; then
        emit_a2a_graph_diagram "$out" "$timestamp"
    else
        emit_a2a_sequence_diagram "$out" "$timestamp"
    fi

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_a2a_flow_mermaid" "$op_elapsed"
    log_ok "A2A flow diagram written to $out"
}

# Internal: A2A sequence diagram
emit_a2a_sequence_diagram() {
    local out="$1"
    local timestamp="$2"

    cat > "$out" << 'MERMAID'
%%{ init: { 'theme': 'base', 'themeVariables': { 'primaryColor': '#805ad5', 'sequenceNumberColor': '#fff' } } }%%
---
title: A2A (Agent-to-Agent) Protocol Sequence Flow
description: Autonomous agent communication via HTTP REST
generated: TIMESTAMP_PLACEHOLDER
commit: COMMIT_PLACEHOLDER
---
sequenceDiagram
    autonumber
    participant AgentA as Agent A<br/>(Requester)
    participant Transport as HTTP/1.1
    participant Auth as AuthenticationProvider
    participant Server as YawlA2AServer
    participant Executor as YawlAgentExecutor
    participant Adapter as YawlEngineAdapter
    participant InterfaceB as InterfaceB
    participant Engine as YEngine
    participant AgentB as Agent B<br/>(Responder)

    Note over AgentA,AgentB: Phase 1 - Agent Discovery (No Auth Required)

    rect rgb(128, 90, 213, 0.15)
        AgentA->>Transport: GET /.well-known/agent.json
        Transport->>Server: HTTP GET request
        Server->>Server: No authentication for discovery
        Server-->>Transport: AgentCard JSON-LD
        Transport-->>AgentA: Agent capabilities + endpoints
        Note right of AgentA: AgentCard contains:<br/>- skills (workflow ops)<br/>- authentication schemes<br/>- endpoints
    end

    Note over AgentA,AgentB: Phase 2 - Authentication

    rect rgb(214, 158, 46, 0.15)
        AgentA->>AgentA: Select auth scheme from AgentCard
        AgentA->>Transport: POST / (message) + Authorization header
        Transport->>Server: HTTP POST with auth
        Server->>Auth: validateAuthentication(headers, body)

        alt mTLS Authentication
            Auth->>Auth: Verify X.509 SVID certificate
            Auth-->>Server: Authenticated (principal: agent-a)
        else JWT Bearer
            Auth->>Auth: Verify HS256 signature
            Auth->>Auth: Check expiry and claims
            Auth-->>Server: Authenticated (sub: agent-a)
        else API Key
            Auth->>Auth: Compute HMAC-SHA256
            Auth->>Auth: Compare signature
            Auth-->>Server: Authenticated (key_id: key-001)
        else Invalid Auth
            Auth-->>Server: Authentication failed
            Server-->>Transport: HTTP 401 + WWW-Authenticate
            Transport-->>AgentA: Authentication required
        end
    end

    Note over AgentA,AgentB: Phase 3 - Message Processing

    rect rgb(56, 161, 105, 0.15)
        Server->>Executor: execute(A2AMessage)
        Executor->>Executor: Parse message intent
        Executor->>Executor: Match skill from registry

        alt Skill: launch_workflow
            Executor->>Adapter: launchWorkflow(specId, params)
            Adapter->>InterfaceB: launchCase(specID, data, handle)
            InterfaceB->>Engine: Create case
            Engine-->>InterfaceB: caseId
            InterfaceB-->>Adapter: caseId
            Adapter-->>Executor: WorkflowResult(caseId)
        else Skill: query_workflows
            Executor->>Adapter: queryWorkflows(filter)
            Adapter->>InterfaceB: getSpecificationIDs(handle)
            InterfaceB->>Engine: Query specs
            Engine-->>InterfaceB: Spec list
            InterfaceB-->>Adapter: Specifications
            Adapter-->>Executor: QueryResult(specs)
        else Skill: manage_workitems
            Executor->>Adapter: getWorkItems(caseId)
            Adapter->>InterfaceB: getWorkItems(handle)
            InterfaceB->>Engine: Query work items
            Engine-->>InterfaceB: Work item list
            InterfaceB-->>Adapter: WorkItems
            Adapter-->>Executor: WorkItemResult(items)
        else Skill: cancel_workflow
            Executor->>Adapter: cancelWorkflow(caseId)
            Adapter->>InterfaceB: cancelCase(caseId, handle)
            InterfaceB->>Engine: Cancel case
            Engine-->>InterfaceB: Cancellation result
            InterfaceB-->>Adapter: Result
            Adapter-->>Executor: CancelResult
        end

        Executor-->>Server: A2AResponse(status, data)
    end

    Note over AgentA,AgentB: Phase 4 - Response Delivery

    rect rgb(49, 130, 206, 0.15)
        Server-->>Transport: HTTP 200 + JSON response
        Transport-->>AgentA: A2A response message
        AgentA->>AgentA: Process response
        AgentA->>AgentA: Decide next action
        AgentA->>AgentB: Forward task (if multi-agent)
    end

    Note over AgentA,AgentB: Phase 5 - Streaming Response (Optional)

    rect rgb(160, 174, 192, 0.15)
        AgentA->>Transport: POST /tasks/{id}/subscribe
        Transport->>Server: SSE subscription request
        Server->>Server: Create event stream
        loop Until task complete
            Engine->>Server: State change event
            Server-->>Transport: SSE: data: {"event":"progress",...}
            Transport-->>AgentA: Real-time progress
        end
        Server-->>Transport: SSE: data: {"event":"complete",...}
        Transport-->>AgentA: Final result
    end

    Note over AgentA,AgentB: Phase 6 - Error Handling

    rect rgb(229, 62, 62, 0.15)
        AgentA->>Transport: POST / (invalid message)
        Transport->>Server: Malformed request
        Server->>Server: Validate JSON schema
        Server-->>Transport: HTTP 400 + error details
        Transport-->>AgentA: Bad request error

        AgentA->>Transport: POST / (skill not found)
        Transport->>Server: Unknown skill
        Server->>Executor: Match skill - FAIL
        Server-->>Transport: HTTP 404 + available skills
        Transport-->>AgentA: Skill not found

        AgentA->>Transport: POST / (engine error)
        Server->>Executor: Execute skill
        Executor->>Adapter: Call engine
        Adapter->>InterfaceB: Engine operation
        InterfaceB-->>Adapter: Engine failure
        Adapter-->>Executor: Error result
        Executor-->>Server: A2AResponse(error)
        Server-->>Transport: HTTP 500 + error context
        Transport-->>AgentA: Internal error
    end

    %% Protocol Details:
    %% - Transport: HTTP/1.1 REST with JSON payloads
    %% - Protocol Version: A2A v0.2.0
    %% - Auth: Multi-scheme (mTLS/SPIFFE, JWT HS256, API Key HMAC-SHA256)
    %% - Discovery: /.well-known/agent.json (JSON-LD AgentCard)
    %% - Skills: launch_workflow, query_workflows, manage_workitems, cancel_workflow
MERMAID

    # Replace placeholders
    sed -i.bak \
        -e "s|TIMESTAMP_PLACEHOLDER|${timestamp}|g" \
        -e "s|COMMIT_PLACEHOLDER|$(git_commit)|g" \
        "$out" && rm -f "${out}.bak"
}

# Internal: A2A graph topology diagram
emit_a2a_graph_diagram() {
    local out="$1"
    local timestamp="$2"

    cat > "$out" << 'MERMAID'
%%{ init: { 'theme': 'base', 'themeVariables': { 'primaryColor': '#805ad5' } } }%%
---
title: A2A (Agent-to-Agent) Network Topology
description: Multi-agent collaboration via YAWL orchestration
generated: TIMESTAMP_PLACEHOLDER
commit: COMMIT_PLACEHOLDER
---
graph TB
    classDef agent fill:#805ad5,stroke:#6b46c1,color:#fff
    classDef yawl fill:#e53e3e,stroke:#c53030,color:#fff
    classDef mcp fill:#3182ce,stroke:#2b6cb0,color:#fff
    classDef transport fill:#38a169,stroke:#2f855a,color:#fff
    classDef auth fill:#d69e2e,stroke:#b7791f,color:#fff

    subgraph AgentEcosystem["Agent Ecosystem"]
        PLANNER[Planning Agent<br/>Workflow Design]:::agent
        EXECUTOR[Execution Agent<br/>Task Automation]:::agent
        MONITOR[Monitoring Agent<br/>State Tracking]:::agent
        APPROVER[Human Agent<br/>Approval Workflows]:::agent
        COORD[Coordinator Agent<br/>Multi-agent Orchestration]:::agent
    end

    subgraph TransportLayer["Transport Layer"]
        HTTP[HTTP/1.1 REST<br/>com.sun.net.httpserver]:::transport
        SSE[Server-Sent Events<br/>Real-time Updates]:::transport
    end

    subgraph A2AServer["YawlA2AServer"]
        A2A[YawlA2AServer<br/>Port 8081]:::agent
        EXE[YawlAgentExecutor<br/>Message Processing]:::agent
        ADAPT[YawlEngineAdapter<br/>Protocol Translation]:::agent
    end

    subgraph Authentication["Multi-Scheme Auth"]
        MTLS[mTLS/SPIFFE<br/>X.509 SVID]:::auth
        JWT[JWT Bearer<br/>HS256]:::auth
        APIKEY[API Key<br/>HMAC-SHA256]:::auth
        COMPOSITE[CompositeAuthenticationProvider]:::auth
    end

    subgraph YAWLEngine["YAWL Engine"]
        IFB[InterfaceB<br/>Workflow Operations]:::yawl
        ENGINE[YEngine<br/>Petri Net Runtime]:::yawl
        SPECS[Specification<br/>Registry]:::yawl
        CASES[Case<br/>Instances]:::yawl
    end

    subgraph MCPServer["MCP Server (Alternative)"]
        MCP[YawlMcpServer<br/>STDIO Transport]:::mcp
        MCP_TOOLS[15+ Tools<br/>AI Integration]:::mcp
    end

    %% Agent connections
    PLANNER --> HTTP
    EXECUTOR --> HTTP
    MONITOR --> SSE
    APPROVER --> HTTP
    COORD --> HTTP

    HTTP --> A2A
    SSE --> A2A

    %% Authentication flow
    A2A --> COMPOSITE
    COMPOSITE --> MTLS
    COMPOSITE --> JWT
    COMPOSITE --> APIKEY

    %% Execution flow
    A2A --> EXE
    EXE --> ADAPT
    ADAPT --> IFB
    IFB --> ENGINE
    ENGINE --> SPECS
    ENGINE --> CASES

    %% MCP alternative path
    MCP --> IFB
    MCP --> MCP_TOOLS

    %% Cross-agent coordination
    COORD -.->|delegates| PLANNER
    COORD -.->|delegates| EXECUTOR
    COORD -.->|delegates| MONITOR
    COORD -.->|requests| APPROVER

    %% Agent-to-Agent communication
    PLANNER -.->|A2A protocol| EXECUTOR
    EXECUTOR -.->|A2A protocol| MONITOR
    MONITOR -.->|notify| APPROVER

    %% Discovery
    subgraph Discovery["Agent Discovery"]
        AGENTCARD[/.well-known/agent.json<br/>JSON-LD AgentCard]:::transport
    end

    AGENTCARD -.->|advertises| A2A
    PLANNER -.->|discovers| AGENTCARD

    %% Protocol annotations
    %% A2A v0.2.0 - HTTP REST with JSON payloads
    %% Multi-scheme auth: mTLS, JWT, API Key
    %% Skills: launch_workflow, query_workflows, manage_workitems, cancel_workflow
MERMAID

    # Replace placeholders
    sed -i.bak \
        -e "s|TIMESTAMP_PLACEHOLDER|${timestamp}|g" \
        -e "s|COMMIT_PLACEHOLDER|$(git_commit)|g" \
        "$out" && rm -f "${out}.bak"
}

# ── emit_integration_yawl ────────────────────────────────────────────────────
# Generates YAWL XML specifications for integration workflows.
#
# Creates YAWL workflow definitions that model:
# 1. MCP tool invocation as workflow tasks
# 2. A2A message processing as workflow tasks
# 3. Multi-agent orchestration patterns
#
# Arguments:
#   $1 - output file path (optional, defaults to $YAWL_DIR/integration.yawl.xml)
#   $2 - workflow type: "mcp", "a2a", or "full" (optional, defaults to "full")
#
# Output:
#   Writes YAWL XML specification to specified file
# ─────────────────────────────────────────────────────────────────────────────
emit_integration_yawl() {
    local out="${1:-${YAWL_DIR}/integration.yawl.xml}"
    local workflow_type="${2:-full}"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting integration YAWL XML ($workflow_type) to $out ..."

    mkdir -p "$(dirname "$out")"

    local timestamp
    timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    local commit
    commit=$(git_commit)
    local branch
    branch=$(git_branch)

    cat > "$out" << YAWLXML
<?xml version="1.0" encoding="UTF-8"?>
<!--
  YAWL Specification: Integration Workflow
  Generated: ${timestamp}
  Commit: ${commit}
  Branch: ${branch}

  This YAWL net models MCP and A2A integration workflows:
  - MCP tool invocation pipeline
  - A2A message processing pipeline
  - Multi-agent orchestration patterns
  - Error handling and compensation

  Control-flow patterns used:
  - WCP1: Sequence
  - WCP2: Parallel Split (AND-split)
  - WCP3: Synchronization (AND-join)
  - WCP4: Exclusive Choice (XOR-split)
  - WCP5: Simple Merge (XOR-join)
  - WCP19: Cancel Case
-->
<specification version="4.0"
               xmlns="http://www.yawlfoundation.org/yawlschema"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
                                   http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd"
               uri="http://yawlfoundation.org/yawl/v6/integration">

  <metaData>
    <title>YAWL V6 Integration Workflow</title>
    <description>MCP and A2A protocol integration as YAWL workflow nets</description>
    <creator>YAWL V6 Observatory</creator>
    <created>${timestamp}</created>
  </metaData>

  <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="mcpRequest" type="xs:string"/>
    <xs:element name="a2aMessage" type="xs:string"/>
    <xs:element name="authResult" type="xs:string"/>
    <xs:element name="workflowResult" type="xs:string"/>
    <xs:element name="errorContext" type="xs:string"/>
  </xs:schema>

  <!-- Root decomposition: Integration Orchestrator -->
  <decomposition id="IntegrationOrchestrator" isRootNet="true">
    <processControlElements>
      <inputCondition id="start" name="Start Integration">
        <flowsInto>
          <nextElementRef id="routeProtocol"/>
        </flowsInto>
      </inputCondition>

      <!-- Protocol Router: XOR-split based on protocol type -->
      <task id="routeProtocol" name="Route Protocol">
        <description>Determine protocol type and route to appropriate handler</description>
        <flowsInto>
          <nextElementRef id="mcpHandler"/>
          <predicateEval>protocol = 'mcp'</predicateEval>
        </flowsInto>
        <flowsInto>
          <nextElementRef id="a2aHandler"/>
          <predicateEval>protocol = 'a2a'</predicateEval>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <!-- MCP Handler Subnet -->
      <task id="mcpHandler" name="MCP Handler">
        <description>Process MCP tool invocation request</description>
        <flowsInto>
          <nextElementRef id="mcpValidate"/>
        </flowsInto>
        <decomposesTo id="McpToolInvocation"/>
        <join code="xor"/>
        <split code="and"/>
      </task>

      <!-- A2A Handler Subnet -->
      <task id="a2aHandler" name="A2A Handler">
        <description>Process A2A agent message</description>
        <flowsInto>
          <nextElementRef id="a2aAuth"/>
        </flowsInto>
        <decomposesTo id="A2AMessageProcessing"/>
        <join code="xor"/>
        <split code="and"/>
      </task>

      <!-- MCP Validation -->
      <task id="mcpValidate" name="Validate MCP Request">
        <description>Validate JSON-RPC request against MCP schema</description>
        <flowsInto>
          <nextElementRef id="mcpValid"/>
          <predicateEval>validation = 'success'</predicateEval>
        </flowsInto>
        <flowsInto>
          <nextElementRef id="validationError"/>
          <predicateEval>validation = 'failure'</predicateEval>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <!-- A2A Authentication -->
      <task id="a2aAuth" name="Authenticate A2A Agent">
        <description>Validate agent credentials via CompositeAuthenticationProvider</description>
        <flowsInto>
          <nextElementRef id="a2aAuthorized"/>
          <predicateEval>authenticated = true</predicateEval>
        </flowsInto>
        <flowsInto>
          <nextElementRef id="authError"/>
          <predicateEval>authenticated = false</predicateEval>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <!-- MCP Valid Path -->
      <condition id="mcpValid" name="MCP Valid">
        <flowsInto>
          <nextElementRef id="executeTool"/>
        </flowsInto>
      </condition>

      <!-- A2A Authorized Path -->
      <condition id="a2aAuthorized" name="A2A Authorized">
        <flowsInto>
          <nextElementRef id="executeSkill"/>
        </flowsInto>
      </condition>

      <!-- Execute MCP Tool -->
      <task id="executeTool" name="Execute MCP Tool">
        <description>Invoke YAWL engine via InterfaceB for tool execution</description>
        <flowsInto>
          <nextElementRef id="toolSuccess"/>
          <predicateEval>result = 'success'</predicateEval>
        </flowsInto>
        <flowsInto>
          <nextElementRef id="toolError"/>
          <predicateEval>result = 'error'</predicateEval>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <!-- Execute A2A Skill -->
      <task id="executeSkill" name="Execute A2A Skill">
        <description>Execute skill via YawlAgentExecutor and YawlEngineAdapter</description>
        <flowsInto>
          <nextElementRef id="skillSuccess"/>
          <predicateEval>result = 'success'</predicateEval>
        </flowsInto>
        <flowsInto>
          <nextElementRef id="skillError"/>
          <predicateEval>result = 'error'</predicateEval>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <!-- Success Paths -->
      <condition id="toolSuccess" name="Tool Success">
        <flowsInto>
          <nextElementRef id="joinResults"/>
        </flowsInto>
      </condition>

      <condition id="skillSuccess" name="Skill Success">
        <flowsInto>
          <nextElementRef id="joinResults"/>
        </flowsInto>
      </condition>

      <!-- Error Paths -->
      <task id="validationError" name="Handle Validation Error">
        <description>Log validation error and return JSON-RPC error response</description>
        <flowsInto>
          <nextElementRef id="joinErrors"/>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <task id="authError" name="Handle Auth Error">
        <description>Return HTTP 401 with WWW-Authenticate header</description>
        <flowsInto>
          <nextElementRef id="joinErrors"/>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <task id="toolError" name="Handle Tool Error">
        <description>Log error and return tool error response</description>
        <flowsInto>
          <nextElementRef id="joinErrors"/>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <task id="skillError" name="Handle Skill Error">
        <description>Log error and return A2A error response</description>
        <flowsInto>
          <nextElementRef id="joinErrors"/>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <!-- Join Conditions -->
      <condition id="joinResults" name="Join Results">
        <flowsInto>
          <nextElementRef id="end"/>
        </flowsInto>
      </condition>

      <condition id="joinErrors" name="Join Errors">
        <flowsInto>
          <nextElementRef id="end"/>
        </flowsInto>
      </condition>

      <outputCondition id="end" name="Integration Complete"/>

    </processControlElements>
  </decomposition>

  <!-- MCP Tool Invocation Subnet -->
  <decomposition id="McpToolInvocation">
    <processControlElements>
      <inputCondition id="mcpStart" name="MCP Start">
        <flowsInto>
          <nextElementRef id="parseJsonRpc"/>
        </flowsInto>
      </inputCondition>

      <task id="parseJsonRpc" name="Parse JSON-RPC">
        <description>Parse incoming JSON-RPC 2.0 message</description>
        <flowsInto>
          <nextElementRef id="lookupTool"/>
        </flowsInto>
        <join code="xor"/>
        <split code="and"/>
      </task>

      <task id="lookupTool" name="Lookup Tool">
        <description>Find tool in ToolRegistry by name</description>
        <flowsInto>
          <nextElementRef id="validateArgs"/>
        </flowsInto>
        <join code="xor"/>
        <split code="and"/>
      </task>

      <task id="validateArgs" name="Validate Arguments">
        <description>Validate arguments against tool inputSchema</description>
        <flowsInto>
          <nextElementRef id="callInterfaceB"/>
        </flowsInto>
        <join code="xor"/>
        <split code="and"/>
      </task>

      <task id="callInterfaceB" name="Call InterfaceB">
        <description>Execute YAWL operation via InterfaceB client</description>
        <flowsInto>
          <nextElementRef id="formatResponse"/>
        </flowsInto>
        <join code="xor"/>
        <split code="and"/>
      </task>

      <task id="formatResponse" name="Format Response">
        <description>Format result as MCP tool response</description>
        <flowsInto>
          <nextElementRef id="mcpEnd"/>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <outputCondition id="mcpEnd" name="MCP End"/>
    </processControlElements>
  </decomposition>

  <!-- A2A Message Processing Subnet -->
  <decomposition id="A2AMessageProcessing">
    <processControlElements>
      <inputCondition id="a2aStart" name="A2A Start">
        <flowsInto>
          <nextElementRef id="parseMessage"/>
        </flowsInto>
      </inputCondition>

      <task id="parseMessage" name="Parse A2A Message">
        <description>Parse incoming A2A message JSON</description>
        <flowsInto>
          <nextElementRef id="matchSkill"/>
        </flowsInto>
        <join code="xor"/>
        <split code="and"/>
      </task>

      <task id="matchSkill" name="Match Skill">
        <description>Match intent to registered skill</description>
        <flowsInto>
          <nextElementRef id="executeWithAdapter"/>
        </flowsInto>
        <join code="xor"/>
        <split code="and"/>
      </task>

      <task id="executeWithAdapter" name="Execute via Adapter">
        <description>Execute skill via YawlEngineAdapter</description>
        <flowsInto>
          <nextElementRef id="createA2AResponse"/>
        </flowsInto>
        <join code="xor"/>
        <split code="and"/>
      </task>

      <task id="createA2AResponse" name="Create A2A Response">
        <description>Build A2A response message with result</description>
        <flowsInto>
          <nextElementRef id="a2aEnd"/>
        </flowsInto>
        <join code="xor"/>
        <split code="xor"/>
      </task>

      <outputCondition id="a2aEnd" name="A2A End"/>
    </processControlElements>
  </decomposition>

</specification>
YAWLXML

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_integration_yawl" "$op_elapsed"
    log_ok "Integration YAWL XML written to $out"
}

# ── emit_zai_flow_mermaid ────────────────────────────────────────────────────
# Generates a Mermaid diagram for Z.AI (GLM-4) function calling integration.
#
# Z.AI provides:
# - Natural language understanding
# - Function calling (tool use)
# - Context management
#
# Arguments:
#   $1 - output file path (optional, defaults to $DIAGRAMS_DIR/zai-flow.mmd)
#
# Output:
#   Writes Mermaid sequence diagram to specified file
# ─────────────────────────────────────────────────────────────────────────────
emit_zai_flow_mermaid() {
    local out="${1:-${DIAGRAMS_DIR}/zai-flow.mmd}"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting Z.AI flow Mermaid diagram to $out ..."

    mkdir -p "$(dirname "$out")"

    local timestamp
    timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)

    cat > "$out" << 'MERMAID'
%%{ init: { 'theme': 'base', 'themeVariables': { 'primaryColor': '#d69e2e', 'sequenceNumberColor': '#fff' } } }%%
---
title: Z.AI (GLM-4) Function Calling Integration
description: Natural language workflow control via GLM-4 function calling
generated: TIMESTAMP_PLACEHOLDER
commit: COMMIT_PLACEHOLDER
---
sequenceDiagram
    autonumber
    participant User as User/AI Client
    participant MCP as YawlMcpServer
    participant ZAI as ZaiFunctionService
    participant HTTP as ZaiHttpClient
    participant GLM4 as GLM-4 API<br/>(api.zhihui.ai)
    participant Engine as YEngine

    Note over User,Engine: Phase 1 - Natural Language Request

    rect rgb(214, 158, 46, 0.15)
        User->>MCP: "Start the order processing workflow"
        MCP->>MCP: Detect natural language input
        MCP->>ZAI: processWithFunctions(nlText, context)
        ZAI->>ZAI: Build function definitions
        ZAI->>HTTP: POST /api/paas/v4/tools
        HTTP->>GLM4: Request with function definitions
        GLM4->>GLM4: Parse intent
        GLM4->>GLM4: Match to function schema
        GLM4-->>HTTP: Function call: launch_case(spec_id="order-processing")
        HTTP-->>ZAI: Function call response
    end

    Note over User,Engine: Phase 2 - Function Execution

    rect rgb(56, 161, 105, 0.15)
        ZAI->>ZAI: Extract function name + arguments
        ZAI->>MCP: Callback: executeFunction("launch_case", args)
        MCP->>Engine: launchCase(specID, caseData)
        Engine->>Engine: Create case instance
        Engine-->>MCP: caseId: Case_12345
        MCP-->>ZAI: Function result
        ZAI->>HTTP: POST /api/paas/v4/tools (with result)
        HTTP->>GLM4: Continue with function result
        GLM4->>GLM4: Generate natural language response
        GLM4-->>HTTP: "I have started the order processing workflow..."
        HTTP-->>ZAI: Natural language response
        ZAI-->>MCP: Formatted response
        MCP-->>User: "Started order processing workflow (Case_12345)"
    end

    Note over User,Engine: Phase 3 - Multi-turn Conversation

    rect rgb(49, 130, 206, 0.15)
        User->>MCP: "What tasks are pending?"
        MCP->>ZAI: processWithFunctions(nlText, context + history)
        ZAI->>HTTP: POST with conversation history
        HTTP->>GLM4: Request with context
        GLM4->>GLM4: Understand context from history
        GLM4-->>HTTP: Function call: get_workitems(case_id="Case_12345")
        HTTP-->>ZAI: Function call
        ZAI->>MCP: Execute get_workitems
        MCP->>Engine: getWorkItems(caseId)
        Engine-->>MCP: Work items list
        MCP-->>ZAI: Function result
        ZAI->>HTTP: Continue with result
        GLM4-->>HTTP: "There are 3 pending tasks..."
        HTTP-->>ZAI: Response
        ZAI-->>MCP: Formatted response
        MCP-->>User: "3 pending tasks: Review Order, Check Inventory, Ship"
    end

    Note over User,Engine: Phase 4 - Error Recovery

    rect rgb(229, 62, 62, 0.15)
        User->>MCP: "Cancel the workflow"
        MCP->>ZAI: processWithFunctions(nlText, context)
        ZAI->>HTTP: POST request
        HTTP->>GLM4: Request
        GLM4-->>HTTP: Function call: cancel_workflow(case_id=unknown)
        HTTP-->>ZAI: Function call (missing case_id)
        ZAI->>ZAI: Detect missing required parameter
        ZAI->>HTTP: Ask for clarification
        GLM4-->>HTTP: "Which workflow would you like to cancel?"
        HTTP-->>ZAI: Clarification request
        ZAI-->>MCP: Need user input
        MCP-->>User: "Which workflow? You have: Case_12345, Case_67890"
    end

    Note over User,Engine: Phase 5 - Z.AI Configuration

    rect rgb(160, 174, 192, 0.15)
        Note over ZAI,GLM4: Configuration Requirements
        Note right of ZAI: ZHIPU_API_KEY environment variable<br/>HTTP/2 REST client<br/>Function schema definitions
        Note right of HTTP: Base URL: api.zhihui.ai<br/>Model: glm-4<br/>Timeout: 30s
    end

    %% Z.AI Integration Details:
    %% - Model: GLM-4 (glm-4-plus for production)
    %% - API: api.zhihui.ai
    %% - Auth: ZHIPU_API_KEY environment variable
    %% - Function Calling: OpenAI-compatible schema
    %% - Context: Session-based conversation history
MERMAID

    # Replace placeholders
    sed -i.bak \
        -e "s|TIMESTAMP_PLACEHOLDER|${timestamp}|g" \
        -e "s|COMMIT_PLACEHOLDER|$(git_commit)|g" \
        "$out" && rm -f "${out}.bak"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_zai_flow_mermaid" "$op_elapsed"
    log_ok "Z.AI flow diagram written to $out"
}

# ── emit_all_integration_flows ───────────────────────────────────────────────
# Main dispatcher to emit all integration flow diagrams.
#
# Generates:
#   - MCP flow sequence diagram
#   - A2A flow sequence diagram
#   - Z.AI flow sequence diagram
#   - Integration YAWL XML specification
# ─────────────────────────────────────────────────────────────────────────────
emit_all_integration_flows() {
    timer_start
    record_memory "integration_flows_start"

    # Ensure output directories exist
    mkdir -p "$DIAGRAMS_DIR" "$YAWL_DIR"

    # Emit all diagrams
    emit_mcp_flow_mermaid
    emit_a2a_flow_mermaid
    emit_zai_flow_mermaid
    emit_integration_yawl

    INTEGRATION_FLOWS_ELAPSED=$(timer_elapsed_ms)
    record_phase_timing "integration_flows" "$INTEGRATION_FLOWS_ELAPSED"
    log_ok "All integration flow diagrams emitted in ${INTEGRATION_FLOWS_ELAPSED}ms"
}

# Export functions for use by other scripts
export -f emit_mcp_flow_mermaid
export -f emit_a2a_flow_mermaid
export -f emit_integration_yawl
export -f emit_zai_flow_mermaid
export -f emit_all_integration_flows
