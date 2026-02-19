#!/usr/bin/env bash
# ==========================================================================
# validate-a2a-compliance.sh — A2A Protocol Compliance Validator
#
# Validates YAWL A2A server implementation against Agent-to-Agent
# protocol standards (SPIFFE/JWT/API Key authentication).
#
# Usage:
#   bash scripts/validation/a2a/validate-a2a-compliance.sh    # Human-readable
#   bash scripts/validation/a2a/validate-a2a-compliance.sh --json  # JSON output
#   bash scripts/validation/a2a/validate-a2a-compliance.sh --verbose  # Debug
#
# Exit Codes:
#   0 - All checks pass
#   1 - A2A protocol issues found
#   2 - Server connection failed
#   3 - Invalid arguments
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
LIB_DIR="${SCRIPT_DIR}/lib"
TESTS_DIR="${SCRIPT_DIR}/tests"

# Configuration
OUTPUT_FORMAT="text"
VERBOSE=0
A2A_SERVER_HOST="${A2A_SERVER_HOST:-localhost}"
A2A_SERVER_PORT="${A2A_SERVER_PORT:-8080}"
A2A_SERVER_TIMEOUT="${A2A_SERVER_TIMEOUT:-30}"
ISSUES_FOUND=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

# ── Parse Arguments ──────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --json)     OUTPUT_FORMAT="json"; shift ;;
        --verbose|-v) VERBOSE=1; shift ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *)  echo "Unknown argument: $1" >&2; echo "Use --help for usage." >&2; exit 3 ;;
    esac
done

# ── Logging Functions ────────────────────────────────────────────────────
log_verbose() {
    [[ "$VERBOSE" -eq 1 ]] && echo "[VERBOSE] $*" >&2
}

log_info() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${CYAN}[INFO]${NC} $*"
}

log_success() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${GREEN}[OK]${NC} $*"
}

log_warning() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${RED}[ERROR]${NC} $*" >&2
}

log_section() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "\n${BOLD}${BLUE}=== $1 ===${NC}"
}

# ── A2A Client Utilities ─────────────────────────────────────────────────
source "${LIB_DIR}/a2a-common.sh" 2>/dev/null || {
    log_error "A2A client library not found: ${LIB_DIR}/a2a-common.sh"
    exit 2
}

