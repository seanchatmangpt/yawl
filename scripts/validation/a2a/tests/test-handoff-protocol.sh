#!/usr/bin/env bash
# ==========================================================================
# test-handoff-protocol.sh — A2A Handoff Protocol Validation Tests
#
# Validates the ADR-025 agent coordination handoff protocol:
#   - JWT-based handoff token generation
#   - Token validation and expiration
#   - Handoff message structure
#   - Session creation and management
#   - Error handling for invalid handoffs
#
# Usage:
#   bash scripts/validation/a2a/tests/test-handoff-protocol.sh
#   bash scripts/validation/a2a/tests/test-handoff-protocol.sh --verbose
#   bash scripts/validation/a2a/tests/test-handoff-protocol.sh --json
#
# Exit Codes:
#   0 - All handoff tests passed
#   1 - One or more handoff tests failed
#   2 - Server not available
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
A2A_SERVER_HOST="${A2A_SERVER_HOST:-localhost}"
A2A_SERVER_PORT="${A2A_SERVER_PORT:-8080}"
A2A_TIMEOUT="${A2A_TIMEOUT:-30}"

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
declare -a TEST_RESULTS=()

# ── Logging Functions ─────────────────────────────────────────────────────
log_info() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${CYAN}[INFO]${NC} $*"
}

log_success() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${GREEN}[PASS]${NC} $*"
}

log_error() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${RED}[FAIL]${NC} $*" >&2
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

# ── Handoff Endpoint Tests ────────────────────────────────────────────────
test_handoff_endpoint_requires_auth() {
    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -d '{"message":"YAWL_HANDOFF:WI-42"}' \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    [[ "$status_code" == "401" ]]
}

test_handoff_endpoint_accepts_valid_auth() {
    local api_key
    api_key=$(generate_api_key)

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "X-API-Key: ${api_key}" \
        -d '{"message":"YAWL_HANDOFF:WI-42"}' \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    # Should not be 401 (authentication should succeed)
    [[ "$status_code" != "401" ]]
}

test_handoff_requires_workitem_permission() {
    # Create a JWT with only query permission
    local query_jwt
    query_jwt=$(generate_jwt "query-only-agent" '"workflow:query"' "yawl-a2a")

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${query_jwt}" \
        -d '{"message":"YAWL_HANDOFF:WI-42"}' \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    # Should be 403 (forbidden - insufficient permissions)
    [[ "$status_code" == "403" ]]
}

# ── Handoff Message Format Tests ──────────────────────────────────────────
test_handoff_message_format_valid() {
    local api_key
    api_key=$(generate_api_key)

    # Valid handoff message format
    local handoff_message='{"message":"YAWL_HANDOFF:WI-42:agent-a:agent-b:session-123"}'

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "X-API-Key: ${api_key}" \
        -d "$handoff_message" \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    # Should be 200 (OK) or 400 (if session invalid) but not 401/403
    [[ "$status_code" != "401" ]] && [[ "$status_code" != "403" ]]
}

test_handoff_message_missing_prefix() {
    local api_key
    api_key=$(generate_api_key)

    # Missing YAWL_HANDOFF prefix
    local handoff_message='{"message":"WI-42:agent-a:agent-b"}'

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "X-API-Key: ${api_key}" \
        -d "$handoff_message" \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    # Should be 400 (bad request) - invalid handoff format
    [[ "$status_code" == "400" ]] || [[ "$status_code" == "500" ]]
}

test_handoff_message_malformed_json() {
    local api_key
    api_key=$(generate_api_key)

    # Malformed JSON
    local handoff_message='{message:"YAWL_HANDOFF:WI-42"}'

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "X-API-Key: ${api_key}" \
        -d "$handoff_message" \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    # Should be 400 (bad request)
    [[ "$status_code" == "400" ]]
}

