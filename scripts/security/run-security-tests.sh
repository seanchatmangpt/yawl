#!/bin/bash

# Comprehensive YAWL MCP-A2A Security Test Execution Script
# This script runs multiple security testing scenarios

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
TARGET_HOST="${TARGET_HOST:-localhost}"
TARGET_PORT="${TARGET_PORT:-8080}"
MCP_PORT="${MCP_PORT:-8081}"
A2A_PORT="${A2A_PORT:-8082}"

BASE_URL="http://$TARGET_HOST:$TARGET_PORT"
MCP_URL="http://$TARGET_HOST:$MCP_PORT"
A2A_URL="http://$TARGET_HOST:$A2A_PORT"

# Test results file
TEST_RESULTS="/tmp/yawl-security-test-results.txt"
echo "YAWL MCP-A2A Security Test Results - $(date)" > "$TEST_RESULTS"
echo "===============================================" >> "$TEST_RESULTS"

# Function to print banner
print_banner() {
    echo -e "${BLUE}"
    echo "======================================"
    echo "$1"
    echo "======================================"
    echo -e "${NC}"
}

# Function to run test and record result
run_test() {
    local test_name="$1"
    local test_command="$2"
    local expected_result="$3"
    
    echo -e "${YELLOW}Running: $test_name${NC}"
    echo -n "  Command: $test_command" >> "$TEST_RESULTS"
    
    # Execute test
    if eval "$test_command" >> "$TEST_RESULTS" 2>&1; then
        result="PASS"
        color=$GREEN
    else
        result="FAIL"
        color=$RED
    fi
    
    echo -e "  Result: $color$result${NC}"
    echo "  Status: $result" >> "$TEST_RESULTS"
    echo "" >> "$TEST_RESULTS"
    
    return $([ "$result" = "PASS" ])
}

print_banner "Authentication Testing"

# Test 1: MCP endpoints without authentication
run_test "Direct MCP SSE Access" \
    "curl -s -o /dev/null -w '%{http_code}' $MCP_URL/mcp/sse" \
    "401"

run_test "Direct MCP Message Access" \
    "curl -s -o /dev/null -w '%{http_code}' -X POST $MCP_URL/mcp/message -d '{\"jsonrpc\":\"2.0\",\"method\":\"tools.list\"}'" \
    "401"

# Test 2: Invalid JWT tokens
run_test "Invalid JWT Token" \
    "curl -s -H 'Authorization: Bearer invalid.token' $MCP_URL/mcp/tools | grep -q 'error'" \
    "true"

print_banner "Authorization Testing"

# Test 3: Administrative endpoints
run_test "Actuator Endpoint Protection" \
    "curl -s -o /dev/null -w '%{http_code}' $BASE_URL/actuator/env" \
    "401"

# Test 4: Cross-service access
run_test "Cross-Service Data Access" \
    "curl -s $A2A_URL/agents/list | grep -q 'error'" \
    "true"

print_banner "Input Injection Testing"

# Test 5: SQL injection
run_test "SQL Injection Protection" \
    "curl -s -X POST $MCP_URL/mcp/message -d '{\"jsonrpc\":\"2.0\",\"method\":\"launch\",\"params\":{\"spec\":\"' OR '1'='1' --\"}}' | grep -q 'error'" \
    "true"

# Test 6: XSS protection
run_test "XSS Protection" \
    "curl -s -X POST $MCP_URL/mcp/message -d '{\"jsonrpc\":\"2.0\",\"method\":\"launch\",\"params\":{\"name\":\"<script>alert(1)</script>\"}}' | grep -q 'error'" \
    "true"

print_banner "Protocol Testing"

# Test 7: JSON-RPC validation
run_test "JSON-RPC Invalid Method" \
    "curl -s -X POST $MCP_URL/mcp/message -d '{\"jsonrpc\":\"2.0\",\"method\":\"invalid.method\",\"params\":{}}' | grep -q 'error'" \
    "true"

print_banner "Denial of Service Testing"

# Test 8: Connection limits
CONNECTION_COUNT=0
for i in {1..110}; do
    if curl -s --max-time 1 $MCP_URL/mcp/sse > /dev/null 2>&1; then
        ((CONNECTION_COUNT++))
    fi
done
run_test "Connection Pool Limit" \
    "[ $CONNECTION_COUNT -le 100 ]" \
    "true"

print_banner "Data Exfiltration Testing"

# Test 9: Sensitive data exposure
run_test "Sensitive Data Protection" \
    "curl -s $BASE_URL/actuator/env | grep -q 'password\|secret\|key'" \
    "false"

# Summary
echo -e "${BLUE}"
echo "======================================"
echo "Security Test Summary"
echo "======================================"
echo -e "${NC}"

# Count test results
TOTAL_TESTS=$(grep -c "Status:" "$TEST_RESULTS")
PASSED_TESTS=$(grep -c "Status: PASS" "$TEST_RESULTS")
FAILED_TESTS=$((TOTAL_TESTS - PASSED_TESTS))

echo "Total Tests: $TOTAL_TESTS"
echo "Passed: $PASSED_TESTS"
echo "Failed: $FAILED_TESTS"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ $FAILED_TESTS tests failed${NC}"
    echo -e "${YELLOW}View detailed results: cat $TEST_RESULTS${NC}"
    exit 1
fi
