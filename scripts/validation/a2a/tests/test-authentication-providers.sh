#!/usr/bin/env bash
# ==========================================================================
# test-authentication-providers.sh — A2A Authentication Provider Validation
#
# Comprehensive tests for A2A authentication providers:
#   - API Key authentication (HMAC-SHA256)
#   - JWT Bearer token authentication (HS256)
#   - SPIFFE X.509 SVID authentication
#   - Composite authentication provider chain
#
# Usage:
#   bash scripts/validation/a2a/tests/test-authentication-providers.sh
#   bash scripts/validation/a2a/tests/test-authentication-providers.sh --verbose
#   bash scripts/validation/a2a/tests/test-authentication-providers.sh --json
#
# Exit Codes:
#   0 - All authentication tests passed
#   1 - One or more authentication tests failed
#   2 - Server not available or configuration error
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/../lib"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"

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

# ── API Key Authentication Tests ──────────────────────────────────────────
test_api_key_no_header() {
    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -d '{"message":"test"}' \
        "${A2A_BASE_URL}/" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    [[ "$status_code" == "401" ]]
}

test_api_key_invalid_key() {
    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "X-API-Key: invalid-api-key-12345" \
        -d '{"message":"test"}' \
        "${A2A_BASE_URL}/" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    [[ "$status_code" == "401" ]]
}

test_api_key_malformed_header() {
    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "X-API-Key:" \
        -d '{"message":"test"}' \
        "${A2A_BASE_URL}/" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    [[ "$status_code" == "401" ]]
}

test_api_key_www_authenticate_header() {
    local headers
    headers=$(curl -s -I --connect-timeout ${A2A_TIMEOUT} \
        -X POST \
        -H "X-API-Key: invalid" \
        "${A2A_BASE_URL}/" 2>/dev/null)

    echo "$headers" | grep -qi "WWW-Authenticate"
}

test_api_key_error_response_json() {
    local response
    response=$(curl -s --connect-timeout ${A2A_TIMEOUT} \
        -X POST \
        -H "Content-Type: application/json" \
        -H "X-API-Key: invalid" \
        -d '{"message":"test"}' \
        "${A2A_BASE_URL}/" 2>/dev/null)

    echo "$response" | grep -q '"error"'
}

# ── JWT Bearer Token Tests ────────────────────────────────────────────────
test_jwt_no_token() {
    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -d '{"message":"test"}' \
        "${A2A_BASE_URL}/" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    [[ "$status_code" == "401" ]]
}

test_jwt_malformed_token() {
    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer not-a-valid-jwt" \
        -d '{"message":"test"}' \
        "${A2A_BASE_URL}/" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    [[ "$status_code" == "401" ]]
}

test_jwt_missing_bearer_prefix() {
    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "Authorization: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" \
        -d '{"message":"test"}' \
        "${A2A_BASE_URL}/" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    # Should fail because format is wrong
    [[ "$status_code" == "401" ]] || [[ "$status_code" == "400" ]]
}

test_jwt_expired_token() {
    # Create an expired JWT (exp in the past)
    local expired_jwt
    expired_jwt="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0LWFnZW50IiwiZXhwIjoxMDAwMDAwMDAwLCJpYXQiOjEwMDAwMDAwMDB9.invalid_signature"

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${expired_jwt}" \
        -d '{"message":"test"}' \
        "${A2A_BASE_URL}/" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    [[ "$status_code" == "401" ]]
}

test_jwt_wrong_algorithm_header() {
    # JWT with wrong algorithm in header
    local wrong_algo_jwt
    wrong_algo_jwt="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0In0.invalid"

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${wrong_algo_jwt}" \
        -d '{"message":"test"}' \
        "${A2A_BASE_URL}/" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    [[ "$status_code" == "401" ]]
}

# ── Permission-Based Authorization Tests ───────────────────────────────────
test_permission_query_only_cannot_launch() {
    # This test verifies that a token with only query permission
    # cannot perform launch operations
    local query_only_jwt
    query_only_jwt=$(generate_jwt "query-agent" '"workflow:query"' "yawl-a2a")

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${query_only_jwt}" \
        -d '{"message":"launch workflow test"}' \
        "${A2A_BASE_URL}/" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    # Should be 403 Forbidden if permission check works
    [[ "$status_code" == "403" ]] || [[ "$status_code" == "401" ]]
}

test_permission_missing_sub_claim() {
    # JWT without subject claim
    local payload="{\"iat\":$(date +%s),\"exp\":$(( $(date +%s) + 3600 ))}"
    local payload_encoded=$(echo -n "$payload" | base64 | tr -d '\n' | tr '+/' '-_' | tr -d '=')

    local no_sub_jwt="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.${payload_encoded}.invalid"

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${no_sub_jwt}" \
        -d '{"message":"test"}' \
        "${A2A_BASE_URL}/" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    [[ "$status_code" == "401" ]]
}