# ── Handoff Response Tests ────────────────────────────────────────────────
test_handoff_success_response_format() {
    local api_key
    api_key=$(generate_api_key)

    local response
    response=$(curl -s -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "X-API-Key: ${api_key}" \
        -d '{"message":"YAWL_HANDOFF:WI-42"}' \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    # Response should mention the work item ID
    echo "$response" | grep -q "WI-42" || echo "$response" | grep -q "handoff" || echo "$response" | grep -q "success"
}

test_handoff_error_response_json() {
    # Test error response is JSON
    local response
    response=$(curl -s -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -d '{"message":"YAWL_HANDOFF:WI-42"}' \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    # Should be valid JSON
    echo "$response" | jq -e '.' > /dev/null 2>&1
}

# ── Token Expiration Tests ────────────────────────────────────────────────
test_handoff_token_expired_rejected() {
    local api_key
    api_key=$(generate_api_key)

    # Create an expired JWT for handoff
    local expired_jwt
    expired_jwt=$(generate_jwt "test-agent" '"workitem:manage"' "yawl-a2a" -3600)

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${expired_jwt}" \
        -d '{"message":"YAWL_HANDOFF:WI-42"}' \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    # Expired tokens should be rejected
    [[ "$status_code" == "401" ]]
}

# ── Edge Case Tests ───────────────────────────────────────────────────────
test_handoff_empty_workitem_id() {
    local api_key
    api_key=$(generate_api_key)

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "X-API-Key: ${api_key}" \
        -d '{"message":"YAWL_HANDOFF:"}' \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    # Should be 400 (bad request)
    [[ "$status_code" == "400" ]] || [[ "$status_code" == "500" ]]
}

test_handoff_special_characters_in_id() {
    local api_key
    api_key=$(generate_api_key)

    # Work item ID with special characters
    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "X-API-Key: ${api_key}" \
        -d '{"message":"YAWL_HANDOFF:WI-<test>-123"}' \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    # Should handle gracefully (not 500 server error)
    [[ "$status_code" != "500" ]] || [[ "$status_code" == "400" ]]
}

test_handoff_very_long_workitem_id() {
    local api_key
    api_key=$(generate_api_key)

    # Very long work item ID (1000+ chars)
    local long_id=$(head -c 1000 /dev/zero | tr '\0' 'W')
    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "X-API-Key: ${api_key}" \
        -d "{\"message\":\"YAWL_HANDOFF:${long_id}\"}" \
        "${A2A_BASE_URL}/handoff" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    # Should handle without crashing
    [[ "$status_code" != "500" ]]
}

# ── Concurrent Handoff Tests ──────────────────────────────────────────────
test_handoff_concurrent_requests() {
    local api_key
    api_key=$(generate_api_key)

    # Send multiple concurrent handoff requests
    local pids=()
    for i in {1..5}; do
        curl -s -X POST \
            --connect-timeout ${A2A_TIMEOUT} \
            -H "Content-Type: application/json" \
            -H "X-API-Key: ${api_key}" \
            -d "{\"message\":\"YAWL_HANDOFF:WI-concurrent-${i}\"}" \
            "${A2A_BASE_URL}/handoff" > /dev/null 2>&1 &
        pids+=($!)
    done

    # Wait for all requests
    local failed=0
    for pid in "${pids[@]}"; do
        wait "$pid" || ((failed++)) || true
    done

    # All requests should complete (even if some fail with 400)
    [[ $failed -eq 0 ]]
}

# ── Run All Handoff Tests ─────────────────────────────────────────────────
run_all_handoff_tests() {
    log_info "Starting A2A Handoff Protocol Tests"
    log_info "Server: ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}"
    echo ""

    # Handoff Endpoint Tests
    echo "=== Handoff Endpoint ==="
    run_test "handoff_requires_auth" "Handoff endpoint requires authentication" \
        "test_handoff_endpoint_requires_auth"
    run_test "handoff_accepts_valid_auth" "Handoff accepts valid authentication" \
        "test_handoff_endpoint_accepts_valid_auth"
    run_test "handoff_permission_check" "Handoff requires workitem permission" \
        "test_handoff_requires_workitem_permission"
    echo ""

    # Handoff Message Format Tests
    echo "=== Handoff Message Format ==="
    run_test "handoff_format_valid" "Valid handoff message format accepted" \
        "test_handoff_message_format_valid"
    run_test "handoff_missing_prefix" "Missing YAWL_HANDOFF prefix rejected" \
        "test_handoff_message_missing_prefix"
    run_test "handoff_malformed_json" "Malformed JSON rejected" \
        "test_handoff_message_malformed_json"
    echo ""

    # Handoff Response Tests
    echo "=== Handoff Response ==="
    run_test "handoff_success_format" "Success response has expected format" \
        "test_handoff_success_response_format"
    run_test "handoff_error_json" "Error response is valid JSON" \
        "test_handoff_error_response_json"
    echo ""

    # Token Expiration Tests
    echo "=== Token Expiration ==="
    run_test "handoff_expired_token" "Expired tokens are rejected" \
        "test_handoff_token_expired_rejected"
    echo ""

    # Edge Case Tests
    echo "=== Edge Cases ==="
    run_test "handoff_empty_id" "Empty work item ID handled" \
        "test_handoff_empty_workitem_id"
    run_test "handoff_special_chars" "Special characters in ID handled" \
        "test_handoff_special_characters_in_id"
    run_test "handoff_long_id" "Very long work item ID handled" \
        "test_handoff_very_long_workitem_id"
    echo ""

    # Concurrent Tests
    echo "=== Concurrent Requests ==="
    run_test "handoff_concurrent" "Concurrent handoff requests handled" \
        "test_handoff_concurrent_requests"
    echo ""
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
  "category": "handoff",
  "total_tests": ${TOTAL_TESTS},
  "passed": ${PASSED_TESTS},
  "failed": ${FAILED_TESTS},
  "results": [${results_json}],
  "status": $([[ "${FAILED_TESTS}" -eq 0 ]] && echo "\"PASS\"" || echo "\"FAIL\"")
}
JSON_EOF
}

output_text() {
    echo ""
    echo "==========================================="
    echo "A2A Handoff Protocol Test Results"
    echo "==========================================="
    echo "Total Tests: ${TOTAL_TESTS}"
    echo "Passed: ${PASSED_TESTS}"
    echo "Failed: ${FAILED_TESTS}"
    echo ""

    if [[ ${FAILED_TESTS} -eq 0 ]]; then
        echo -e "${GREEN}All handoff protocol tests passed.${NC}"
        return 0
    else
        echo -e "${RED}${FAILED_TESTS} handoff protocol tests failed.${NC}"
        return 1
    fi
}

# ── Main Execution ────────────────────────────────────────────────────────
main() {
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --json)     OUTPUT_FORMAT="json"; shift ;;
            --verbose|-v) VERBOSE=1; shift ;;
            -h|--help)
                sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
                exit 0 ;;
            *)  shift ;;
        esac
    done

    # Check for jq dependency
    if ! command -v jq &> /dev/null; then
        echo "[ERROR] jq is required for handoff validation tests" >&2
        exit 2
    fi

    # Check server availability
    log_verbose "Checking A2A server availability..."
    if ! a2a_ping; then
        echo "[ERROR] A2A server not running at ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}" >&2
        exit 2
    fi

    log_verbose "A2A server is available"

    # Run tests
    run_all_handoff_tests

    # Output results
    if [[ "$OUTPUT_FORMAT" == "json" ]]; then
        output_json
    else
        output_text
    fi
}

main "$@"
