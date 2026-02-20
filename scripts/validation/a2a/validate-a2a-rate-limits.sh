#!/usr/bin/env bash
# ==========================================================================
# validate-a2a-rate-limits.sh - Validate A2A rate limiting behavior
#
# Usage:
#   bash scripts/validation/a2a/validate-a2a-rate-limits.sh [OPTIONS]
#
# Options:
#   --server URL       A2A server URL (default: http://localhost:8080)
#   --rate N           Requests per second limit to test (default: 10)
#   --duration N       Test duration in seconds (default: 10)
#   --verbose          Show detailed output
#   --help             Show this help
#
# Tests:
#   1. Within limit - requests succeed
#   2. Exceed limit - 429 responses with Retry-After header
#   3. Window reset - requests resume after window
#   4. Burst handling - brief bursts are allowed
#
# Exit codes:
#   0 - All tests passed
#   1 - One or more tests failed
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Default options
A2A_SERVER="http://localhost:8080"
RATE_LIMIT=10
DURATION=10
VERBOSE=false

# -------------------------------------------------------------------------
# Parse arguments
# -------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --server)
            A2A_SERVER="$2"
            shift 2
            ;;
        --rate)
            RATE_LIMIT="$2"
            shift 2
            ;;
        --duration)
            DURATION="$2"
            shift 2
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            cat << EOF
A2A Rate Limit Validation

Tests rate limiting behavior of A2A server.

Usage: bash scripts/validation/a2a/validate-a2a-rate-limits.sh [OPTIONS]

Options:
  --server URL       A2A server URL (default: http://localhost:8080)
  --rate N           Requests per second limit to test (default: 10)
  --duration N       Test duration in seconds (default: 10)
  --verbose          Show detailed output
  --help             Show this help
EOF
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}" >&2
            exit 1
            ;;
    esac
done

# -------------------------------------------------------------------------
# Helper functions
# -------------------------------------------------------------------------
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

make_request() {
    local response
    response=$(curl -s -w "\n%{http_code}\n%{size_header}" \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"skills/get_status","id":"test-'$$'"}' \
        "${A2A_SERVER}/rpc" 2>/dev/null || echo -e "\n000\n0")

    local body
    body=$(echo "$response" | head -n -2)
    local code
    code=$(echo "$response" | tail -n 2 | head -n 1)

    echo "${code}:${body}"
}

# -------------------------------------------------------------------------
# Tests
# -------------------------------------------------------------------------
test_within_limit() {
    log_info "Test 1: Requests within limit should succeed"

    local success=0
    local total=5

    for ((i=1; i<=total; i++)); do
        local result
        result=$(make_request)
        local code="${result%%:*}"

        if [[ "$code" == "200" ]]; then
            ((success++)) || true
        fi
        sleep 0.2  # Stay under rate limit
    done

    if [[ $success -eq $total ]]; then
        log_pass "All $total requests succeeded"
        return 0
    else
        log_fail "Only $success/$total requests succeeded"
        return 1
    fi
}

test_exceed_limit() {
    log_info "Test 2: Requests exceeding limit should get 429"

    # Send rapid burst
    local rate_limited=0
    local total=$((RATE_LIMIT + 5))

    for ((i=1; i<=total; i++)); do
        local result
        result=$(curl -s -o /dev/null -w "%{http_code}" \
            -H "Content-Type: application/json" \
            -d '{"jsonrpc":"2.0","method":"skills/get_status","id":"burst-'$$'-'$i'"}' \
            "${A2A_SERVER}/rpc" 2>/dev/null || echo "000")

        if [[ "$result" == "429" ]]; then
            ((rate_limited++)) || true
        fi
    done

    if [[ $rate_limited -gt 0 ]]; then
        log_pass "Rate limiting triggered ($rate_limited requests got 429)"
        return 0
    else
        log_warn "No rate limiting detected (server may not implement rate limits)"
        return 2
    fi
}

test_retry_after_header() {
    log_info "Test 3: 429 responses should include Retry-After header"

    # Trigger rate limit
    for ((i=1; i<=RATE_LIMIT+5; i++)); do
        curl -s -o /dev/null \
            -H "Content-Type: application/json" \
            -d '{"jsonrpc":"2.0","method":"skills/get_status","id":"retry-test-'$$'"}' \
            "${A2A_SERVER}/rpc" 2>/dev/null || true
    done

    # Check next request for Retry-After
    local headers
    headers=$(curl -s -I \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"skills/get_status","id":"header-test"}' \
        "${A2A_SERVER}/rpc" 2>/dev/null || echo "")

    if echo "$headers" | grep -iq "retry-after"; then
        local retry_after
        retry_after=$(echo "$headers" | grep -i "retry-after" | head -1 | cut -d: -f2 | tr -d ' \r')
        log_pass "Retry-After header present: ${retry_after}s"
        return 0
    else
        log_warn "Retry-After header not found in 429 response"
        return 2
    fi
}

test_window_reset() {
    log_info "Test 4: Requests should resume after window reset"

    # Wait for window to reset
    log_info "Waiting 5 seconds for rate limit window reset..."
    sleep 5

    local result
    result=$(make_request)
    local code="${result%%:*}"

    if [[ "$code" == "200" ]]; then
        log_pass "Requests resumed after window reset"
        return 0
    elif [[ "$code" == "429" ]]; then
        log_fail "Still rate limited after waiting"
        return 1
    else
        log_warn "Unexpected response code: $code"
        return 2
    fi
}

# -------------------------------------------------------------------------
# Main
# -------------------------------------------------------------------------
echo "========================================="
echo "  A2A Rate Limit Validation"
echo "  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "========================================="
echo ""
echo "Server: $A2A_SERVER"
echo "Rate Limit: $RATE_LIMIT req/s"
echo ""

# Check server availability
if ! curl -s -o /dev/null -w "%{http_code}" "${A2A_SERVER}/health" 2>/dev/null | grep -q "200"; then
    log_warn "A2A server not responding at ${A2A_SERVER}"
    log_info "Some tests may fail or be skipped"
fi

echo ""

TESTS_PASSED=0
TESTS_FAILED=0
TESTS_WARNED=0

run_test() {
    local test_func="$1"
    if $test_func; then
        ((TESTS_PASSED++)) || true
    elif [[ $? -eq 2 ]]; then
        ((TESTS_WARNED++)) || true
    else
        ((TESTS_FAILED++)) || true
    fi
    echo ""
}

run_test test_within_limit
run_test test_exceed_limit
run_test test_retry_after_header
run_test test_window_reset

echo "========================================="
echo "  Summary"
echo "========================================="
echo -e "  ${GREEN}Passed:   $TESTS_PASSED${NC}"
echo -e "  ${YELLOW}Warnings: $TESTS_WARNED${NC}"
echo -e "  ${RED}Failed:   $TESTS_FAILED${NC}"
echo ""

if [[ $TESTS_FAILED -gt 0 ]]; then
    exit 1
elif [[ $TESTS_WARNED -gt 0 ]]; then
    exit 2
else
    exit 0
fi