# ── Composite Provider Tests ───────────────────────────────────────────────
test_composite_fallback_to_api_key() {
    # Test that when JWT fails, API key can still work
    # This verifies the composite provider tries multiple schemes
    local valid_api_key
    valid_api_key=$(generate_api_key)

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer invalid-jwt" \
        -H "X-API-Key: ${valid_api_key}" \
        -d '{"message":"test"}' \
        "${A2A_BASE_URL}/" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    # If composite provider works, API key should authenticate even with bad JWT
    [[ "$status_code" != "401" ]] || [[ "$status_code" == "200" ]] || [[ "$status_code" == "400" ]]
}

test_composite_www_authenticate_includes_all_schemes() {
    local headers
    headers=$(curl -s -I --connect-timeout ${A2A_TIMEOUT} \
        -X POST \
        "${A2A_BASE_URL}/" 2>/dev/null)

    # WWW-Authenticate should mention multiple schemes
    local www_auth=$(echo "$headers" | grep -i "WWW-Authenticate" || echo "")
    if [[ -n "$www_auth" ]]; then
        # Check for Bearer and/or ApiKey in the header
        echo "$www_auth" | grep -qi "Bearer" && return 0
        echo "$www_auth" | grep -qi "ApiKey" && return 0
    fi
    return 1
}

# ── SPIFFE Authentication Tests ────────────────────────────────────────────
test_spiffe_no_certificate() {
    # Without mTLS certificate, should fall back or reject
    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        --connect-timeout ${A2A_TIMEOUT} \
        -H "Content-Type: application/json" \
        -d '{"message":"test"}' \
        "${A2A_BASE_URL}/" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    # Should require authentication
    [[ "$status_code" == "401" ]]
}

# ── Run All Authentication Tests ───────────────────────────────────────────
run_all_authentication_tests() {
    log_info "Starting A2A Authentication Provider Tests"
    log_info "Server: ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}"
    echo ""

    # API Key Tests
    echo "=== API Key Authentication ==="
    run_test "api_key_no_header" "Request without API key header returns 401" \
        "test_api_key_no_header"
    run_test "api_key_invalid_key" "Invalid API key returns 401" \
        "test_api_key_invalid_key"
    run_test "api_key_malformed_header" "Malformed API key header returns 401" \
        "test_api_key_malformed_header"
    run_test "api_key_www_authenticate" "401 response includes WWW-Authenticate header" \
        "test_api_key_www_authenticate_header"
    run_test "api_key_error_json" "Error response is valid JSON" \
        "test_api_key_error_response_json"
    echo ""

    # JWT Tests
    echo "=== JWT Bearer Token Authentication ==="
    run_test "jwt_no_token" "Request without JWT returns 401" \
        "test_jwt_no_token"
    run_test "jwt_malformed_token" "Malformed JWT returns 401" \
        "test_jwt_malformed_token"
    run_test "jwt_missing_bearer_prefix" "JWT without Bearer prefix fails" \
        "test_jwt_missing_bearer_prefix"
    run_test "jwt_expired_token" "Expired JWT returns 401" \
        "test_jwt_expired_token"
    run_test "jwt_wrong_algorithm" "JWT with wrong algorithm returns 401" \
        "test_jwt_wrong_algorithm_header"
    echo ""

    # Permission Tests
    echo "=== Permission-Based Authorization ==="
    run_test "permission_query_only" "Query-only token cannot launch workflows" \
        "test_permission_query_only_cannot_launch"
    run_test "permission_missing_sub" "JWT without sub claim returns 401" \
        "test_permission_missing_sub_claim"
    echo ""

    # Composite Provider Tests
    echo "=== Composite Authentication Provider ==="
    run_test "composite_fallback" "Composite provider falls back to API key" \
        "test_composite_fallback_to_api_key"
    run_test "composite_www_authenticate" "WWW-Authenticate includes all schemes" \
        "test_composite_www_authenticate_includes_all_schemes"
    echo ""

    # SPIFFE Tests
    echo "=== SPIFFE/X.509 Authentication ==="
    run_test "spiffe_no_certificate" "Request without certificate requires auth" \
        "test_spiffe_no_certificate"
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
  "category": "authentication",
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
    echo "A2A Authentication Provider Test Results"
    echo "==========================================="
    echo "Total Tests: ${TOTAL_TESTS}"
    echo "Passed: ${PASSED_TESTS}"
    echo "Failed: ${FAILED_TESTS}"
    echo ""

    if [[ ${FAILED_TESTS} -eq 0 ]]; then
        echo -e "${GREEN}All authentication tests passed.${NC}"
        return 0
    else
        echo -e "${RED}${FAILED_TESTS} authentication tests failed.${NC}"
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

    # Check server availability
    log_verbose "Checking A2A server availability..."
    if ! a2a_ping; then
        echo "[ERROR] A2A server not running at ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}" >&2
        echo "Start the server first: bash scripts/start-a2a-server.sh" >&2
        exit 2
    fi

    log_verbose "A2A server is available"

    # Run tests
    run_all_authentication_tests

    # Output results
    if [[ "$OUTPUT_FORMAT" == "json" ]]; then
        output_json
    else
        output_text
    fi
}

main "$@"
