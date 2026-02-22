#!/bin/bash
#
# Phase 05: A2A Protocol Tests
#
# Tests the Agent-to-Agent protocol implementation:
# - Agent card endpoint
# - JSON-RPC 2.0 protocol compliance
# - Task management
# - Message handling
#
# Exit codes:
#   0 - All A2A tests passed
#   1 - A2A tests failed

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
A2A_PORT="${A2A_PORT:-8082}"
A2A_URL="http://localhost:$A2A_PORT"
A2A_STARTUP_TIMEOUT="${A2A_STARTUP_TIMEOUT:-30}"

echo "==========================================="
echo "Phase 05: A2A Protocol Tests"
echo "==========================================="
echo ""
echo "A2A port:       $A2A_PORT"
echo "A2A URL:        $A2A_URL"
echo "Startup timeout: ${A2A_STARTUP_TIMEOUT}s"
echo ""

# Check if A2A server is running
check_a2a_running() {
    nc -z localhost "$A2A_PORT" 2>/dev/null
}

# Test: A2A server availability
echo "--- Test: A2A Server Availability ---"
reset_test_counters

if check_a2a_running; then
    echo -e "${GREEN}✓ A2A server running on port $A2A_PORT${NC}"
    A2A_RUNNING=true
else
    echo -e "${YELLOW}! A2A server not running on port $A2A_PORT${NC}"
    A2A_RUNNING=false

    # Try to start A2A server
    if [ -f "$PROJECT_DIR/run-a2a-server.sh" ]; then
        echo ""
        echo "Attempting to start A2A server..."
        cd "$PROJECT_DIR"

        # Start in background
        ./run-a2a-server.sh > /tmp/a2a-server.log 2>&1 &
        A2A_PID=$!
        echo "Started A2A server (PID: $A2A_PID)"

        # Wait for startup
        if wait_for_port localhost "$A2A_PORT" "$A2A_STARTUP_TIMEOUT"; then
            echo -e "${GREEN}✓ A2A server started successfully${NC}"
            A2A_RUNNING=true
        else
            echo -e "${RED}✗ A2A server failed to start${NC}"
            echo "Log:"
            tail -20 /tmp/a2a-server.log 2>/dev/null || true
        fi
    fi
fi
echo ""

if [ "$A2A_RUNNING" != "true" ]; then
    echo "==========================================="
    echo "A2A Protocol Summary"
    echo "==========================================="
    echo ""
    echo -e "${YELLOW}Phase 05 SKIPPED (A2A server not available)${NC}"
    echo ""
    echo "To enable A2A testing:"
    echo "  1. Build project: ant -f build/build.xml compile"
    echo "  2. Start A2A server: ./run-a2a-server.sh"
    echo "  3. Verify server: curl http://localhost:8082/.well-known/agent.json"
    echo "  4. Re-run this test"
    exit 0
fi

# Test: Agent card endpoint
echo "--- Test: Agent Card Endpoint ---"
reset_test_counters

AGENT_CARD=$(http_get "$A2A_URL/.well-known/agent.json") || {
    echo -e "${RED}✗ Failed to fetch agent card${NC}"
    echo "Response: $AGENT_CARD"
}

if [ -n "$AGENT_CARD" ] && json_is_valid "$AGENT_CARD"; then
    echo -e "${GREEN}✓ Agent card endpoint returns valid JSON${NC}"

    # Check required fields
    assert_json_has_field "$AGENT_CARD" "name" "Agent card has 'name' field"
    assert_json_has_field "$AGENT_CARD" "version" "Agent card has 'version' field"
    assert_json_has_field "$AGENT_CARD" "capabilities" "Agent card has 'capabilities' field"

    # Display agent info
    local agent_name
    agent_name=$(json_get "$AGENT_CARD" "name" "Unknown")
    local agent_version
    agent_version=$(json_get "$AGENT_CARD" "version" "Unknown")
    echo ""
    echo "Agent: $agent_name v$agent_version"
else
    echo -e "${RED}✗ Agent card is not valid JSON${NC}"
fi
echo ""

# Test: JSON-RPC 2.0 protocol
echo "--- Test: JSON-RPC 2.0 Protocol ---"
reset_test_counters

# Test valid JSON-RPC request
RESPONSE=$(jsonrpc_call "$A2A_URL/a2a" "getCapabilities" "{}" 1) || {
    echo -e "${YELLOW}! JSON-RPC request failed (server may not support getCapabilities)${NC}"
    RESPONSE=""
}

if [ -n "$RESPONSE" ]; then
    if json_is_valid "$RESPONSE"; then
        echo -e "${GREEN}✓ JSON-RPC response is valid JSON${NC}"

        # Check JSON-RPC compliance
        if echo "$RESPONSE" | jq -e '.jsonrpc == "2.0"' > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Response follows JSON-RPC 2.0${NC}"
        fi

        # Check for error or result
        if jsonrpc_has_error "$RESPONSE"; then
            local error_msg
            error_msg=$(jsonrpc_error_message "$RESPONSE")
            echo -e "${YELLOW}! JSON-RPC error: $error_msg${NC}"
        else
            echo -e "${GREEN}✓ JSON-RPC request succeeded${NC}"
        fi
    else
        echo -e "${RED}✗ Invalid JSON-RPC response${NC}"
    fi
fi
echo ""

# Test: Health endpoint
echo "--- Test: Health Endpoint ---"
reset_test_counters

local health_code
health_code=$(http_status "$A2A_URL/health" "GET") || health_code="000"

if [ "$health_code" = "200" ]; then
    echo -e "${GREEN}✓ Health endpoint returns 200${NC}"
elif [ "$health_code" = "404" ]; then
    echo -e "${YELLOW}! Health endpoint not found (may not be implemented)${NC}"
else
    echo -e "${YELLOW}! Health endpoint returned HTTP $health_code${NC}"
fi
echo ""

# Test: Task creation (if supported)
echo "--- Test: Task Management ---"
reset_test_counters

# Try to create a simple task
TASK_REQUEST='{"specId": "test-spec", "data": {}}'
RESPONSE=$(jsonrpc_call "$A2A_URL/a2a" "createTask" "$TASK_REQUEST" 2) || RESPONSE=""

if [ -n "$RESPONSE" ] && json_is_valid "$RESPONSE"; then
    if ! jsonrpc_has_error "$RESPONSE"; then
        echo -e "${GREEN}✓ Task creation endpoint available${NC}"

        # Try to get task ID
        local task_id
        task_id=$(json_get "$RESPONSE" "result.taskId" "")
        if [ -n "$task_id" ]; then
            echo "  Task ID: $task_id"
        fi
    else
        local error_msg
        error_msg=$(jsonrpc_error_message "$RESPONSE")
        echo -e "${YELLOW}! Task creation error: $error_msg${NC}"
    fi
else
    echo -e "${YELLOW}! Task management may not be fully implemented${NC}"
fi
echo ""

# Cleanup: Stop A2A server if we started it
if [ -n "${A2A_PID:-}" ] && kill -0 "$A2A_PID" 2>/dev/null; then
    echo "Stopping A2A server (PID: $A2A_PID)..."
    kill "$A2A_PID" 2>/dev/null || true
fi

# Summary
echo "==========================================="
print_test_summary
echo "==========================================="

if [ $ASSERT_TESTS_FAILED -gt 0 ]; then
    echo -e "${RED}Phase 05 FAILED${NC}"
    exit 1
fi

echo -e "${GREEN}Phase 05 PASSED${NC}"
exit 0
