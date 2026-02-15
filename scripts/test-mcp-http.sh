#!/bin/bash
#
# Test YAWL MCP Server HTTP Endpoints
#
# Uses curl to directly test the MCP server's HTTP endpoints.
# This performs real HTTP requests against the running server.
#
# Usage: ./test-mcp-http.sh [server_url]
#
# Requirements: curl, jq (for JSON formatting)

set -e

# Configuration
MCP_SERVER_URL="${1:-http://localhost:3000}"
MCP_ENDPOINT="${MCP_ENDPOINT:-/mcp}"
SSE_ENDPOINT="${MCP_SERVER_URL}${MCP_ENDPOINT}/sse"
MESSAGE_ENDPOINT="${MCP_SERVER_URL}${MCP_ENDPOINT}/message"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PASSED=0
FAILED=0

# Test result functions
pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((PASSED++))
}

fail() {
    echo -e "${RED}[FAIL]${NC} $1: $2"
    ((FAILED++))
}

info() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

# JSON-RPC request builder
build_request() {
    local method="$1"
    local params="$2"
    local id="${3:-1}"

    if [ -n "$params" ]; then
        echo "{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":\"$method\",\"params\":$params}"
    else
        echo "{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":\"$method\"}"
    fi
}

# Send MCP request
send_request() {
    local request="$1"
    local endpoint="${2:-$MESSAGE_ENDPOINT}"

    curl -s -X POST \
        -H "Content-Type: application/json" \
        -H "Accept: application/json" \
        -d "$request" \
        "$endpoint"
}

# Format JSON if jq is available
format_json() {
    if command -v jq &> /dev/null; then
        jq '.' 2>/dev/null || cat
    else
        cat
    fi
}

echo "==========================================="
echo "YAWL MCP Server HTTP Endpoint Tests"
echo "==========================================="
echo "Server: $MCP_SERVER_URL"
echo "SSE Endpoint: $SSE_ENDPOINT"
echo "Message Endpoint: $MESSAGE_ENDPOINT"
echo "==========================================="
echo ""

# Test 1: Server Health Check
test_server_health() {
    info "Testing server health..."

    if curl -s --connect-timeout 5 "$MCP_SERVER_URL" > /dev/null 2>&1; then
        pass "Server health check"
    else
        fail "Server health check" "Cannot connect to server"
    fi
}

# Test 2: MCP Initialize
test_initialize() {
    info "Testing MCP initialize..."

    local request
    request=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "clientInfo": {
      "name": "test-client",
      "version": "1.0.0"
    },
    "capabilities": {}
  }
}
EOF
)

    local response
    response=$(send_request "$request")

    if echo "$response" | grep -q '"result"'; then
        pass "MCP initialize"
        echo "$response" | format_json | head -20
    else
        fail "MCP initialize" "$response"
    fi
}

# Test 3: List Tools
test_list_tools() {
    info "Testing tools/list..."

    local request
    request=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list",
  "params": {}
}
EOF
)

    local response
    response=$(send_request "$request")

    if echo "$response" | grep -q '"tools"'; then
        pass "tools/list"

        # Count tools
        local tool_count
        if command -v jq &> /dev/null; then
            tool_count=$(echo "$response" | jq '.result.tools | length' 2>/dev/null || echo "unknown")
            echo "Found $tool_count tools"
        fi

        echo "$response" | format_json | head -50
    else
        fail "tools/list" "$response"
    fi
}

# Test 4: List Resources
test_list_resources() {
    info "Testing resources/list..."

    local request
    request=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "resources/list",
  "params": {}
}
EOF
)

    local response
    response=$(send_request "$request")

    if echo "$response" | grep -q '"resources"'; then
        pass "resources/list"
        echo "$response" | format_json | head -30
    else
        fail "resources/list" "$response"
    fi
}

