#!/usr/bin/env bash
set -euo pipefail
# ==========================================================================
# test-a2a-workflow.sh — A2A E2E Workflow Test
#
# Tests A2A protocol integration with YAWL workflow engine.
#
# Usage: bash scripts/test-e2e-mcp-a2a/test-a2a-workflow.sh [--verbose]
# ==========================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/lib"
SPEC_DIR="${SCRIPT_DIR}/specs"

# Source libraries
source "${LIB_DIR}/workflow-common.sh"
source "${LIB_DIR}/workflow-assertions.sh"

# Configuration
SPEC_FILE="${SPEC_FILE:-${SPEC_DIR}/minimal-test.xml}"
VERBOSE="${VERBOSE:-0}"
TEST_TIMEOUT="${TEST_TIMEOUT:-120}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

# ── Test Variables ───────────────────────────────────────────────────────
declare CASE_ID=""
declare WORKFLOW_NAME="minimal-test"
declare TEST_PASSED=0
declare TEST_FAILED=0

# ── Setup Functions ─────────────────────────────────────────────────────
setup_test() {
    log_section "A2A Workflow Test Setup"

    # Clean up any previous test data
    cleanup_test_state

    # Check if A2A server is running
    if ! nc -z localhost 8081 2>/dev/null; then
        log_error "A2A server not running on localhost:8081"
        return 1
    fi

    # Verify spec file exists
    if [[ ! -f "$SPEC_FILE" ]]; then
        log_error "Specification file not found: $SPEC_FILE"
        return 1
    fi

    log_success "Test setup complete"
    return 0
}

# ── A2A Workflow Test Functions ───────────────────────────────────────────
test_a2a_agent_card() {
    log_section "A2A Agent Card Discovery"

    # Get agent card
    local response_file="${TEMP_DIR}/agent-card.json"
    if curl -s "${A2A_SERVER_URL}/.well-known/agent.json" > "$response_file" 2>/dev/null; then
        assert_contains "$(cat "$response_file")" "name" "Agent card has name"
        assert_contains "$(cat "$response_file")" "version" "Agent card has version"
        assert_contains "$(cat "$response_file")" "skills" "Agent card has skills"
        return 0
    else
        assert_false "Failed to get agent card"
        return 1
    fi
}

test_a2a_skill_registration() {
    log_section "A2A Skills Registration"

    # Get agent card and check skills
    local response_file="${TEMP_DIR}/agent-card.json"
    if curl -s "${A2A_SERVER_URL}/.well-known/agent.json" > "$response_file" 2>/dev/null; then
        assert_contains "$(cat "$response_file")" "launch_workflow" "Launch workflow skill registered"
        assert_contains "$(cat "$response_file")" "query_workflows" "Query workflows skill registered"
        assert_contains "$(cat "$response_file")" "manage_workitems" "Manage work items skill registered"
        assert_contains "$(cat "$response_file")" "cancel_workflow" "Cancel workflow skill registered"
        return 0
    else
        assert_false "Failed to check skills registration"
        return 1
    fi
}

test_a2a_workflow_launch() {
    log_section "A2A Workflow Launch"

    # Launch workflow via A2A
    local launch_request='{"message":"launch_workflow","params":{"specification":"'"$WORKFLOW_NAME"'","initiator":"test-user"}}'

    CASE_ID=$(a2a_launch_workflow "$WORKFLOW_NAME")

    assert_not_empty "$CASE_ID" "Case ID generated"
    assert_not_empty "${CASE_ID:0:10}" "Case ID has valid format"

    if [[ -n "$CASE_ID" ]]; then
        log_success "Case launched: $CASE_ID"
        return 0
    else
        assert_false "Failed to launch workflow via A2A"
        return 1
    fi
}

test_a2a_case_query() {
    log_section "A2A Case Query"

    # Query case status
    local status
    status=$(a2a_query_case "$CASE_ID")

    assert_not_empty "$status" "Status retrieved"
    assert_contains "$status" "running" "Case is running"

    return $?
}

test_a2a_workflow_monitoring() {
    log_section "A2A Workflow Monitoring"

    # Monitor case progress
    local max_checks=30
    local checks=0
    local expected_status="complete"

    while [[ $checks -lt $max_checks ]]; do
        local status
        status=$(a2a_query_case "$CASE_ID" 2>/dev/null || echo "")

        if [[ "$status" == "$expected_status" ]]; then
            assert_equal "$status" "$expected_status" "Case completed"
            return 0
        fi

        ((checks++))
        sleep 2
    done

    assert_false "Case did not complete within ${max_checks} * 2 seconds"
    return 1
}

