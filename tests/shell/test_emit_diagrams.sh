#!/usr/bin/env bash
# ==========================================================================
# test_emit_diagrams.sh - Unit tests for observatory/lib/emit-diagrams.sh
#
# Tests diagram emission functions:
# - emit_reactor_diagram
# - emit_mcp_architecture_diagram
# - emit_a2a_topology_diagram
# - emit_agent_capabilities_diagram
# - emit_protocol_sequences_diagram
# - emit_all_diagrams
#
# Run with: bash test_emit_diagrams.sh
# ==========================================================================

# Determine shunit2 location
if command -v shunit2 >/dev/null 2>&1; then
    SHUNIT2="$(command -v shunit2)"
elif [[ -f "/usr/share/shunit2/shunit2" ]]; then
    SHUNIT2="/usr/share/shunit2/shunit2"
elif [[ -f "/usr/local/share/shunit2/shunit2" ]]; then
    SHUNIT2="/usr/local/share/shunit2/shunit2"
elif [[ -f "${HOME}/.local/share/shunit2/shunit2" ]]; then
    SHUNIT2="${HOME}/.local/share/shunit2/shunit2"
elif [[ -f "$(dirname "${BASH_SOURCE[0]}")/shunit2" ]]; then
    SHUNIT2="$(dirname "${BASH_SOURCE[0]}")/shunit2"
else
    echo "ERROR: shunit2 not found. Install with: apt-get install shunit2 or brew install shunit2"
    exit 1
fi

# Test constants
DIAGRAMS_SCRIPT="/Users/sac/cre/vendors/yawl/scripts/observatory/lib/emit-diagrams.sh"
UTIL_SCRIPT="/Users/sac/cre/vendors/yawl/scripts/observatory/lib/util.sh"
PROJECT_DIR="/Users/sac/cre/vendors/yawl"
TEMP_DIR=""

# ---------------------------------------------------------------------------
# Test fixture setup/teardown
# ---------------------------------------------------------------------------

oneTimeSetUp() {
    echo "Setting up emit-diagrams.sh test environment..."
    TEMP_DIR=$(mktemp -d)

    # Create directory structure
    mkdir -p "${TEMP_DIR}/docs/v6/latest/facts"
    mkdir -p "${TEMP_DIR}/docs/v6/latest/diagrams/yawl"
    mkdir -p "${TEMP_DIR}/docs/v6/latest/receipts"
    mkdir -p "${TEMP_DIR}/yawl-utilities/src/main/java/org/yawlfoundation/yawl/util"
    mkdir -p "${TEMP_DIR}/yawl-engine/src/main/java/org/yawlfoundation/yawl/engine"
    mkdir -p "${TEMP_DIR}/src/org/yawlfoundation/yawl/util"
    mkdir -p "${TEMP_DIR}/src/org/yawlfoundation/yawl/engine"

    # Create root pom.xml
    cat > "${TEMP_DIR}/pom.xml" << 'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-parent</artifactId>
    <version>6.0.0-Beta</version>
    <packaging>pom</packaging>
    <modules>
        <module>yawl-utilities</module>
        <module>yawl-engine</module>
    </modules>
</project>
POM

    # Create module pom.xml files
    cat > "${TEMP_DIR}/yawl-utilities/pom.xml" << 'POM'
<?xml version="1.0"?>
<project>
    <parent>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-parent</artifactId>
        <version>6.0.0-Beta</version>
    </parent>
    <artifactId>yawl-utilities</artifactId>
    <sourceDirectory>../src</sourceDirectory>
</project>
POM

    cat > "${TEMP_DIR}/yawl-engine/pom.xml" << 'POM'
<?xml version="1.0"?>
<project>
    <parent>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-parent</artifactId>
        <version>6.0.0-Beta</version>
    </parent>
    <artifactId>yawl-engine</artifactId>
    <sourceDirectory>../src</sourceDirectory>
</project>
POM

    # Create test Java files
    echo "package org.yawlfoundation.yawl.util; public class Util {}" > \
        "${TEMP_DIR}/src/org/yawlfoundation/yawl/util/Util.java"
    echo "package org.yawlfoundation.yawl.engine; public class Engine {}" > \
        "${TEMP_DIR}/src/org/yawlfoundation/yawl/engine/Engine.java"

    # Create minimal reactor.json for dependency testing
    echo '{"module_deps":[{"from":"yawl-engine","to":"yawl-utilities"}]}' > \
        "${TEMP_DIR}/docs/v6/latest/facts/reactor.json"
}