# ── Test Suite Runners ───────────────────────────────────────────────────
run_agent_card_tests() {
    log_section "A2A Agent Card Discovery Tests"
    local tests=(
        "agent_card_endpoint"
        "no_auth_required"
        "content_type_json"
        "required_fields"
        "agent_name"
    )

    local passed=0
    for test in "${tests[@]}"; do
        if run_a2a_test "${test}"; then
            ((passed++)) || true
        else
            ((ISSUES_FOUND++)) || true
        fi
    done

    return $((passed < ${#tests[@]}))
}

run_skills_validation_tests() {
    log_section "A2A Skills Validation Tests"
    local skills=(
        "launch_workflow"
        "query_workflows"
        "manage_workitems"
        "cancel_workflow"
    )

    local passed=0
    for skill in "${skills[@]}"; do
        log_verbose "Testing skill: $skill"
        if run_a2a_skill_test "$skill"; then
            ((passed++)) || true
        else
            ((ISSUES_FOUND++)) || true
        fi
    done

    return $((passed < ${#skills[@]}))
}

run_authentication_tests() {
    log_section "A2A Authentication Tests"
    local tests=(
        "no_credentials_401"
        "valid_api_key"
        "invalid_api_key"
        "valid_jwt"
        "expired_jwt"
        "wrong_audience_jwt"
        "invalid_signature_jwt"
        "malformed_jwt"
        "jwt_missing_sub"
        "query_only_jwt_403"
        "full_permissions_api_key"
        "www_authenticate_header"
    )

    local passed=0
    for test in "${tests[@]}"; do
        if run_a2a_auth_test "${test}"; then
            ((passed++)) || true
        else
            ((ISSUES_FOUND++)) || true
        fi
    done

    return $((passed < ${#tests[@]}))
}

run_message_endpoint_tests() {
    log_section "A2A Message Endpoint Tests"
    local tests=(
        "post_without_auth"
        "post_with_auth"
        "get_task_without_auth"
        "get_task_with_auth"
        "cancel_without_auth"
        "cancel_with_auth"
    )

    local passed=0
    for test in "${tests[@]}"; do
        if run_a2a_endpoint_test "${test}"; then
            ((passed++)) || true
        else
            ((ISSUES_FOUND++)) || true
        fi
    done

    return $((passed < ${#tests[@]}))
}

run_error_response_tests() {
    log_section "A2A Error Response Tests"
    local tests=(
        "error_401_json"
        "error_403_json"
        "error_404"
        "error_405"
        "error_500"
        "malformed_request"
    )

    local passed=0
    for test in "${tests[@]}"; do
        if run_a2a_error_test "${test}"; then
            ((passed++)) || true
        else
            ((ISSUES_FOUND++)) || true
        fi
    done

    return $((passed < ${#tests[@]}))
}

# ── Main Test Runner ──────────────────────────────────────────────────────
run_all_tests() {
    local overall_result=0

    # Agent Card
    if ! run_agent_card_tests; then
        log_warning "Some agent card tests failed"
        overall_result=1
    fi

    # Skills
    if ! run_skills_validation_tests; then
        log_warning "Some skill validation tests failed"
        overall_result=1
    fi

    # Authentication
    if ! run_authentication_tests; then
        log_warning "Some authentication tests failed"
        overall_result=1
    fi

    # Message Endpoints
    if ! run_message_endpoint_tests; then
        log_warning "Some message endpoint tests failed"
        overall_result=1
    fi

    # Error Responses
    if ! run_error_response_tests; then
        log_warning "Some error response tests failed"
        overall_result=1
    fi

    return $overall_result
}

# ── Output Functions ───────────────────────────────────────────────────────
output_json() {
    cat << JSON_EOF
{
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "server": {
    "host": "${A2A_SERVER_HOST}",
    "port": ${A2A_SERVER_PORT}
  },
  "results": {
    "agent_card": $([[ "${ISSUES_FOUND}" -gt 0 ]] && echo "false" || echo "true"),
    "skills": $([[ "${ISSUES_FOUND}" -gt 5 ]] && echo "false" || echo "true"),
    "authentication": $([[ "${ISSUES_FOUND}" -gt 17 ]] && echo "false" || echo "true"),
    "endpoints": $([[ "${ISSUES_FOUND}" -gt 23 ]] && echo "false" || echo "true"),
    "error_responses": $([[ "${ISSUES_FOUND}" -gt 29 ]] && echo "false" || echo "true")
  },
  "issues_found": ${ISSUES_FOUND},
  "status": $([[ "${ISSUES_FOUND}" -eq 0 ]] && echo "\"PASS\"" || echo "\"FAIL\"")
}
JSON_EOF
}

output_text() {
    echo ""
    echo -e "${BOLD}YAWL A2A Compliance Report${NC}"
    echo "Generated: $(date -u +'%Y-%m-%d %H:%M:%S UTC')"
    echo "Server: ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}"
    echo ""

    if [[ $ISSUES_FOUND -eq 0 ]]; then
        echo -e "${GREEN}✓ All A2A compliance checks passed.${NC}"
        echo ""
        exit 0
    else
        echo -e "${RED}✗ ${ISSUES_FOUND} compliance issues found.${NC}"
        echo ""
        echo "Refer to the test output above for details."
        echo ""
        exit 1
    fi
}

# ── Main Execution ────────────────────────────────────────────────────────
# Start A2A server if not already running
log_verbose "Checking A2A server status..."
if ! a2a_ping; then
    log_warning "A2A server not running at ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}"
    echo ""
    echo "Please start the A2A server first:"
    echo "  bash scripts/start-a2a-server.sh"
    echo ""
    exit 2
fi

log_verbose "A2A server is running"

# Run all tests
if ! run_all_tests; then
    log_warning "Some tests failed, but continuing to report results"
fi

# Output results
if [[ "$OUTPUT_FORMAT" == "json" ]]; then
    output_json
else
    output_text
fi

exit $([[ $ISSUES_FOUND -eq 0 ]] && echo 0 || echo 1)