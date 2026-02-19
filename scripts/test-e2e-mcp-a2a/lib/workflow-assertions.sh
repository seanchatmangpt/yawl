#!/usr/bin/env bash
# ==========================================================================
# workflow-assertions.sh — Workflow Test Assertion Functions
#
# Provides assertion functions for validating workflow test results.
#
# Usage: Source this file to get workflow test assertion functions.
# ==========================================================================

# ── Test State Management ───────────────────────────────────────────────
declare -a TEST_PASSED=()
declare -a TEST_FAILED=()
declare -i TOTAL_TESTS=0
declare -i PASSED_TESTS=0
declare -i FAILED_TESTS=0

# ── Assertion Functions ──────────────────────────────────────────────────
assert_equal() {
    local actual="$1"
    local expected="$2"
    local test_name="$3"

    ((TOTAL_TESTS++))

    if [[ "$actual" == "$expected" ]]; then
        TEST_PASSED+=("$test_name")
        ((PASSED_TESTS++))
        log_success "$test_name: Expected '$expected', got '$actual'"
        return 0
    else
        TEST_FAILED+=("$test_name")
        ((FAILED_TESTS++))
        log_error "$test_name: Expected '$expected', got '$actual'"
        return 1
    fi
}

assert_not_empty() {
    local value="$1"
    local test_name="$2"

    ((TOTAL_TESTS++))

    if [[ -n "$value" ]]; then
        TEST_PASSED+=("$test_name")
        ((PASSED_TESTS++))
        log_success "$test_name: Value is not empty"
        return 0
    else
        TEST_FAILED+=("$test_name")
        ((FAILED_TESTS++))
        log_error "$test_name: Value is empty"
        return 1
    fi
}

assert_contains() {
    local haystack="$1"
    local needle="$2"
    local test_name="$3"

    ((TOTAL_TESTS++))

    if echo "$haystack" | grep -q "$needle"; then
        TEST_PASSED+=("$test_name")
        ((PASSED_TESTS++))
        log_success "$test_name: Contains '$needle'"
        return 0
    else
        TEST_FAILED+=("$test_name")
        ((FAILED_TESTS++))
        log_error "$test_name: Does not contain '$needle'"
        return 1
    fi
}

assert_success() {
    local command="$1"
    local test_name="$2"

    ((TOTAL_TESTS++))

    if eval "$command" >/dev/null 2>&1; then
        TEST_PASSED+=("$test_name")
        ((PASSED_TESTS++))
        log_success "$test_name: Command succeeded"
        return 0
    else
        TEST_FAILED+=("$test_name")
        ((FAILED_TESTS++))
        log_error "$test_name: Command failed"
        return 1
    fi
}

assert_status() {
    local url="$1"
    local expected_status="$2"
    local test_name="$3"

    ((TOTAL_TESTS++))

    local actual_status
    actual_status=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null)

    if [[ "$actual_status" == "$expected_status" ]]; then
        TEST_PASSED+=("$test_name")
        ((PASSED_TESTS++))
        log_success "$test_name: HTTP status $actual_status"
        return 0
    else
        TEST_FAILED+=("$test_name")
        ((FAILED_TESTS++))
        log_error "$test_name: Expected HTTP status $expected_status, got $actual_status"
        return 1
    fi
}

assert_case_complete() {
    local case_id="$1"
    local timeout="${2:-30}"
    local test_name="Case $case_id completion"

    ((TOTAL_TESTS++))

    local attempts=0
    while [[ $attempts -lt $timeout ]]; do
        local status
        status=$(yawl_get_case_status "$case_id" 2>/dev/null || echo "")

        case "$status" in
            "complete"|"closed"|"finished")
                TEST_PASSED+=("$test_name")
                ((PASSED_TESTS++))
                log_success "$test_name: Case completed with status '$status'"
                return 0
                ;;
        esac

        ((attempts++))
        sleep 1
    done

    TEST_FAILED+=("$test_name")
    ((FAILED_TESTS++))
    log_error "$test_name: Case did not complete within $timeout seconds"
    return 1
}

assert_work_item_available() {
    local case_id="$1"
    local expected_items="$2"
    local test_name="Work items available for case $case_id"

    ((TOTAL_TESTS++))

    local work_items
    work_items=$(yawl_get_work_items "$case_id" 2>/dev/null || echo "")
    local item_count=$(echo "$work_items" | wc -l)

    if [[ "$item_count" -ge "$expected_items" ]]; then
        TEST_PASSED+=("$test_name")
        ((PASSED_TESTS++))
        log_success "$test_name: Found $item_count work items (expected >= $expected_items)"
        return 0
    else
        TEST_FAILED+=("$test_name")
        ((FAILED_TESTS++))
        log_error "$test_name: Found $item_count work items (expected >= $expected_items)"
        return 1
    fi
}