oneTimeTearDown() {
    echo "Tearing down emit-diagrams.sh test environment..."
    if [[ -n "${TEMP_DIR}" && -d "${TEMP_DIR}" ]]; then
        rm -rf "${TEMP_DIR}"
    fi
}

setUp() {
    # Set up environment variables
    REPO_ROOT="${TEMP_DIR}"
    OUT_DIR="${REPO_ROOT}/docs/v6/latest"
    FACTS_DIR="${OUT_DIR}/facts"
    DIAGRAMS_DIR="${OUT_DIR}/diagrams"
    YAWL_DIR="${DIAGRAMS_DIR}/yawl"
    RECEIPTS_DIR="${OUT_DIR}/receipts"

    # Reset arrays
    REFUSALS=()
    WARNINGS=()

    # Source utilities first
    source "${UTIL_SCRIPT}"
    # Override REPO_ROOT
    REPO_ROOT="${TEMP_DIR}"
    OUT_DIR="${REPO_ROOT}/docs/v6/latest"
    FACTS_DIR="${OUT_DIR}/facts"
    DIAGRAMS_DIR="${OUT_DIR}/diagrams"
    YAWL_DIR="${DIAGRAMS_DIR}/yawl"
    RECEIPTS_DIR="${OUT_DIR}/receipts"

    # Source emit-diagrams functions
    source "${DIAGRAMS_SCRIPT}"
}

tearDown() {
    :
}

