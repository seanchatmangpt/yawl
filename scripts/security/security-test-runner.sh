#!/bin/bash

# YAWL MCP-A2A Security Test Runner
# Performs comprehensive adversarial and security testing

set -euo pipefail

# Configuration
TARGET_HOST="localhost"
TARGET_PORT=8080
MCP_PORT=8081
A2A_PORT=8082
JSONRPC_PORT=8083
BASE_URL="http://$TARGET_HOST:$TARGET_PORT"
MCP_URL="http://$TARGET_HOST:$MCP_PORT"
A2A_URL="http://$TARGET_HOST:$A2A_PORT"
JSONRPC_URL="http://$TARGET_HOST:$JSONRPC_PORT"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test results
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to print test results
print_test_result() {
    local test_name="$1"
    local result="$2"
    local details="$3"
    
    ((TOTAL_TESTS++))
    
    if [[ "$result" == "PASS" ]]; then
        echo -e "${GREEN}[✓] PASS${NC} - $test_name"
        echo -e "    Details: $details"
        ((PASSED_TESTS++))
    else
        echo -e "${RED}[✗] FAIL${NC} - $test_name"
        echo -e "    Details: $details"
        ((FAILED_TESTS++))
    fi
    echo
}

# Function to check if endpoint is accessible
check_endpoint() {
    local url="$1"
    local expected_status="${2:-200}"
    
    if curl -s -o /dev/null -w "%{http_code}" "$url" | grep -q "$expected_status"; then
        return 0
    else
        return 1
    fi
}

# Test 1: Authentication Bypass Testing
echo -e "${BLUE}Running Authentication Bypass Tests...${NC}"
echo "=================================================="

# 1.1: Direct MCP access without authentication
echo "Test 1.1: Direct MCP endpoint access"
if check_endpoint "$MCP_URL/mcp/sse"; then
    print_test_result "MCP SSE endpoint accessible" "FAIL" "Endpoint accessible without authentication"
else
    print_test_result "MCP SSE endpoint protected" "PASS" "Endpoint properly secured"
fi

if check_endpoint "$MCP_URL/mcp/message" -d '{"jsonrpc":"2.0","method":"tools.list"}'; then
    print_test_result "MCP message endpoint accessible" "FAIL" "POST endpoint accessible without authentication"
else
    print_test_result "MCP message endpoint protected" "PASS" "POST endpoint properly secured"
fi

# 1.2: JWT token manipulation
echo "Test 1.2: JWT token validation"
# Test with no token
if curl -s "$MCP_URL/mcp/tools" | grep -q "error"; then
    print_test_result "No JWT token" "PASS" "Requests without token rejected"
else
    print_test_result "No JWT token" "FAIL" "Requests without token accepted"
fi

# Test with invalid token
FAKE_JWT="invalid.jwt.token"
if curl -s -H "Authorization: Bearer $FAKE_JWT" "$MCP_URL/mcp/tools" | grep -q "error"; then
    print_test_result "Invalid JWT token" "PASS" "Invalid JWT token rejected"
else
    print_test_result "Invalid JWT token" "FAIL" "Invalid JWT token accepted"
fi

# Test 2: Authorization Testing
echo -e "${BLUE}Running Authorization Tests...${NC}"
echo "=================================================="

# 2.1: Administrative endpoint protection
echo "Test 2.1: Administrative endpoint protection"
if check_endpoint "$BASE_URL/actuator/env" 401; then
    print_test_result "Actuator endpoint protection" "PASS" "Administrative endpoints properly protected"
else
    print_test_result "Actuator endpoint exposure" "FAIL" "Sensitive information exposed"
fi

# Test 3: Input Injection Attacks
echo -e "${BLUE}Running Input Injection Tests...${NC}"
echo "=================================================="

# 3.1: SQL injection protection
echo "Test 3.1: SQL injection protection"
SQL_PAYLOAD="' OR '1'='1' --"
RESPONSE=$(curl -s -X POST "$MCP_URL/mcp/message" -d "{\"jsonrpc\":\"2.0\",\"method\":\"yawl.launchWorkflow\",\"params\":{\"specId\":\"$SQL_PAYLOAD\"}}" 2>&1)
if echo "$RESPONSE" | grep -q "error\|invalid\|rejected"; then
    print_test_result "SQL injection protection" "PASS" "SQL injection payload rejected"
else
    print_test_result "SQL injection protection" "FAIL" "SQL injection payload accepted"
fi

# 3.2: XSS protection
echo "Test 3.2: XSS protection"
XSS_PAYLOAD="<script>alert(1)</script>"
RESPONSE=$(curl -s -X POST "$MCP_URL/mcp/message" -d "{\"jsonrpc\":\"2.0\",\"method\":\"yawl.launchWorkflow\",\"params\":{\"name\":\"$XSS_PAYLOAD\"}}" 2>&1)
if echo "$RESPONSE" | grep -q "error\|invalid\|rejected"; then
    print_test_result "XSS protection" "PASS" "XSS payload rejected"
else
    print_test_result "XSS protection" "FAIL" "XSS payload accepted"
fi

# Test 4: Protocol Attacks
echo -e "${BLUE}Running Protocol Tests...${NC}"
echo "=================================================="

# 4.1: JSON-RPC validation
echo "Test 4.1: JSON-RPC message validation"
if curl -s -X POST "$MCP_URL/mcp/message" -d '{"jsonrpc":"2.0","method":"invalid.method","params":{}}' | grep -q "error\|invalid"; then
    print_test_result "JSON-RPC invalid method" "PASS" "Invalid method rejected"
else
    print_test_result "JSON-RPC invalid method" "FAIL" "Invalid method accepted"
fi

# Test 5: Denial of Service Testing
echo -e "${BLUE}Running Denial of Service Tests...${NC}"
echo "=================================================="

# 5.1: Connection pool testing
echo "Test 5.1: Connection pool limit"
CONNECTION_COUNT=0
for i in {1..110}; do
    if curl -s --max-time 1 "$MCP_URL/mcp/sse" > /dev/null 2>&1; then
        ((CONNECTION_COUNT++))
    fi
    sleep 0.01
done

if [[ $CONNECTION_COUNT -le 100 ]]; then
    print_test_result "Connection pool protection" "PASS" "Connection limits enforced"
else
    print_test_result "Connection pool protection" "FAIL" "Too many connections allowed"
fi

# Test 6: Data Exfiltration
echo -e "${BLUE}Running Data Exfiltration Tests...${NC}"
echo "=================================================="

# 6.1: Sensitive data exposure
echo "Test 6.1: Sensitive endpoint protection"
if check_endpoint "$BASE_URL/actuator/env" 401; then
    print_test_result "Sensitive endpoint protection" "PASS" "Actuator endpoint protected"
else
    print_test_result "Sensitive endpoint exposure" "FAIL" "Actuator endpoint exposed"
fi

# Final Results
echo -e "${BLUE}Security Testing Complete!${NC}"
echo "=================================================="
echo -e "Total Tests: ${BLUE}$TOTAL_TESTS${NC}"
echo -e "${GREEN}Passed: $PASSED_TESTS${NC}"
echo -e "${RED}Failed: $FAILED_TESTS${NC}"
echo

if [[ $FAILED_TESTS -eq 0 ]]; then
    echo -e "${GREEN}🎉 All security tests passed!${NC}"
    exit 0
else
    echo -e "${RED}⚠️  $FAILED_TESTS security tests failed. Please review vulnerabilities.${NC}"
    exit 1
fi
