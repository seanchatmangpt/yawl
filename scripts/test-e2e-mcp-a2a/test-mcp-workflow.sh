#!/usr/bin/env bash
set -euo pipefail
# ==========================================================================
# test-mcp-workflow.sh — MCP E2E Workflow Test
#
# Tests MCP protocol integration with YAWL workflow engine.
#
# Usage: bash scripts/test-e2e-mcp-a2a/test-mcp-workflow.sh [--verbose]
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
    log_section "MCP Workflow Test Setup"

    # Clean up any previous test data
    cleanup_test_state

    # Check if MCP server is running
    if ! nc -z localhost 9090 2>/dev/null; then
        log_error "MCP server not running on localhost:9090"
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

# ── MCP Workflow Test Functions ──────────────────────────────────────────
test_mcp_initialization() {
    log_section "MCP Protocol Initialization"

    # Initialize MCP connection
    local request='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05"}}'

    if echo "$request" | timeout 10 nc -w 10 localhost 9090 > "${TEMP_DIR}/mcp-init-response.json" 2>/dev/null; then
        assert_contains "$(cat "${TEMP_DIR}/mcp-init-response.json")" "jsonrpc"
        assert_contains "$(cat "${TEMP_DIR}/mcp-init-response.json")" "capabilities"
        return 0
    else
        assert_false "Failed to initialize MCP connection"
        return 1
    fi
}

test_mcp_spec_upload() {
    log_section "MCP Specification Upload"

    # Upload specification via MCP
    CASE_ID=$(mcp_upload_specification "$SPEC_FILE")

    assert_not_empty "$CASE_ID" "Case ID generated"
    assert_not_empty "${CASE_ID:0:10}" "Case ID has valid format"

    if [[ -n "$CASE_ID" ]]; then
        log_success "Case created: $CASE_ID"
        return 0
    else
        assert_false "Failed to upload specification via MCP"
        return 1
    fi
}

test_mcp_case_status() {
    log_section "MCP Case Status Check"

    # Wait for case to be created
    local max_attempts=30
    local attempts=0

    while [[ $attempts -lt $max_attempts ]]; do
        local status
        status=$(mcp_get_case_status "$CASE_ID")

        if [[ -n "$status" ]]; then
            assert_contains "$status" "running" "Case is running"
            return 0
        fi

        ((attempts++))
        sleep 1
    done

    assert_false "Case status not available after 30 seconds"
    return 1
}

test_mcp_work_items() {
    log_section "MCP Work Items Retrieval"

    # Get work items
    local work_items
    work_items=$(yawl_get_work_items "$CASE_ID")

    assert_work_item_available "$CASE_ID" "1" "At least one work item available"
    assert_contains "$work_items" "id" "Work items contain IDs"

    # Get the first work item
    local work_item_id
    work_item_id=$(echo "$work_items" | head -n 1)
    assert_not_empty "$work_item_id" "Work item ID extracted"

    return $?
}

test_mcp_workflow_completion() {
    log_section "MCP Workflow Completion"

    # Check if case completes
    assert_case_complete "$CASE_ID" "60" "Case completes within 60 seconds"

    # Verify final status
    local final_status
    final_status=$(mcp_get_case_status "$CASE_ID")
    assert_equal "$final_status" "complete" "Final case status is complete"

    return $?
}

test_mcp_error_handling() {
    log_section "MCP Error Handling"

    # Test invalid case ID
    local invalid_response
    invalid_response=$(echo '{"jsonrpc":"2.0","id":999,"method":"tools/call","params":{"name":"yawl_get_case_status","arguments":{"caseId":"invalid"}}}' | timeout 10 nc -w 10 localhost 9090 2>/dev/null)
    assert_contains "$invalid_response" "error" "Invalid case ID returns error"

    # Test invalid tool name
    local invalid_tool_response
    invalid_tool_response=$(echo '{"jsonrpc":"2.0","id":999,"method":"tools/call","params":{"name":"invalid_tool","arguments":{}}}' | timeout 10 nc -w 10 localhost 9090 2>/dev/null)
    assert_contains "$invalid_tool_response" "error" "Invalid tool name returns error"

    return $?
}

test_mcp_resource_access() {
    log_section "MCP Resource Access"

    # Test resource listing
    local resource_request='{"jsonrpc":"2.0","id":5,"method":"resources/list","params":{}}'

    if echo "$resource_request" | timeout 10 nc -w 10 localhost 9090 > "${TEMP_DIR}/mcp-resources.json" 2>/dev/null; then
        assert_contains "$(cat "${TEMP_DIR}/mcp-resources.json")" "resources" "Resource list available"
        assert_contains "$(cat "${TEMP_DIR}/mcp-resources.json")" "yawl://" "YAWL resources found"
        return 0
    fi

    assert_false "Failed to access MCP resources"
    return 1
}

# ── Main Test Execution ─────────────────────────────────────────────────
run_mcp_workflow_tests() {
    local tests=(
        "test_mcp_initialization"
        "test_mcp_spec_upload"
        "test_mcp_case_status"
        "test_mcp_work_items"
        "test_mcp_workflow_completion"
        "test_mcp_error_handling"
        "test_mcp_resource_access"
    )

    log_section "Starting MCP Workflow Test Suite"
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
    log_section "MCP Workflow Test Summary"
    echo "Total Tests: ${#tests[@]}"
    echo -e "${GREEN}Passed: $TEST_PASSED${NC}"
    echo -e "${RED}Failed: $TEST_FAILED${NC}"

    if [[ $TEST_FAILED -eq 0 ]]; then
        echo ""
        echo -e "${GREEN}✓ All MCP workflow tests passed!${NC}"
        generate_test_report "mcp-workflow"
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
    if setup_test && run_mcp_workflow_tests; then
        exit 0
    else
        exit 1
    fi
}

# Execute main function if not sourced
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi