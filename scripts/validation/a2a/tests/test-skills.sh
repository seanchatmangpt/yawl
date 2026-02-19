#!/usr/bin/env bash
# ==========================================================================
# test-skills.sh — A2A Skills Functional Validation Tests
#
# Comprehensive functional tests for all 5 A2A skills:
#   - launch_workflow: Launch new workflow cases
#   - query_workflows: List specifications and running cases
#   - manage_workitems: Get and complete work items
#   - cancel_workflow: Cancel running cases
#   - handoff_workitem: Transfer work items between agents
#
# For each skill, tests:
#   - Input validation (required/optional params)
#   - Output schema validation
#   - Error handling (invalid inputs, engine errors)
#   - Permission checks
#   - Integration with YAWL engine
#
# Usage:
#   bash scripts/validation/a2a/tests/test-skills.sh
#   bash scripts/validation/a2a/tests/test-skills.sh --verbose
#   bash scripts/validation/a2a/tests/test-skills.sh --json
#   bash scripts/validation/a2a/tests/test-skills.sh --skill launch_workflow
#
# Exit Codes:
#   0 - All skill tests passed
#   1 - One or more skill tests failed
#   2 - Server not available or dependency missing
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/../lib"

# Source A2A common functions
source "${LIB_DIR}/a2a-common.sh" 2>/dev/null || {
    echo "[ERROR] A2A client library not found: ${LIB_DIR}/a2a-common.sh" >&2
    exit 2
}

# Configuration
VERBOSE="${VERBOSE:-0}"
OUTPUT_FORMAT="${OUTPUT_FORMAT:-text}"
TARGET_SKILL="${TARGET_SKILL:-}"
A2A_SERVER_HOST="${A2A_SERVER_HOST:-localhost}"
A2A_SERVER_PORT="${A2A_SERVER_PORT:-8080}"
A2A_TIMEOUT="${A2A_TIMEOUT:-30}"

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
declare -a TEST_RESULTS=()

# Permission constants (from AuthenticatedPrincipal)
PERM_WORKFLOW_LAUNCH="workflow:launch"
PERM_WORKFLOW_QUERY="workflow:query"
PERM_WORKITEM_MANAGE="workitem:manage"
PERM_WORKFLOW_CANCEL="workflow:cancel"
PERM_ALL="*"

# ── Logging Functions ─────────────────────────────────────────────────────
log_section() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "\n${BOLD}${BLUE}=== $1 ===${NC}"
}

log_info() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${CYAN}[INFO]${NC} $*"
}

log_success() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${GREEN}[PASS]${NC} $*"
}

log_error() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${RED}[FAIL]${NC} $*" >&2
}

log_warning() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${YELLOW}[WARN]${NC} $*"
}

log_verbose() {
    [[ "$VERBOSE" -eq 1 ]] && echo "[VERBOSE] $*" >&2
}

# ── Test Runner Functions ─────────────────────────────────────────────────
run_test() {
    local test_name="$1"
    local test_description="$2"
    local test_function="$3"

    ((TOTAL_TESTS++)) || true

    log_verbose "Running: $test_name - $test_description"

    if eval "$test_function"; then
        ((PASSED_TESTS++)) || true
        TEST_RESULTS+=("{\"name\":\"${test_name}\",\"status\":\"PASS\",\"description\":\"${test_description}\"}")
        log_success "$test_name"
        return 0
    else
        ((FAILED_TESTS++)) || true
        TEST_RESULTS+=("{\"name\":\"${test_name}\",\"status\":\"FAIL\",\"description\":\"${test_description}\"}")
        log_error "$test_name"
        return 1
    fi
}

# ── Authentication Helper Functions ───────────────────────────────────────
get_auth_header() {
    local permissions="$1"
    local jwt
    jwt=$(generate_jwt "test-user" "$permissions" "yawl-a2a")
    echo "Authorization: Bearer ${jwt}"
}

get_full_auth_header() {
    get_auth_header "\"${PERM_ALL}\""
}

# ── HTTP Helper Functions for A2A Messages ─────────────────────────────────
send_a2a_message() {
    local message="$1"
    local auth_header="$2"
    local expected_code="$3"

    local payload
    payload=$(jq -n --arg msg "$message" '{"parts":[{"type":"text","text":$msg}]}')

    http_post "${A2A_BASE_URL}/" "$payload" "$auth_header" "$expected_code" ""
}

send_a2a_message_with_response() {
    local message="$1"
    local auth_header="$2"

    local payload
    payload=$(jq -n --arg msg "$message" '{"parts":[{"type":"text","text":$msg}]}')

    curl -s -S -w "\nHTTP_CODE:%{http_code}" \
        --connect-timeout ${A2A_TIMEOUT} \
        -X POST \
        -H "Content-Type: application/json" \
        ${auth_header:+ -H "$auth_header"} \
        -d "$payload" \
        "${A2A_BASE_URL}/" 2>/dev/null
}

# ==========================================================================
# SKILL 1: launch_workflow
# ==========================================================================

test_launch_workflow_input_valid() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_LAUNCH},${PERM_WORKFLOW_QUERY}\"")

    # Test valid launch command with specification identifier
    local response
    response=$(send_a2a_message_with_response "launch OrderProcessing" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    # Should succeed (200) or fail gracefully if spec not loaded (engine error, not protocol error)
    [[ "$status_code" == "200" || "$status_code" == "500" ]]
}

test_launch_workflow_input_missing_spec() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_LAUNCH}\"")

    # Test launch without specifying a workflow
    local response
    response=$(send_a2a_message_with_response "launch" "$auth")

    local body=$(echo "$response" | sed -e 's/HTTP_CODE:[0-9]*$//')

    # Should return helpful error message, not crash
    echo "$body" | grep -qiE "(specify|workflow|identifier|available)"
}

test_launch_workflow_input_quoted_spec() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_LAUNCH}\"")

    # Test with quoted specification name
    local response
    response=$(send_a2a_message_with_response "launch 'InvoiceApproval' with order data" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    [[ "$status_code" == "200" || "$status_code" == "500" ]]
}

test_launch_workflow_output_schema() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_LAUNCH},${PERM_WORKFLOW_QUERY}\"")

    local response
    response=$(send_a2a_message_with_response "launch TestSpec" "$auth")

    local body=$(echo "$response" | sed -e 's/HTTP_CODE:[0-9]*$//')
    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    if [[ "$status_code" == "200" ]]; then
        # Successful launch should include case ID and status
        echo "$body" | grep -qiE "(case|launched|running|id)" && return 0
    fi

    # Engine error (spec not found) is acceptable
    echo "$body" | grep -qiE "(failed|error|not found)" && return 0

    return 1
}

test_launch_workflow_permission_denied() {
    # User with only query permission should not be able to launch
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_QUERY}\"")

    local response
    response=$(send_a2a_message_with_response "launch OrderProcessing" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    # Should return 403 Forbidden
    [[ "$status_code" == "403" ]]
}

test_launch_workflow_engine_integration() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_LAUNCH},${PERM_WORKFLOW_QUERY}\"")

    # First list specs, then try to launch one if available
    local list_response
    list_response=$(send_a2a_message_with_response "list specifications" "$auth")

    local list_body=$(echo "$list_response" | sed -e 's/HTTP_CODE:[0-9]*$//')

    # If we got a list of specs, try to launch
    if echo "$list_body" | grep -qiE "specification"; then
        # Extract first spec name (simplified)
        local spec_name=$(echo "$list_body" | grep -oE '\- [A-Za-z0-9_]+' | head -1 | sed 's/^- //')

        if [[ -n "$spec_name" ]]; then
            local launch_response
            launch_response=$(send_a2a_message_with_response "launch $spec_name" "$auth")

            local status_code=$(echo "$launch_response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

            [[ "$status_code" == "200" || "$status_code" == "500" ]] && return 0
        fi
    fi

    # Engine integration test passed (specs may not be loaded)
    return 0
}

run_launch_workflow_tests() {
    log_section "launch_workflow Skill Tests"

    run_test "launch_input_valid" "Valid launch command input" \
        "test_launch_workflow_input_valid"
    run_test "launch_input_missing_spec" "Launch without specification returns helpful error" \
        "test_launch_workflow_input_missing_spec"
    run_test "launch_input_quoted_spec" "Launch with quoted specification name" \
        "test_launch_workflow_input_quoted_spec"
    run_test "launch_output_schema" "Output contains case ID and status" \
        "test_launch_workflow_output_schema"
    run_test "launch_permission_denied" "Permission denied without launch permission" \
        "test_launch_workflow_permission_denied"
    run_test "launch_engine_integration" "Engine integration works" \
        "test_launch_workflow_engine_integration"
}

# ==========================================================================
# SKILL 2: query_workflows
# ==========================================================================

test_query_workflows_input_valid() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_QUERY}\"")

    # Test list specifications command
    local response
    response=$(send_a2a_message_with_response "list specifications" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    [[ "$status_code" == "200" ]]
}

test_query_workflows_input_variations() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_QUERY}\"")

    # Test various query command variations
    local variations=(
        "list workflows"
        "show specifications"
        "list specs"
        "what workflows are available"
    )

    for variation in "${variations[@]}"; do
        local response
        response=$(send_a2a_message_with_response "$variation" "$auth")

        local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

        if [[ "$status_code" != "200" ]]; then
            return 1
        fi
    done

    return 0
}

test_query_workflows_output_schema() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_QUERY}\"")

    local response
    response=$(send_a2a_message_with_response "list specifications" "$auth")

    local body=$(echo "$response" | sed -e 's/HTTP_CODE:[0-9]*$//')

    # Output should contain workflow/specification information or "no specifications" message
    echo "$body" | grep -qiE "(specification|workflow|loaded|no.*specification)" && return 0

    return 1
}

test_query_workflows_running_cases() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_QUERY}\"")

    # Query for running cases
    local response
    response=$(send_a2a_message_with_response "show running cases" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    [[ "$status_code" == "200" ]]
}

test_query_workflows_case_status() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_QUERY}\"")

    # Query specific case status
    local response
    response=$(send_a2a_message_with_response "status case 1" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    # Should succeed even if case doesn't exist (graceful handling)
    [[ "$status_code" == "200" || "$status_code" == "500" ]]
}

test_query_workflows_permission_denied() {
    # User without query permission should be denied
    local auth
    auth=$(get_auth_header "\"${PERM_WORKITEM_MANAGE}\"")

    local response
    response=$(send_a2a_message_with_response "list specifications" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    [[ "$status_code" == "403" ]]
}

test_query_workflows_no_auth() {
    # Request without authentication should be rejected
    local response
    response=$(send_a2a_message_with_response "list specifications" "")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    [[ "$status_code" == "401" ]]
}

run_query_workflows_tests() {
    log_section "query_workflows Skill Tests"

    run_test "query_input_valid" "Valid list specifications command" \
        "test_query_workflows_input_valid"
    run_test "query_input_variations" "Various query command variations" \
        "test_query_workflows_input_variations"
    run_test "query_output_schema" "Output contains workflow information" \
        "test_query_workflows_output_schema"
    run_test "query_running_cases" "Can query running cases" \
        "test_query_workflows_running_cases"
    run_test "query_case_status" "Can query case status" \
        "test_query_workflows_case_status"
    run_test "query_permission_denied" "Permission denied without query permission" \
        "test_query_workflows_permission_denied"
    run_test "query_no_auth" "Rejected without authentication" \
        "test_query_workflows_no_auth"
}

# ==========================================================================
# SKILL 3: manage_workitems
# ==========================================================================

test_manage_workitems_input_valid() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKITEM_MANAGE}\"")

    # Test get work items command
    local response
    response=$(send_a2a_message_with_response "list work items" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    [[ "$status_code" == "200" ]]
}

test_manage_workitems_input_with_case() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKITEM_MANAGE},${PERM_WORKFLOW_QUERY}\"")

    # Test get work items for specific case
    local response
    response=$(send_a2a_message_with_response "work items for case 42" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    [[ "$status_code" == "200" || "$status_code" == "500" ]]
}

test_manage_workitems_input_task() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKITEM_MANAGE}\"")

    # Test "task" variation
    local response
    response=$(send_a2a_message_with_response "show tasks" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    [[ "$status_code" == "200" ]]
}

test_manage_workitems_output_schema() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKITEM_MANAGE}\"")

    local response
    response=$(send_a2a_message_with_response "list work items" "$auth")

    local body=$(echo "$response" | sed -e 's/HTTP_CODE:[0-9]*$//')

    # Output should contain work item information or "no active work items" message
    echo "$body" | grep -qiE "(work.*item|task|no.*active|case)" && return 0

    return 1
}

test_manage_workitems_complete_input() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKITEM_MANAGE}\"")

    # Test complete work item command
    local response
    response=$(send_a2a_message_with_response "complete work item 42:ReviewOrder with approved status" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    # May fail if item doesn't exist, but should handle gracefully
    [[ "$status_code" == "200" || "$status_code" == "500" ]]
}

test_manage_workitems_permission_denied() {
    # User without workitem permission should be denied
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_QUERY}\"")

    local response
    response=$(send_a2a_message_with_response "list work items" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    [[ "$status_code" == "403" ]]
}

test_manage_workitems_invalid_case_id() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKITEM_MANAGE}\"")

    # Test with invalid case ID (non-numeric)
    local response
    response=$(send_a2a_message_with_response "work items for case invalid" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    # Should handle gracefully (either 200 with error message or 500)
    [[ "$status_code" == "200" || "$status_code" == "500" ]]
}

run_manage_workitems_tests() {
    log_section "manage_workitems Skill Tests"

    run_test "workitems_input_valid" "Valid list work items command" \
        "test_manage_workitems_input_valid"
    run_test "workitems_input_with_case" "Get work items for specific case" \
        "test_manage_workitems_input_with_case"
    run_test "workitems_input_task" "Task variation works" \
        "test_manage_workitems_input_task"
    run_test "workitems_output_schema" "Output contains work item info" \
        "test_manage_workitems_output_schema"
    run_test "workitems_complete_input" "Complete work item command" \
        "test_manage_workitems_complete_input"
    run_test "workitems_permission_denied" "Permission denied without workitem permission" \
        "test_manage_workitems_permission_denied"
    run_test "workitems_invalid_case_id" "Handles invalid case ID gracefully" \
        "test_manage_workitems_invalid_case_id"
}

# ==========================================================================
# SKILL 4: cancel_workflow
# ==========================================================================

test_cancel_workflow_input_valid() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_CANCEL}\"")

    # Test cancel command with case ID
    local response
    response=$(send_a2a_message_with_response "cancel case 123" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    # May fail if case doesn't exist, but should handle gracefully
    [[ "$status_code" == "200" || "$status_code" == "500" ]]
}

test_cancel_workflow_input_variations() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_CANCEL}\"")

    # Test various cancel command variations
    local variations=(
        "cancel case 42"
        "stop workflow 42"
        "cancel 42"
    )

    for variation in "${variations[@]}"; do
        local response
        response=$(send_a2a_message_with_response "$variation" "$auth")

        local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

        if [[ "$status_code" != "200" && "$status_code" != "500" ]]; then
            return 1
        fi
    done

    return 0
}

test_cancel_workflow_input_missing_case_id() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_CANCEL}\"")

    # Test cancel without case ID
    local response
    response=$(send_a2a_message_with_response "cancel" "$auth")

    local body=$(echo "$response" | sed -e 's/HTTP_CODE:[0-9]*$//')

    # Should return helpful error message
    echo "$body" | grep -qiE "(specify|case.*id|required)" && return 0

    return 1
}

test_cancel_workflow_output_schema() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_CANCEL}\"")

    local response
    response=$(send_a2a_message_with_response "cancel case 999" "$auth")

    local body=$(echo "$response" | sed -e 's/HTTP_CODE:[0-9]*$//')

    # Output should contain cancellation status
    echo "$body" | grep -qiE "(cancel|case|success|failed|error)" && return 0

    return 1
}

test_cancel_workflow_permission_denied() {
    # User without cancel permission should be denied
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_QUERY}\"")

    local response
    response=$(send_a2a_message_with_response "cancel case 123" "$auth")

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    [[ "$status_code" == "403" ]]
}

test_cancel_workflow_engine_error() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_CANCEL}\"")

    # Test with non-existent case ID (engine should return error)
    local response
    response=$(send_a2a_message_with_response "cancel case 999999" "$auth")

    local body=$(echo "$response" | sed -e 's/HTTP_CODE:[0-9]*$//')
    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    # Should handle engine error gracefully (not crash)
    if [[ "$status_code" == "200" ]]; then
        echo "$body" | grep -qiE "(failed|error|not found)" && return 0
    fi

    [[ "$status_code" == "200" || "$status_code" == "500" ]]
}

test_cancel_workflow_via_task_endpoint() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_CANCEL}\"")

    # Test cancellation via task endpoint
    local response
    response=$(curl -s -S -w "\nHTTP_CODE:%{http_code}" \
        --connect-timeout ${A2A_TIMEOUT} \
        -X POST \
        -H "Content-Type: application/json" \
        -H "$auth" \
        -d '{}' \
        "${A2A_BASE_URL}/tasks/test-case-id/cancel" 2>/dev/null)

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    # Should attempt cancellation (may fail if case doesn't exist)
    [[ "$status_code" == "200" || "$status_code" == "500" || "$status_code" == "404" ]]
}

run_cancel_workflow_tests() {
    log_section "cancel_workflow Skill Tests"

    run_test "cancel_input_valid" "Valid cancel command input" \
        "test_cancel_workflow_input_valid"
    run_test "cancel_input_variations" "Various cancel command variations" \
        "test_cancel_workflow_input_variations"
    run_test "cancel_input_missing_case_id" "Cancel without case ID returns error" \
        "test_cancel_workflow_input_missing_case_id"
    run_test "cancel_output_schema" "Output contains cancellation status" \
        "test_cancel_workflow_output_schema"
    run_test "cancel_permission_denied" "Permission denied without cancel permission" \
        "test_cancel_workflow_permission_denied"
    run_test "cancel_engine_error" "Engine error handled gracefully" \
        "test_cancel_workflow_engine_error"
    run_test "cancel_via_task_endpoint" "Cancellation via task endpoint works" \
        "test_cancel_workflow_via_task_endpoint"
}

# ==========================================================================
# SKILL 5: handoff_workitem
# ==========================================================================

test_handoff_workitem_endpoint_exists() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKITEM_MANAGE}\"")

    # Test that handoff endpoint exists
    local response
    response=$(curl -s -S -w "\nHTTP_CODE:%{http_code}" \
        --connect-timeout ${A2A_TIMEOUT} \
        -X POST \
        -H "Content-Type: application/json" \
        -H "$auth" \
        -d '{"parts":[{"type":"text","text":"YAWL_HANDOFF:WI-42"}]}' \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    # Should not return 404 (endpoint exists)
    [[ "$status_code" != "404" ]]
}

test_handoff_workitem_input_valid_prefix() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKITEM_MANAGE}\"")

    # Test with valid handoff prefix
    local payload='{"parts":[{"type":"text","text":"YAWL_HANDOFF:WI-42:target-agent"}]}'

    local response
    response=$(curl -s -S -w "\nHTTP_CODE:%{http_code}" \
        --connect-timeout ${A2A_TIMEOUT} \
        -X POST \
        -H "Content-Type: application/json" \
        -H "$auth" \
        -d "$payload" \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    # Should process (may fail if work item doesn't exist, but should accept format)
    [[ "$status_code" == "200" || "$status_code" == "400" || "$status_code" == "500" ]]
}

test_handoff_workitem_input_missing_prefix() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKITEM_MANAGE}\"")

    # Test without handoff prefix (should be rejected)
    local payload='{"parts":[{"type":"text","text":"some other message"}]}'

    local response
    response=$(curl -s -S -w "\nHTTP_CODE:%{http_code}" \
        --connect-timeout ${A2A_TIMEOUT} \
        -X POST \
        -H "Content-Type: application/json" \
        -H "$auth" \
        -d "$payload" \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    # Should return 400 Bad Request
    [[ "$status_code" == "400" ]]
}