assert_mcp_tool_response() {
    local tool_name="$1"
    local request_data="$2"
    local expected_field="$3"
    local test_name="MCP tool $tool_name response"

    ((TOTAL_TESTS++))

    # Send MCP request
    echo "$request_data" | timeout 30 nc -w 30 localhost 9090 > "${TEMP_DIR}/mcp-response.json" 2>/dev/null

    if [[ -s "${TEMP_DIR}/mcp-response.json" ]] && \
       grep -q "\"result\"" "${TEMP_DIR}/mcp-response.json" && \
       grep -q "$expected_field" "${TEMP_DIR}/mcp-response.json"; then
        TEST_PASSED+=("$test_name")
        ((PASSED_TESTS++))
        log_success "$test_name: Tool responded with $expected_field"
        return 0
    else
        TEST_FAILED+=("$test_name")
        ((FAILED_TESTS++))
        log_error "$test_name: Tool did not respond correctly"
        return 1
    fi
}

assert_a2a_message_success() {
    local message_type="$1"
    local request_data="$2"
    local test_name="A2A $message_type message"

    ((TOTAL_TESTS++))

    # Generate JWT
    local jwt=$(generate_jwt "test-user" "\"workflow:launch\"" "yawl-a2a")

    # Send A2A request
    if curl -s -X POST \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $jwt" \
        -d "$request_data" \
        "${A2A_SERVER_URL}/" > "${TEMP_DIR}/a2a-response.json" 2>/dev/null; then

        if grep -q "success" "${TEMP_DIR}/a2a-response.json" || \
           grep -q "caseId" "${TEMP_DIR}/a2a-response.json"; then
            TEST_PASSED+=("$test_name")
            ((PASSED_TESTS++))
            log_success "$test_name: Message processed successfully"
            return 0
        fi
    fi

    TEST_FAILED+=("$test_name")
    ((FAILED_TESTS++))
    log_error "$test_name: Message failed to process"
    return 1
}

# ── Test Summary Functions ───────────────────────────────────────────────
print_test_summary() {
    echo ""
    echo -e "${BOLD}=== Test Summary ===${NC}"
    echo "Total Tests: $TOTAL_TESTS"
    echo -e "${GREEN}Passed: $PASSED_TESTS${NC}"
    echo -e "${RED}Failed: $FAILED_TESTS${NC}"

    if [[ $FAILED_TESTS -eq 0 ]]; then
        echo ""
        echo -e "${GREEN}✓ All tests passed!${NC}"
        return 0
    else
        echo ""
        echo -e "${RED}✗ $FAILED_TESTS tests failed.${NC}"
        echo ""
        echo "Failed Tests:"
        for test in "${TEST_FAILED[@]}"; do
            echo "  - $test"
        done
        return 1
    fi
}

get_test_results_json() {
    cat << JSON_EOF
{
  "total_tests": $TOTAL_TESTS,
  "passed_tests": $PASSED_TESTS,
  "failed_tests": $FAILED_TESTS,
  "passed_tests_list": $(printf '%s\n' "${TEST_PASSED[@]}" | jq -R -s 'split("\n")[:-1]'),
  "failed_tests_list": $(printf '%s\n' "${TEST_FAILED[@]}" | jq -R -s 'split("\n")[:-1]'),
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
}
JSON_EOF
}

# ─── Test Cleanup ────────────────────────────────────────────────────────
cleanup_test_state() {
    # Clear test arrays
    TEST_PASSED=()
    TEST_FAILED=()
    TOTAL_TESTS=0
    PASSED_TESTS=0
    FAILED_TESTS=0

    # Clear temporary files
    [[ -d "${TEMP_DIR:-/tmp}" ]] && rm -rf "${TEMP_DIR:-/tmp}" && mkdir -p "${TEMP_DIR:-/tmp}"
}

# ─── Test Utilities ─────────────────────────────────────────────────────
run_test_suite() {
    local suite_name="$1"
    shift
    local tests=("$@")

    echo -e "\n${BOLD}${BLUE}=== Running $suite_name Test Suite ===${NC}"
    echo "Tests: ${#tests[@]}"

    for test in "${tests[@]}"; do
        echo -n "Running $test... "
        if eval "$test"; then
            echo -e "${GREEN}PASS${NC}"
        else
            echo -e "${RED}FAIL${NC}"
        fi
    done

    print_test_summary
    return $?
}

# Integration with workflow-common
if command -v record_test_result >/dev/null 2>&1; then
    record_test_result() {
        local test_name="$1"
        local status="$2"

        if [[ "$status" == "pass" ]]; then
            TEST_PASSED+=("$test_name")
            ((PASSED_TESTS++))
        else
            TEST_FAILED+=("$test_name")
            ((FAILED_TESTS++))
        fi

        ((TOTAL_TESTS++))
    }
fi