# Test 5: Call Tool - yawl_list_specs
test_yawl_list_specs() {
    info "Testing yawl_list_specs tool..."

    local request
    request=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "yawl_list_specs",
    "arguments": {}
  }
}
EOF
)

    local response
    response=$(send_request "$request")

    if echo "$response" | grep -qE '"(result|success)"'; then
        pass "yawl_list_specs tool"
        echo "$response" | format_json | head -40
    else
        fail "yawl_list_specs tool" "$response"
    fi
}

# Test 6: Call Tool - yawl_get_running_cases
test_yawl_get_running_cases() {
    info "Testing yawl_get_running_cases tool..."

    local request
    request=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "tools/call",
  "params": {
    "name": "yawl_get_running_cases",
    "arguments": {}
  }
}
EOF
)

    local response
    response=$(send_request "$request")

    if echo "$response" | grep -qE '"(result|success)"'; then
        pass "yawl_get_running_cases tool"
        echo "$response" | format_json | head -30
    else
        fail "yawl_get_running_cases tool" "$response"
    fi
}

# Test 7: Call Tool - yawl_get_workitems
test_yawl_get_workitems() {
    info "Testing yawl_get_workitems tool..."

    local request
    request=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "id": 6,
  "method": "tools/call",
  "params": {
    "name": "yawl_get_workitems",
    "arguments": {}
  }
}
EOF
)

    local response
    response=$(send_request "$request")

    if echo "$response" | grep -qE '"(result|success)"'; then
        pass "yawl_get_workitems tool"
        echo "$response" | format_json | head -40
    else
        fail "yawl_get_workitems tool" "$response"
    fi
}

# Test 8: Read Resource - yawl://specifications
test_read_specifications() {
    info "Testing resource read: yawl://specifications..."

    local request
    request=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "id": 7,
  "method": "resources/read",
  "params": {
    "uri": "yawl://specifications"
  }
}
EOF
)

    local response
    response=$(send_request "$request")

    if echo "$response" | grep -q '"contents"'; then
        pass "Read resource yawl://specifications"
        echo "$response" | format_json | head -40
    else
        fail "Read resource yawl://specifications" "$response"
    fi
}

# Test 9: Read Resource - yawl://cases
test_read_cases() {
    info "Testing resource read: yawl://cases..."

    local request
    request=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "id": 8,
  "method": "resources/read",
  "params": {
    "uri": "yawl://cases"
  }
}
EOF
)

    local response
    response=$(send_request "$request")

    if echo "$response" | grep -q '"contents"'; then
        pass "Read resource yawl://cases"
        echo "$response" | format_json | head -30
    else
        fail "Read resource yawl://cases" "$response"
    fi
}

# Test 10: Read Resource - yawl://workitems
test_read_workitems() {
    info "Testing resource read: yawl://workitems..."

    local request
    request=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "id": 9,
  "method": "resources/read",
  "params": {
    "uri": "yawl://workitems"
  }
}
EOF
)

    local response
    response=$(send_request "$request")

    if echo "$response" | grep -q '"contents"'; then
        pass "Read resource yawl://workitems"
        echo "$response" | format_json | head -40
    else
        fail "Read resource yawl://workitems" "$response"
    fi
}

# Run all tests
run_all_tests() {
    echo ""
    echo "Running tests..."
    echo ""

    test_server_health
    echo ""

    test_initialize
    echo ""

    test_list_tools
    echo ""

    test_list_resources
    echo ""

    test_yawl_list_specs
    echo ""

    test_yawl_get_running_cases
    echo ""

    test_yawl_get_workitems
    echo ""

    test_read_specifications
    echo ""

    test_read_cases
    echo ""

    test_read_workitems
    echo ""
}

# Main
run_all_tests

echo "==========================================="
echo "Test Results"
echo "==========================================="
echo -e "Passed: ${GREEN}$PASSED${NC}"
echo -e "Failed: ${RED}$FAILED${NC}"
echo "Total:  $((PASSED + FAILED))"
echo "==========================================="

if [ $FAILED -gt 0 ]; then
    exit 1
fi
