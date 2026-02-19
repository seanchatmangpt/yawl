#!/usr/bin/env bash
# ==========================================================================
# test-protocol-handshake.sh — MCP Protocol Handshake Tests
#
# Tests MCP protocol initialization and capabilities.
# Usage: bash tests/test-protocol-handshake.sh [--verbose]
# ==========================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/../lib"
source "${LIB_DIR}/mcp-client.sh"

MCP_SERVER_HOST="${MCP_SERVER_HOST:-localhost}"
MCP_SERVER_PORT="${MCP_SERVER_PORT:-9090}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo "MCP Protocol Handshake Tests"
echo "============================"

total_tests=0
passed_tests=0
failed_tests=0

# Test function
run_test() {
    local test_name="$1"
    local expected_result="$2"
    local test_command="$3"

    ((total_tests++))
    echo -n "Testing $test_name... "

    if eval "$test_command"; then
        echo -e "${GREEN}PASS${NC}"
        ((passed_tests++))
    else
        echo -e "${RED}FAIL${NC}"
        ((failed_tests++))
    fi
}

# Test 1: Check if MCP server is running
run_test "MCP Server Running" "0" "timeout 5 nc -z ${MCP_SERVER_HOST} ${MCP_SERVER_PORT}"

# Test 2: Initialize connection
run_test "Initialize Connection" "0" "mcp_initialize | timeout 10 nc -w 5 ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} | grep -q '\"jsonrpc\":\"2.0\"'"

# Test 3: Check protocol version
run_test "Protocol Version 2024-11-05" "0" "mcp_initialize | timeout 10 nc -w 5 ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} | grep -q 'protocol.*2024-11-05'"

# Test 4: Check server info
run_test "Server Info Present" "0" "mcp_initialize | timeout 10 nc -w 5 ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} | grep -q 'server.*info'"

# Test 5: Check capabilities
run_test "Capabilities Object" "0" "mcp_initialize | timeout 10 nc -w 5 ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} | grep -q '\"capabilities\"'"

# Test 6: Check tools capability
run_test "Tools Capability" "0" "mcp_list_tools | timeout 10 nc -w 5 ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} | grep -q '\"tools\"'"

# Test 7: Check resources capability
run_test "Resources Capability" "0" "mcp_list_resources | timeout 10 nc -w 5 ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} | grep -q '\"resources\"'"

echo ""
echo "Results: ${passed_tests}/${total_tests} tests passed"
echo ""

if [[ $failed_tests -eq 0 ]]; then
    echo "All protocol handshake tests passed!"
    exit 0
else
    echo "${failed_tests} tests failed. Check the MCP server configuration."
    exit 1
fi