# ---------------------------------------------------------------------------
# Helper: Check Mermaid syntax
# ---------------------------------------------------------------------------
mermaid_has_valid_start() {
    local file="$1"
    local first_line
    first_line=$(head -1 "${file}")

    # Valid starts: graph, flowchart, %%{init, sequenceDiagram
    # Note: sequenceDiagram may be on second line after %%{init} directive
    if [[ "${first_line}" =~ ^(graph|flowchart|%%\{init|sequenceDiagram) ]]; then
        return 0
    fi

    # Check if second line is sequenceDiagram (Mermaid init directive case)
    local second_line
    second_line=$(sed -n '2p' "${file}")
    if [[ "${second_line}" =~ ^sequenceDiagram ]]; then
        return 0
    fi

    return 1
}

# ---------------------------------------------------------------------------
# Test: emit_reactor_diagram
# ---------------------------------------------------------------------------

test_emit_reactor_diagram_creates_file() {
    emit_reactor_diagram

    assertTrue "10-maven-reactor.mmd should exist" "[[ -f ${DIAGRAMS_DIR}/10-maven-reactor.mmd ]]"
}

test_emit_reactor_diagram_valid_mermaid() {
    emit_reactor_diagram

    assertTrue "Should have valid Mermaid start" "mermaid_has_valid_start ${DIAGRAMS_DIR}/10-maven-reactor.mmd"
}

test_emit_reactor_diagram_has_parent_node() {
    emit_reactor_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/10-maven-reactor.mmd")

    assertContains "Should have PARENT node" "${content}" "PARENT"
    assertContains "Should have version" "${content}" "6.0.0-Beta"
}

test_emit_reactor_diagram_has_modules() {
    emit_reactor_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/10-maven-reactor.mmd")

    assertContains "Should have yawl_utilities" "${content}" "yawl_utilities"
    assertContains "Should have yawl_engine" "${content}" "yawl_engine"
}

test_emit_reactor_diagram_has_class_definitions() {
    emit_reactor_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/10-maven-reactor.mmd")

    assertContains "Should have classDef" "${content}" "classDef"
    assertContains "Should have fullshared style" "${content}" "fullshared"
    assertContains "Should have pkgscoped style" "${content}" "pkgscoped"
}

test_emit_reactor_diagram_has_legend() {
    emit_reactor_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/10-maven-reactor.mmd")

    assertContains "Should have Legend" "${content}" "Legend"
}

# ---------------------------------------------------------------------------
# Test: emit_mcp_architecture_diagram
# ---------------------------------------------------------------------------

test_emit_mcp_architecture_diagram_creates_file() {
    emit_mcp_architecture_diagram

    assertTrue "60-mcp-architecture.mmd should exist" "[[ -f ${DIAGRAMS_DIR}/60-mcp-architecture.mmd ]]"
}

test_emit_mcp_architecture_diagram_valid_mermaid() {
    emit_mcp_architecture_diagram

    assertTrue "Should have valid Mermaid start" "mermaid_has_valid_start ${DIAGRAMS_DIR}/60-mcp-architecture.mmd"
}

test_emit_mcp_architecture_diagram_has_clients() {
    emit_mcp_architecture_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/60-mcp-architecture.mmd")

    assertContains "Should have Clients subgraph" "${content}" "Clients"
    assertContains "Should have Claude Desktop" "${content}" "Claude Desktop"
}

test_emit_mcp_architecture_diagram_has_server() {
    emit_mcp_architecture_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/60-mcp-architecture.mmd")

    assertContains "Should have YawlMcpServer" "${content}" "YawlMcpServer"
    assertContains "Should have v5.2" "${content}" "v5.2"
}

test_emit_mcp_architecture_diagram_has_capabilities() {
    emit_mcp_architecture_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/60-mcp-architecture.mmd")

    assertContains "Should have Tools" "${content}" "Tools"
    assertContains "Should have Resources" "${content}" "Resources"
    assertContains "Should have Prompts" "${content}" "Prompts"
}

test_emit_mcp_architecture_diagram_has_zai() {
    emit_mcp_architecture_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/60-mcp-architecture.mmd")

    assertContains "Should have ZAI subgraph" "${content}" "Z.AI"
    assertContains "Should have ZaiFunctionService" "${content}" "ZaiFunctionService"
}

# ---------------------------------------------------------------------------
# Test: emit_a2a_topology_diagram
# ---------------------------------------------------------------------------

test_emit_a2a_topology_diagram_creates_file() {
    emit_a2a_topology_diagram

    assertTrue "65-a2a-topology.mmd should exist" "[[ -f ${DIAGRAMS_DIR}/65-a2a-topology.mmd ]]"
}

test_emit_a2a_topology_diagram_valid_mermaid() {
    emit_a2a_topology_diagram

    assertTrue "Should have valid Mermaid start" "mermaid_has_valid_start ${DIAGRAMS_DIR}/65-a2a-topology.mmd"
}

test_emit_a2a_topology_diagram_has_agents() {
    emit_a2a_topology_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/65-a2a-topology.mmd")

    assertContains "Should have ExternalAgents" "${content}" "ExternalAgents"
    assertContains "Should have Planning Agent" "${content}" "Planning Agent"
}

test_emit_a2a_topology_diagram_has_server() {
    emit_a2a_topology_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/65-a2a-topology.mmd")

    assertContains "Should have YawlA2AServer" "${content}" "YawlA2AServer"
    assertContains "Should have YawlAgentExecutor" "${content}" "YawlAgentExecutor"
}

test_emit_a2a_topology_diagram_has_skills() {
    emit_a2a_topology_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/65-a2a-topology.mmd")

    assertContains "Should have launch_workflow" "${content}" "launch_workflow"
    assertContains "Should have query_workflows" "${content}" "query_workflows"
}

test_emit_a2a_topology_diagram_has_auth() {
    emit_a2a_topology_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/65-a2a-topology.mmd")

    assertContains "Should have Authentication" "${content}" "Authentication"
    assertContains "Should have mTLS" "${content}" "mTLS"
}

# ---------------------------------------------------------------------------
# Test: emit_agent_capabilities_diagram
# ---------------------------------------------------------------------------

test_emit_agent_capabilities_diagram_creates_file() {
    emit_agent_capabilities_diagram

    assertTrue "70-agent-capabilities.mmd should exist" "[[ -f ${DIAGRAMS_DIR}/70-agent-capabilities.mmd ]]"
}

test_emit_agent_capabilities_diagram_valid_mermaid() {
    emit_agent_capabilities_diagram

    assertTrue "Should have valid Mermaid start" "mermaid_has_valid_start ${DIAGRAMS_DIR}/70-agent-capabilities.mmd"
}

test_emit_agent_capabilities_diagram_has_mcp_capabilities() {
    emit_agent_capabilities_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/70-agent-capabilities.mmd")

    assertContains "Should have MCPCapabilities" "${content}" "MCPCapabilities"
    assertContains "Should have 15+ Tools" "${content}" "15+ Tools"
}

test_emit_agent_capabilities_diagram_has_a2a_capabilities() {
    emit_agent_capabilities_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/70-agent-capabilities.mmd")

    assertContains "Should have A2ACapabilities" "${content}" "A2ACapabilities"
    assertContains "Should have 4 Skills" "${content}" "4 Skills"
}

test_emit_agent_capabilities_diagram_has_shared_capabilities() {
    emit_agent_capabilities_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/70-agent-capabilities.mmd")

    assertContains "Should have SharedCapabilities" "${content}" "SharedCapabilities"
    assertContains "Should have InterfaceB API" "${content}" "InterfaceB API"
}

test_emit_agent_capabilities_diagram_has_zai_capabilities() {
    emit_agent_capabilities_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/70-agent-capabilities.mmd")

    assertContains "Should have ZAICapabilities" "${content}" "ZAICapabilities"
    assertContains "Should have Function Calling" "${content}" "Function Calling"
}

# ---------------------------------------------------------------------------
# Test: emit_protocol_sequences_diagram
# ---------------------------------------------------------------------------

test_emit_protocol_sequences_diagram_creates_file() {
    emit_protocol_sequences_diagram

    assertTrue "75-protocol-sequences.mmd should exist" "[[ -f ${DIAGRAMS_DIR}/75-protocol-sequences.mmd ]]"
}

test_emit_protocol_sequences_diagram_valid_mermaid() {
    emit_protocol_sequences_diagram

    assertTrue "Should have valid Mermaid start" "mermaid_has_valid_start ${DIAGRAMS_DIR}/75-protocol-sequences.mmd"
}

test_emit_protocol_sequences_diagram_has_sequence_diagram() {
    emit_protocol_sequences_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/75-protocol-sequences.mmd")

    assertContains "Should be sequenceDiagram" "${content}" "sequenceDiagram"
    assertContains "Should have participants" "${content}" "participant"
}

test_emit_protocol_sequences_diagram_has_mcp_flow() {
    emit_protocol_sequences_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/75-protocol-sequences.mmd")

    assertContains "Should mention MCP Tool Invocation" "${content}" "MCP Tool Invocation"
    assertContains "Should have initialize request" "${content}" "initialize request"
}

test_emit_protocol_sequences_diagram_has_a2a_flow() {
    emit_protocol_sequences_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/75-protocol-sequences.mmd")

    assertContains "Should mention A2A Message Flow" "${content}" "A2A Message Flow"
    assertContains "Should have agent.json" "${content}" "agent.json"
}

test_emit_protocol_sequences_diagram_has_zai_flow() {
    emit_protocol_sequences_diagram

    local content
    content=$(cat "${DIAGRAMS_DIR}/75-protocol-sequences.mmd")

    assertContains "Should mention Z.AI Enhanced Flow" "${content}" "Z.AI Enhanced Flow"
    assertContains "Should have processWithFunctions" "${content}" "processWithFunctions"
}

# ---------------------------------------------------------------------------
# Test: emit_all_diagrams
# ---------------------------------------------------------------------------

test_emit_all_diagrams_creates_all_files() {
    emit_all_diagrams

    assertTrue "10-maven-reactor.mmd" "[[ -f ${DIAGRAMS_DIR}/10-maven-reactor.mmd ]]"
    assertTrue "60-mcp-architecture.mmd" "[[ -f ${DIAGRAMS_DIR}/60-mcp-architecture.mmd ]]"
    assertTrue "65-a2a-topology.mmd" "[[ -f ${DIAGRAMS_DIR}/65-a2a-topology.mmd ]]"
    assertTrue "70-agent-capabilities.mmd" "[[ -f ${DIAGRAMS_DIR}/70-agent-capabilities.mmd ]]"
    assertTrue "75-protocol-sequences.mmd" "[[ -f ${DIAGRAMS_DIR}/75-protocol-sequences.mmd ]]"
}

test_emit_all_diagrams_sets_timing() {
    emit_all_diagrams

    assertNotNull "DIAGRAMS_ELAPSED should be set" "${DIAGRAMS_ELAPSED}"
    assertTrue "DIAGRAMS_ELAPSED should be >= 0" "[[ ${DIAGRAMS_ELAPSED:-0} -ge 0 ]]"
}

# ---------------------------------------------------------------------------
# Run tests
# ---------------------------------------------------------------------------

# Source shunit2
. "${SHUNIT2}"
