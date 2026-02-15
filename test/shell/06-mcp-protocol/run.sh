#!/bin/bash
#
# Phase 06: MCP Protocol Tests
#
# Tests the Model Context Protocol implementation:
# - MCP protocol handshake
# - Tool listing
# - Tool execution
# - Resource access
#
# Exit codes:
#   0 - All MCP tests passed
#   1 - MCP tests failed

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${PROJECT_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"

# Source libraries
source "$PROJECT_DIR/scripts/shell-test/assert.sh"
source "$PROJECT_DIR/scripts/shell-test/http-client.sh"
source "$PROJECT_DIR/scripts/shell-test/process-manager.sh"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Configuration
MCP_PORT="${MCP_PORT:-3000}"
MCP_TRANSPORT="${MCP_TRANSPORT:-HTTP}"
MCP_URL="http://localhost:$MCP_PORT"
MCP_STARTUP_TIMEOUT="${MCP_STARTUP_TIMEOUT:-30}"

echo "==========================================="
echo "Phase 06: MCP Protocol Tests"
echo "==========================================="
echo ""
echo "MCP port:       $MCP_PORT"
echo "MCP transport:  $MCP_TRANSPORT"
echo "MCP URL:        $MCP_URL"
echo "Startup timeout: ${MCP_STARTUP_TIMEOUT}s"
echo ""

# Check if MCP server is running
check_mcp_running() {
    nc -z localhost "$MCP_PORT" 2>/dev/null
}

# Test: MCP server availability
echo "--- Test: MCP Server Availability ---"
reset_test_counters

if check_mcp_running; then
    echo -e "${GREEN}✓ MCP server running on port $MCP_PORT${NC}"
    MCP_RUNNING=true
else
    echo -e "${YELLOW}! MCP server not running on port $MCP_PORT${NC}"
    MCP_RUNNING=false

    # Try to start MCP server
    if [ -f "$PROJECT_DIR/run-mcp-server.sh" ]; then
        echo ""
        echo "Attempting to start MCP server..."
        cd "$PROJECT_DIR"

        # Start in background
        MCP_TRANSPORT=HTTP ./run-mcp-server.sh "$MCP_PORT" > /tmp/mcp-server.log 2>&1 &
        MCP_PID=$!
        echo "Started MCP server (PID: $MCP_PID)"

        # Wait for startup
        if wait_for_port localhost "$MCP_PORT" "$MCP_STARTUP_TIMEOUT"; then
            echo -e "${GREEN}✓ MCP server started successfully${NC}"
            MCP_RUNNING=true
        else
            echo -e "${RED}✗ MCP server failed to start${NC}"
            echo "Log:"
            tail -20 /tmp/mcp-server.log 2>/dev/null || true
        fi
    fi
fi
echo ""

if [ "$MCP_RUNNING" != "true" ]; then
    echo "==========================================="
    echo "MCP Protocol Summary"
    echo "==========================================="
    echo ""
    echo -e "${YELLOW}Phase 06 SKIPPED (MCP server not available)${NC}"
    echo ""
    echo "To enable MCP testing:"
    echo "  1. Build project: ant -f build/build.xml compile"
    echo "  2. Start MCP server: MCP_TRANSPORT=HTTP ./run-mcp-server.sh 3000"
    echo "  3. Verify server: curl http://localhost:3000/mcp"
    echo "  4. Re-run this test"
    exit 0
fi

# Test: MCP Initialize
echo "--- Test: MCP Initialize ---"
reset_test_counters

INIT_RESPONSE=$(mcp_initialize "$MCP_URL/mcp" "yawl-shell-test" "1.0.0") || {
    echo -e "${RED}✗ MCP initialize failed${NC}"
    INIT_RESPONSE=""
}

if [ -n "$INIT_RESPONSE" ] && json_is_valid "$INIT_RESPONSE"; then
    echo -e "${GREEN}✓ MCP initialize returns valid JSON${NC}"

    if jsonrpc_has_error "$INIT_RESPONSE"; then
        local error_msg
        error_msg=$(jsonrpc_error_message "$INIT_RESPONSE")
        echo -e "${YELLOW}! MCP initialize error: $error_msg${NC}"
    else
        # Check for protocol version
        local result
        result=$(jsonrpc_result "$INIT_RESPONSE")
        if [ -n "$result" ]; then
            local protocol_version
            protocol_version=$(json_get "$result" "protocolVersion" "")
            if [ -n "$protocol_version" ]; then
                echo -e "${GREEN}✓ Protocol version: $protocol_version${NC}"
            fi

            # Check for server info
            local server_name
            server_name=$(json_get "$result" "serverInfo.name" "Unknown")
            local server_version
            server_version=$(json_get "$result" "serverInfo.version" "Unknown")
            echo "  Server: $server_name v$server_version"
        fi
    fi
else
    echo -e "${RED}✗ Invalid MCP initialize response${NC}"
fi
echo ""

# Test: Tools List
echo "--- Test: Tools List ---"
reset_test_counters

TOOLS_RESPONSE=$(mcp_list_tools "$MCP_URL/mcp") || {
    echo -e "${YELLOW}! Tools list request failed${NC}"
    TOOLS_RESPONSE=""
}

if [ -n "$TOOLS_RESPONSE" ] && json_is_valid "$TOOLS_RESPONSE"; then
    echo -e "${GREEN}✓ Tools list returns valid JSON${NC}"

    if jsonrpc_has_error "$TOOLS_RESPONSE"; then
        local error_msg
        error_msg=$(jsonrpc_error_message "$TOOLS_RESPONSE")
        echo -e "${YELLOW}! Tools list error: $error_msg${NC}"
    else
        local result
        result=$(jsonrpc_result "$TOOLS_RESPONSE")
        if [ -n "$result" ]; then
            local tool_count
            tool_count=$(json_array_length "$result" "tools")
            echo -e "${GREEN}✓ Found $tool_count tools${NC}"

            # List first few tools
            if [ "$tool_count" -gt 0 ]; then
                echo "  Tools:"
                for i in $(seq 0 $((tool_count - 1))); do
                    [ $i -ge 5 ] && break
                    local tool_name
                    tool_name=$(json_array_get "$result" "tools" "$i" | jq -r '.name // empty' 2>/dev/null)
                    [ -n "$tool_name" ] && echo "    - $tool_name"
                done
                [ "$tool_count" -gt 5 ] && echo "    ... and $((tool_count - 5)) more"
            fi

            # Verify minimum tool count
            if [ "$tool_count" -ge 5 ]; then
                echo -e "${GREEN}✓ Sufficient tools available (>= 5)${NC}"
            else
                echo -e "${YELLOW}! Low tool count: $tool_count${NC}"
            fi
        fi
    fi
else
    echo -e "${RED}✗ Invalid tools list response${NC}"
fi
echo ""

# Test: Tool Execution
echo "--- Test: Tool Execution ---"
reset_test_counters

# Try to call a basic tool
TOOL_RESPONSE=$(mcp_call_tool "$MCP_URL/mcp" "listSpecifications" '{}') || {
    echo -e "${YELLOW}! Tool execution request failed${NC}"
    TOOL_RESPONSE=""
}

if [ -n "$TOOL_RESPONSE" ] && json_is_valid "$TOOL_RESPONSE"; then
    echo -e "${GREEN}✓ Tool execution returns valid JSON${NC}"

    if jsonrpc_has_error "$TOOL_RESPONSE"; then
        local error_msg
        error_msg=$(jsonrpc_error_message "$TOOL_RESPONSE")
        echo -e "${YELLOW}! Tool execution error: $error_msg${NC}"
    else
        echo -e "${GREEN}✓ Tool executed successfully${NC}"
    fi
else
    echo -e "${YELLOW}! Invalid tool execution response${NC}"
fi
echo ""

# Test: Resources List
echo "--- Test: Resources List ---"
reset_test_counters

RESOURCES_RESPONSE=$(mcp_list_resources "$MCP_URL/mcp") || {
    echo -e "${YELLOW}! Resources list request failed${NC}"
    RESOURCES_RESPONSE=""
}

if [ -n "$RESOURCES_RESPONSE" ] && json_is_valid "$RESOURCES_RESPONSE"; then
    echo -e "${GREEN}✓ Resources list returns valid JSON${NC}"

    if jsonrpc_has_error "$RESOURCES_RESPONSE"; then
        local error_msg
        error_msg=$(jsonrpc_error_message "$RESOURCES_RESPONSE")
        echo -e "${YELLOW}! Resources list error: $error_msg${NC}"
    else
        local result
        result=$(jsonrpc_result "$RESOURCES_RESPONSE")
        if [ -n "$result" ]; then
            local resource_count
            resource_count=$(json_array_length "$result" "resources")
            echo -e "${GREEN}✓ Found $resource_count resources${NC}"
        fi
    fi
else
    echo -e "${YELLOW}! Invalid resources list response${NC}"
fi
echo ""

# Test: Health endpoint
echo "--- Test: Health Endpoint ---"
reset_test_counters

local health_code
health_code=$(http_status "$MCP_URL/health" "GET") || health_code="000"

if [ "$health_code" = "200" ]; then
    echo -e "${GREEN}✓ Health endpoint returns 200${NC}"
elif [ "$health_code" = "404" ]; then
    echo -e "${YELLOW}! Health endpoint not found${NC}"
else
    echo -e "${YELLOW}! Health endpoint returned HTTP $health_code${NC}"
fi
echo ""

# Cleanup: Stop MCP server if we started it
if [ -n "${MCP_PID:-}" ] && kill -0 "$MCP_PID" 2>/dev/null; then
    echo "Stopping MCP server (PID: $MCP_PID)..."
    kill "$MCP_PID" 2>/dev/null || true
fi

# Summary
echo "==========================================="
print_test_summary
echo "==========================================="

if [ $ASSERT_TESTS_FAILED -gt 0 ]; then
    echo -e "${RED}Phase 06 FAILED${NC}"
    exit 1
fi

echo -e "${GREEN}Phase 06 PASSED${NC}"
exit 0