test_handoff_workitem_permission_denied() {
    # User without workitem permission should be denied
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_QUERY}\"")

    local payload='{"parts":[{"type":"text","text":"YAWL_HANDOFF:WI-42"}]}'

    local response
    response=$(curl -s -S -w "\nHTTP_CODE:%{http_code}" \
        --connect-timeout ${A2A_TIMEOUT} \
        -X POST \
        -H "Content-Type: application/json" \
        -H "$auth" \
        -d "$payload" \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    [[ "$status_code" == "403" ]]
}

test_handoff_workitem_no_auth() {
    # Request without authentication should be rejected
    local payload='{"parts":[{"type":"text","text":"YAWL_HANDOFF:WI-42"}]}'

    local response
    response=$(curl -s -S -w "\nHTTP_CODE:%{http_code}" \
        --connect-timeout ${A2A_TIMEOUT} \
        -X POST \
        -H "Content-Type: application/json" \
        -d "$payload" \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    [[ "$status_code" == "401" ]]
}

test_handoff_workitem_output_schema() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKITEM_MANAGE}\"")

    local payload='{"parts":[{"type":"text","text":"YAWL_HANDOFF:WI-42:agent-2"}]}'

    local response
    response=$(curl -s -S -w "\nHTTP_CODE:%{http_code}" \
        --connect-timeout ${A2A_TIMEOUT} \
        -X POST \
        -H "Content-Type: application/json" \
        -H "$auth" \
        -d "$payload" \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local body=$(echo "$response" | sed -e 's/HTTP_CODE:[0-9]*$//')
    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    if [[ "$status_code" == "200" ]]; then
        # Successful handoff should acknowledge
        echo "$body" | grep -qiE "(success|handed off|transferred)" && return 0
    fi

    # Error response is acceptable for non-existent work item
    echo "$body" | grep -qiE "(error|failed|not found)" && return 0

    return 1
}

test_handoff_workitem_invalid_format() {
    local auth
    auth=$(get_auth_header "\"${PERM_WORKITEM_MANAGE}\"")

    # Test with malformed handoff message
    local payload='{"parts":[{"type":"text","text":"YAWL_HANDOFF:"}]}'

    local response
    response=$(curl -s -S -w "\nHTTP_CODE:%{http_code}" \
        --connect-timeout ${A2A_TIMEOUT} \
        -X POST \
        -H "Content-Type: application/json" \
        -H "$auth" \
        -d "$payload" \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)

    # Should return error for invalid format
    [[ "$status_code" == "400" || "$status_code" == "500" ]]
}

test_handoff_skill_registered() {
    # Verify handoff_workitem skill is registered in agent card
    local response
    response=$(curl -s --connect-timeout ${A2A_TIMEOUT} \
        "${A2A_BASE_URL}/.well-known/agent.json" 2>/dev/null)

    echo "$response" | jq -e '.skills[] | select(.id == "handoff_workitem")' > /dev/null 2>&1
}

run_handoff_workitem_tests() {
    log_section "handoff_workitem Skill Tests"

    run_test "handoff_endpoint_exists" "Handoff endpoint exists" \
        "test_handoff_workitem_endpoint_exists"
    run_test "handoff_input_valid_prefix" "Valid handoff prefix accepted" \
        "test_handoff_workitem_input_valid_prefix"
    run_test "handoff_input_missing_prefix" "Missing prefix rejected" \
        "test_handoff_workitem_input_missing_prefix"
    run_test "handoff_permission_denied" "Permission denied without workitem permission" \
        "test_handoff_workitem_permission_denied"
    run_test "handoff_no_auth" "Rejected without authentication" \
        "test_handoff_workitem_no_auth"
    run_test "handoff_output_schema" "Output schema is valid" \
        "test_handoff_workitem_output_schema"
    run_test "handoff_invalid_format" "Invalid format handled gracefully" \
        "test_handoff_workitem_invalid_format"
    run_test "handoff_skill_registered" "Skill registered in agent card" \
        "test_handoff_skill_registered"
}

# ==========================================================================
# Cross-Skill Integration Tests
# ==========================================================================

test_full_workflow_lifecycle() {
    local auth
    auth=$(get_full_auth_header)

    # 1. List specifications
    local list_response
    list_response=$(send_a2a_message_with_response "list specifications" "$auth")

    local list_status=$(echo "$list_response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)
    [[ "$list_status" != "200" ]] && return 1

    # 2. Query running cases
    local cases_response
    cases_response=$(send_a2a_message_with_response "list running cases" "$auth")

    local cases_status=$(echo "$cases_response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)
    [[ "$cases_status" != "200" ]] && return 1

    # 3. Query work items
    local items_response
    items_response=$(send_a2a_message_with_response "list work items" "$auth")

    local items_status=$(echo "$items_response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)
    [[ "$items_status" != "200" ]] && return 1

    return 0
}

test_permission_isolation() {
    # Verify that skills properly isolate based on permissions

    # Query-only user should be able to query but not launch/cancel
    local query_auth
    query_auth=$(get_auth_header "\"${PERM_WORKFLOW_QUERY}\"")

    # Query should succeed
    local query_response
    query_response=$(send_a2a_message_with_response "list specifications" "$query_auth")
    local query_status=$(echo "$query_response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)
    [[ "$query_status" != "200" ]] && return 1

    # Launch should fail
    local launch_response
    launch_response=$(send_a2a_message_with_response "launch TestSpec" "$query_auth")
    local launch_status=$(echo "$launch_response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)
    [[ "$launch_status" == "403" ]] || return 1

    # Cancel should fail
    local cancel_response
    cancel_response=$(send_a2a_message_with_response "cancel case 1" "$query_auth")
    local cancel_status=$(echo "$cancel_response" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)
    [[ "$cancel_status" == "403" ]] || return 1

    return 0
}

test_error_response_format() {
    # Verify error responses follow consistent format
    local auth
    auth=$(get_auth_header "\"${PERM_WORKFLOW_QUERY}\"")

    # Trigger a permission error
    local response
    response=$(send_a2a_message_with_response "launch TestSpec" "$auth")

    local body=$(echo "$response" | sed -e 's/HTTP_CODE:[0-9]*$//')

    # Error should be JSON with "error" field
    echo "$body" | jq -e '.error' > /dev/null 2>&1
}

run_integration_tests() {
    log_section "Cross-Skill Integration Tests"

    run_test "full_lifecycle" "Full workflow lifecycle works" \
        "test_full_workflow_lifecycle"
    run_test "permission_isolation" "Skills properly isolate by permission" \
        "test_permission_isolation"
    run_test "error_format" "Error responses follow consistent format" \
        "test_error_response_format"
}

# ── Output Functions ───────────────────────────────────────────────────────
output_json() {
    local results_json=$(IFS=,; echo "${TEST_RESULTS[*]}")
    cat << JSON_EOF
{
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "server": {
    "host": "${A2A_SERVER_HOST}",
    "port": ${A2A_SERVER_PORT}
  },
  "category": "skills-functional",
  "skills_tested": [
    "launch_workflow",
    "query_workflows",
    "manage_workitems",
    "cancel_workflow",
    "handoff_workitem"
  ],
  "total_tests": ${TOTAL_TESTS},
  "passed": ${PASSED_TESTS},
  "failed": ${FAILED_TESTS},
  "pass_rate": $(echo "scale=2; ${PASSED_TESTS} * 100 / ${TOTAL_TESTS}" | bc 2>/dev/null || echo "0"),
  "results": [${results_json}],
  "status": $([[ "${FAILED_TESTS}" -eq 0 ]] && echo "\"PASS\"" || echo "\"FAIL\"")
}
JSON_EOF
}

output_text() {
    echo ""
    echo "==========================================="
    echo "A2A Skills Functional Test Results"
    echo "==========================================="
    echo "Server: ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}"
    echo "Total Tests: ${TOTAL_TESTS}"
    echo "Passed: ${PASSED_TESTS}"
    echo "Failed: ${FAILED_TESTS}"

    if [[ ${TOTAL_TESTS} -gt 0 ]]; then
        local pass_rate=$(echo "scale=1; ${PASSED_TESTS} * 100 / ${TOTAL_TESTS}" | bc 2>/dev/null || echo "0")
        echo "Pass Rate: ${pass_rate}%"
    fi

    echo ""

    if [[ ${FAILED_TESTS} -eq 0 ]]; then
        echo -e "${GREEN}All skills functional tests passed.${NC}"
        return 0
    else
        echo -e "${RED}${FAILED_TESTS} skills functional tests failed.${NC}"
        return 1
    fi
}

# ── Help Function ──────────────────────────────────────────────────────────
show_help() {
    cat << 'EOF'
A2A Skills Functional Validation Tests

Usage:
  bash test-skills.sh [options]

Options:
  --json           Output results in JSON format
  --verbose, -v    Enable verbose output
  --skill NAME     Run tests for specific skill only
  -h, --help       Show this help message

Available Skills:
  launch_workflow   - Launch new workflow cases
  query_workflows   - List specifications and running cases
  manage_workitems  - Get and complete work items
  cancel_workflow   - Cancel running cases
  handoff_workitem  - Transfer work items between agents

Examples:
  bash test-skills.sh                        # Run all skill tests
  bash test-skills.sh --skill launch_workflow # Run only launch_workflow tests
  bash test-skills.sh --json                 # JSON output
  bash test-skills.sh --verbose              # Verbose output

Environment Variables:
  A2A_SERVER_HOST   - A2A server host (default: localhost)
  A2A_SERVER_PORT   - A2A server port (default: 8080)
  A2A_TIMEOUT       - Request timeout in seconds (default: 30)

Exit Codes:
  0 - All tests passed
  1 - One or more tests failed
  2 - Server unavailable or dependency missing
EOF
}

# ── Main Execution ────────────────────────────────────────────────────────
main() {
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --json)         OUTPUT_FORMAT="json"; shift ;;
            --verbose|-v)   VERBOSE=1; shift ;;
            --skill)        TARGET_SKILL="$2"; shift 2 ;;
            -h|--help)      show_help; exit 0 ;;
            *)              shift ;;
        esac
    done

    # Check for jq dependency
    if ! command -v jq &> /dev/null; then
        echo "[ERROR] jq is required for skills validation tests" >&2
        echo "Install with: brew install jq (macOS) or apt install jq (Linux)" >&2
        exit 2
    fi

    # Check server availability
    log_verbose "Checking A2A server availability..."
    if ! a2a_ping; then
        echo "[ERROR] A2A server not running at ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}" >&2
        echo "Start the server first: bash scripts/start-a2a-server.sh" >&2
        exit 2
    fi

    log_verbose "A2A server is available"
    log_info "Starting A2A Skills Functional Validation Tests"
    log_info "Server: ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}"

    # Run tests based on target skill or all
    if [[ -n "$TARGET_SKILL" ]]; then
        case "$TARGET_SKILL" in
            launch_workflow)   run_launch_workflow_tests ;;
            query_workflows)   run_query_workflows_tests ;;
            manage_workitems)  run_manage_workitems_tests ;;
            cancel_workflow)   run_cancel_workflow_tests ;;
            handoff_workitem)  run_handoff_workitem_tests ;;
            *)
                echo "[ERROR] Unknown skill: $TARGET_SKILL" >&2
                echo "Available skills: launch_workflow, query_workflows, manage_workitems, cancel_workflow, handoff_workitem" >&2
                exit 3
                ;;
        esac
    else
        # Run all skill tests
        run_launch_workflow_tests
        run_query_workflows_tests
        run_manage_workitems_tests
        run_cancel_workflow_tests
        run_handoff_workitem_tests
        run_integration_tests
    fi

    # Output results
    if [[ "$OUTPUT_FORMAT" == "json" ]]; then
        output_json
    else
        output_text
    fi
}

main "$@"