test_a2a_authentication() {
    log_section "A2A Authentication"

    # Test without authentication
    local unauthorized_response
    unauthorized_response=$(curl -s -o /dev/null -w "%{http_code}" "${A2A_SERVER_URL}/" 2>/dev/null)
    assert_equal "$unauthorized_response" "401" "Unauthenticated request returns 401"

    # Test with invalid JWT
    local invalid_jwt_response
    invalid_jwt_response=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "Authorization: Bearer invalid.token" \
        -d '{"message":"test"}' \
        "${A2A_SERVER_URL}/" 2>/dev/null)
    assert_equal "$invalid_jwt_response" "401" "Invalid JWT returns 401"

    # Test with valid JWT (read permissions)
    local jwt=$(generate_jwt "test-user" '"workflow:query"' "yawl-a2a")
    local authorized_response
    authorized_response=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "Authorization: Bearer $jwt" \
        "${A2A_SERVER_URL}/.well-known/agent.json" 2>/dev/null)
    assert_equal "$authorized_response" "200" "Valid JWT returns 200"

    return $?
}

test_a2a_message_patterns() {
    log_section "A2A Message Patterns"

    # Test message launch pattern
    local launch_request='{"message":"launch_workflow","params":{"spec":"test"}}'
    assert_a2a_message_success "launch_workflow" "$launch_request" "Case creation"

    # Test message query pattern
    local query_request='{"message":"query_workflows","params":{"caseId":"test"}}'
    assert_a2a_message_success "query_workflows" "$query_request" "Query execution"

    # Test message cancel pattern
    local cancel_request='{"message":"cancel_workflow","params":{"caseId":"test","reason":"Test cancellation"}}'
    assert_a2a_message_success "cancel_workflow" "$cancel_request" "Cancellation"

    return $?
}

test_a2a_error_responses() {
    log_section "A2A Error Responses"

    # Test malformed request
    local malformed_response
    malformed_response=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "Content-Type: text/plain" \
        -d "malformed" \
        "${A2A_SERVER_URL}/" 2>/dev/null)
    assert_equal "$malformed_response" "400" "Malformed request returns 400"

    # Test not found endpoint
    local not_found_response
    not_found_response=$(curl -s -o /dev/null -w "%{http_code}" \
        "${A2A_SERVER_URL}/nonexistent" 2>/dev/null)
    assert_equal "$not_found_response" "404" "Not found endpoint returns 404"

    # Test method not allowed
    local method_response
    method_response=$(curl -s -o /dev/null -w "%{http_code}" \
        -X PUT \
        "${A2A_SERVER_URL}/" 2>/dev/null)
    assert_equal "$method_response" "405" "PUT method returns 405"

    return $?
}

# ── Main Test Execution ─────────────────────────────────────────────────
run_a2a_workflow_tests() {
    local tests=(
        "test_a2a_agent_card"
        "test_a2a_skill_registration"
        "test_a2a_workflow_launch"
        "test_a2a_case_query"
        "test_a2a_workflow_monitoring"
        "test_a2a_authentication"
        "test_a2a_message_patterns"
        "test_a2a_error_responses"
    )

    log_section "Starting A2A Workflow Test Suite"
    log_info "Test Specification: $SPEC_FILE"

    # Run tests
    for test in "${tests[@]}"; do
        if ! $test; then
            TEST_FAILED=$((TEST_FAILED + 1))
            log_error "$test failed"
        else
            TEST_PASSED=$((TEST_PASSED + 1))
            log_success "$test passed"
        fi
    done

    # Print summary
    echo ""
    log_section "A2A Workflow Test Summary"
    echo "Total Tests: ${#tests[@]}"
    echo -e "${GREEN}Passed: $TEST_PASSED${NC}"
    echo -e "${RED}Failed: $TEST_FAILED${NC}"

    if [[ $TEST_FAILED -eq 0 ]]; then
        echo ""
        echo -e "${GREEN}✓ All A2A workflow tests passed!${NC}"
        generate_test_report "a2a-workflow"
        return 0
    else
        echo ""
        echo -e "${RED}✗ $TEST_FAILED tests failed.${NC}"
        return 1
    fi
}

# ── Main Script ─────────────────────────────────────────────────────────
main() {
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --verbose) VERBOSE=1; shift ;;
            -h|--help)
                echo "Usage: $0 [--verbose]"
                echo "  --verbose    Enable verbose logging"
                echo "  -h, --help  Show this help message"
                exit 0 ;;
            *)  log_error "Unknown argument: $1"; exit 1 ;;
        esac
    done

    # Run tests
    if setup_test && run_a2a_workflow_tests; then
        exit 0
    else
        exit 1
    fi
}

# Execute main function if not sourced
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi