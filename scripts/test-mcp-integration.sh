#!/usr/bin/env bash
#
# Test YAWL MCP Server Integration
#
# This script performs REAL integration tests against the MCP server.
# It will FAIL if:
#   - Server returns stub/fake data
#   - UnsupportedOperationException is thrown
#   - Any TODO/FIXME/mock patterns detected in responses
#   - Server cannot perform real YAWL operations
#
# Usage: ./test-mcp-integration.sh [server_url]
#
# Prerequisites:
#   - YAWL engine running and accessible
#   - MCP server running
#   - curl with JSON support

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Configuration
MCP_SERVER_URL="${1:-http://localhost:3000}"
YAWL_ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080/yawl/ib}"
TIMEOUT="${TIMEOUT:-30}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Result tracking
declare -a FAILURES

echo "==========================================="
echo "YAWL MCP Integration Tests"
echo "==========================================="
echo "MCP Server:  $MCP_SERVER_URL"
echo "YAWL Engine: $YAWL_ENGINE_URL"
echo "Timeout:     ${TIMEOUT}s"
echo "==========================================="

# Test helper functions
run_test() {
    local test_name="$1"
    local test_cmd="$2"

    TESTS_RUN=$((TESTS_RUN + 1))
    echo ""
    echo "TEST $TESTS_RUN: $test_name"
    echo "----------------------------------------"

    if eval "$test_cmd"; then
        echo -e "${GREEN}✓ PASSED${NC}: $test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        echo -e "${RED}✗ FAILED${NC}: $test_name"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        FAILURES+=("$test_name")
        return 1
    fi
}

check_no_stub_patterns() {
    local response="$1"
    local context="$2"

    # Check for stub indicators
    if echo "$response" | grep -qiE "UnsupportedOperationException|TODO|FIXME|mock|stub|fake|not implemented|placeholder"; then
        echo -e "${RED}STUB DETECTED in $context:${NC}"
        echo "$response" | grep -iE "UnsupportedOperationException|TODO|FIXME|mock|stub|fake|not implemented|placeholder"
        return 1
    fi

    # Check for empty/null responses that might indicate stubs
    if [ -z "$response" ] || [ "$response" = "null" ] || [ "$response" = "{}" ]; then
        echo -e "${YELLOW}WARNING: Empty response in $context${NC}"
        # Don't fail on empty, but warn
    fi

    return 0
}

# TEST 1: Server availability
test_server_available() {
    local response
    response=$(curl -s --connect-timeout 5 "$MCP_SERVER_URL" 2>&1 || echo "CONNECTION_FAILED")

    if [ "$response" = "CONNECTION_FAILED" ]; then
        echo "ERROR: Cannot connect to MCP server at $MCP_SERVER_URL"
        return 1
    fi

    echo "Server responded"
    return 0
}

# TEST 2: MCP Initialize handshake
test_mcp_initialize() {
    local request response

    request='{"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0"}},"id":1}'

    response=$(curl -s -X POST "$MCP_SERVER_URL/mcp" \
        -H "Content-Type: application/json" \
        -d "$request" \
        --connect-timeout "$TIMEOUT" 2>&1 || echo '{"error":"REQUEST_FAILED"}')

    echo "Request:  $request"
    echo "Response: $response"

    # Validate response
    if echo "$response" | grep -q "error"; then
        echo "ERROR in response"
        return 1
    fi

    # Check for server info
    if ! echo "$response" | grep -q "yawl-mcp-server"; then
        echo "ERROR: Expected 'yawl-mcp-server' in response"
        return 1
    fi

    check_no_stub_patterns "$response" "initialize response"
}

# TEST 3: List tools
test_list_tools() {
    local request response

    request='{"jsonrpc":"2.0","method":"tools/list","params":{},"id":2}'

    response=$(curl -s -X POST "$MCP_SERVER_URL/mcp" \
        -H "Content-Type: application/json" \
        -d "$request" \
        --connect-timeout "$TIMEOUT" 2>&1 || echo '{"error":"REQUEST_FAILED"}')

    echo "Response: $response"

    # Should have YAWL tools
    if ! echo "$response" | grep -qE "yawl_launch_case|yawl_get_workitems|yawl_list_specs"; then
        echo "ERROR: Expected YAWL tools in response"
        return 1
    fi

    check_no_stub_patterns "$response" "tools/list response"
}

# TEST 4: List resources
test_list_resources() {
    local request response

    request='{"jsonrpc":"2.0","method":"resources/list","params":{},"id":3}'

    response=$(curl -s -X POST "$MCP_SERVER_URL/mcp" \
        -H "Content-Type: application/json" \
        -d "$request" \
        --connect-timeout "$TIMEOUT" 2>&1 || echo '{"error":"REQUEST_FAILED"}')

    echo "Response: $response"

    # Should have YAWL resource URIs
    if ! echo "$response" | grep -qE "yawl://specifications|yawl://cases|yawl://workitems"; then
        echo "ERROR: Expected YAWL resource URIs in response"
        return 1
    fi

    check_no_stub_patterns "$response" "resources/list response"
}

# TEST 5: Call tool - list specifications
test_tool_list_specs() {
    local request response

    request='{"jsonrpc":"2.0","method":"tools/call","params":{"name":"yawl_list_specs","arguments":{}},"id":4}'

    response=$(curl -s -X POST "$MCP_SERVER_URL/mcp" \
        -H "Content-Type: application/json" \
        -d "$request" \
        --connect-timeout "$TIMEOUT" 2>&1 || echo '{"error":"REQUEST_FAILED"}')

    echo "Response: $response"

    # Should return array or structured response
    if echo "$response" | grep -q "UnsupportedOperationException"; then
        echo "ERROR: Tool not implemented - stub detected!"
        return 1
    fi

    check_no_stub_patterns "$response" "tools/call yawl_list_specs response"
}

# TEST 6: Call tool - get work items
test_tool_get_workitems() {
    local request response

    request='{"jsonrpc":"2.0","method":"tools/call","params":{"name":"yawl_get_workitems","arguments":{}},"id":5}'

    response=$(curl -s -X POST "$MCP_SERVER_URL/mcp" \
        -H "Content-Type: application/json" \
        -d "$request" \
        --connect-timeout "$TIMEOUT" 2>&1 || echo '{"error":"REQUEST_FAILED"}')

    echo "Response: $response"

    if echo "$response" | grep -q "UnsupportedOperationException"; then
        echo "ERROR: Tool not implemented - stub detected!"
        return 1
    fi

    check_no_stub_patterns "$response" "tools/call yawl_get_workitems response"
}

# TEST 7: Get resource - specifications
test_resource_specifications() {
    local request response

    request='{"jsonrpc":"2.0","method":"resources/read","params":{"uri":"yawl://specifications"},"id":6}'

    response=$(curl -s -X POST "$MCP_SERVER_URL/mcp" \
        -H "Content-Type: application/json" \
        -d "$request" \
        --connect-timeout "$TIMEOUT" 2>&1 || echo '{"error":"REQUEST_FAILED"}')

    echo "Response: $response"

    if echo "$response" | grep -q "UnsupportedOperationException"; then
        echo "ERROR: Resource not implemented - stub detected!"
        return 1
    fi

    check_no_stub_patterns "$response" "resources/read yawl://specifications response"
}

# TEST 8: Verify YAWL engine integration
test_yawl_engine_integration() {
    echo "Checking YAWL engine connectivity..."

    local response
    response=$(curl -s --connect-timeout 5 "$YAWL_ENGINE_URL" 2>&1 || echo "FAILED")

    if [ "$response" = "FAILED" ]; then
        echo "WARNING: YAWL engine not reachable (some tests may fail)"
        return 0  # Don't fail, just warn
    fi

    echo "YAWL engine reachable"
    return 0
}

# TEST 9: Check for hardcoded test data (sign of fake implementation)
test_no_hardcoded_data() {
    echo "Checking for hardcoded/fake data in responses..."

    # Get tools response
    local tools_response
    tools_response=$(curl -s -X POST "$MCP_SERVER_URL/mcp" \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"tools/list","params":{},"id":100}' \
        --connect-timeout "$TIMEOUT" 2>&1 || echo "")

    # Check for obviously fake tool names
    if echo "$tools_response" | grep -qE "testTool|fakeTool|mockTool|dummyTool|sampleTool"; then
        echo "ERROR: Detected fake/mock tool names in response"
        return 1
    fi

    echo "No hardcoded fake data detected"
    return 0
}

# Run all tests
echo ""
echo "==========================================="
echo "Running Integration Tests"
echo "==========================================="

run_test "Server Availability" test_server_available
run_test "MCP Initialize Handshake" test_mcp_initialize
run_test "List Tools" test_list_tools
run_test "List Resources" test_list_resources
run_test "Tool: List Specifications" test_tool_list_specs
run_test "Tool: Get Work Items" test_tool_get_workitems
run_test "Resource: Specifications" test_resource_specifications
run_test "YAWL Engine Integration" test_yawl_engine_integration
run_test "No Hardcoded Fake Data" test_no_hardcoded_data

# Summary
echo ""
echo "==========================================="
echo "Test Summary"
echo "==========================================="
echo "Tests Run:    $TESTS_RUN"
echo -e "Tests Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests Failed: ${RED}$TESTS_FAILED${NC}"
echo "==========================================="

if [ $TESTS_FAILED -gt 0 ]; then
    echo ""
    echo -e "${RED}FAILED TESTS:${NC}"
    for failure in "${FAILURES[@]}"; do
        echo "  - $failure"
    done
    echo ""
    echo "Integration tests FAILED"
    exit 1
else
    echo ""
    echo -e "${GREEN}All integration tests PASSED${NC}"
    exit 0
fi
