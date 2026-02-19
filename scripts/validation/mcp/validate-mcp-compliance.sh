#!/usr/bin/env bash
# ==========================================================================
# validate-mcp-compliance.sh — MCP Protocol Compliance Validator
#
# Validates YAWL MCP server implementation against Model Context Protocol
# standards (SDK 0.17.2, protocol 2024-11-05).
#
# Usage:
#   bash scripts/validation/mcp/validate-mcp-compliance.sh    # Human-readable
#   bash scripts/validation/mcp/validate-mcp-compliance.sh --json  # JSON output
#   bash scripts/validation/mcp/validate-mcp-compliance.sh --verbose  # Debug
#
# Exit Codes:
#   0 - All checks pass
#   1 - MCP protocol issues found
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
MCP_SERVER_HOST="${MCP_SERVER_HOST:-localhost}"
MCP_SERVER_PORT="${MCP_SERVER_PORT:-9090}"
MCP_SERVER_TIMEOUT="${MCP_SERVER_TIMEOUT:-30}"
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

# ── MCP Client Utilities ─────────────────────────────────────────────────
source "${LIB_DIR}/mcp-client.sh" 2>/dev/null || {
    log_error "MCP client library not found: ${LIB_DIR}/mcp-client.sh"
    exit 2
}

# ── Test Suite Runners ───────────────────────────────────────────────────
run_protocol_handshake_tests() {
    log_section "MCP Protocol Handshake Tests"
    local tests=(
        "initialize_request"
        "protocol_version_check"
        "server_info_check"
        "capabilities_check"
        "tools_capability"
        "resources_capability"
        "prompts_capability"
    )

    local passed=0
    for test in "${tests[@]}"; do
        if run_mcp_test "${test}"; then
            ((passed++)) || true
        else
            ((ISSUES_FOUND++)) || true
        fi
    done

    return $((passed < ${#tests[@]}))
}

run_tools_validation_tests() {
    log_section "MCP Tools Validation Tests"
    local tools=(
        "yawl_launch_case"
        "yawl_get_case_status"
        "yawl_cancel_case"
        "yawl_list_specifications"
        "yawl_get_specification"
        "yawl_upload_specification"
        "yawl_get_work_items"
        "yawl_get_work_items_for_case"
        "yawl_checkout_work_item"
        "yawl_checkin_work_item"
        "yawl_get_running_cases"
        "yawl_get_case_data"
        "yawl_suspend_case"
        "yawl_resume_case"
        "yawl_skip_work_item"
    )

    local passed=0
    for tool in "${tools[@]}"; do
        log_verbose "Testing tool: $tool"
        if run_mcp_tool_test "$tool"; then
            ((passed++)) || true
        else
            ((ISSUES_FOUND++)) || true
        fi
    done

    return $((passed < ${#tools[@]}))
}

run_resources_validation_tests() {
    log_section "MCP Resources Validation Tests"
    local tests=(
        "resources_list"
        "specifications_resource"
        "cases_resource"
        "workitems_resource"
        "templates_declaration"
        "template_expansion"
    )

    local passed=0
    for test in "${tests[@]}"; do
        if run_mcp_resource_test "${test}"; then
            ((passed++)) || true
        else
            ((ISSUES_FOUND++)) || true
        fi
    done

    return $((passed < ${#tests[@]}))
}

run_prompts_validation_tests() {
    log_section "MCP Prompts Validation Tests"
    local prompts=(
        "workflow_analysis"
        "task_completion_guide"
        "case_troubleshooting"
        "workflow_design_review"
    )

    local passed=0
    for prompt in "${prompts[@]}"; do
        log_verbose "Testing prompt: $prompt"
        if run_mcp_prompt_test "$prompt"; then
            ((passed++)) || true
        else
            ((ISSUES_FOUND++)) || true
        fi
    done

    return $((passed < ${#prompts[@]}))
}

run_completions_validation_tests() {
    log_section "MCP Completions Validation Tests"
    local tests=(
        "workflow_analysis_completion"
        "task_completion_guide_completion"
        "cases_resource_completion"
    )

    local passed=0
    for test in "${tests[@]}"; do
        if run_mcp_completion_test "${test}"; then
            ((passed++)) || true
        else
            ((ISSUES_FOUND++)) || true
        fi
    done

    return $((passed < ${#tests[@]}))
}

run_error_handling_tests() {
    log_section "MCP Error Handling Tests"
    local tests=(
        "invalid_json_error"
        "invalid_method_error"
        "missing_params_error"
        "invalid_resource_error"
        "nonexistent_tool_error"
        "json_rpc_error_code"
        "error_response_format"
        "connection_error"
        "timeout_error"
        "malformed_request_error"
    )

    local passed=0
    for test in "${tests[@]}"; do
        if run_mcp_error_test "${test}"; then
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

    # Protocol Handshake
    if ! run_protocol_handshake_tests; then
        log_warning "Some protocol handshake tests failed"
        overall_result=1
    fi

    # Tools
    if ! run_tools_validation_tests; then
        log_warning "Some tool validation tests failed"
        overall_result=1
    fi

    # Resources
    if ! run_resources_validation_tests; then
        log_warning "Some resource validation tests failed"
        overall_result=1
    fi

    # Prompts
    if ! run_prompts_validation_tests; then
        log_warning "Some prompt validation tests failed"
        overall_result=1
    fi

    # Completions
    if ! run_completions_validation_tests; then
        log_warning "Some completion validation tests failed"
        overall_result=1
    fi

    # Error Handling
    if ! run_error_handling_tests; then
        log_warning "Some error handling tests failed"
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
    "host": "${MCP_SERVER_HOST}",
    "port": ${MCP_SERVER_PORT}
  },
  "results": {
    "protocol_handshake": $([[ "${ISSUES_FOUND}" -gt 0 ]] && echo "false" || echo "true"),
    "tools": $([[ "${ISSUES_FOUND}" -gt 5 ]] && echo "false" || echo "true"),
    "resources": $([[ "${ISSUES_FOUND}" -gt 11 ]] && echo "false" || echo "true"),
    "prompts": $([[ "${ISSUES_FOUND}" -gt 19 ]] && echo "false" || echo "true"),
    "completions": $([[ "${ISSUES_FOUND}" -gt 22 ]] && echo "false" || echo "true"),
    "error_handling": $([[ "${ISSUES_FOUND}" -gt 32 ]] && echo "false" || echo "true")
  },
  "issues_found": ${ISSUES_FOUND},
  "status": $([[ "${ISSUES_FOUND}" -eq 0 ]] && echo "\"PASS\"" || echo "\"FAIL\"")
}
JSON_EOF
}

output_text() {
    echo ""
    echo -e "${BOLD}YAWL MCP Compliance Report${NC}"
    echo "Generated: $(date -u +'%Y-%m-%d %H:%M:%S UTC')"
    echo "Server: ${MCP_SERVER_HOST}:${MCP_SERVER_PORT}"
    echo ""

    if [[ $ISSUES_FOUND -eq 0 ]]; then
        echo -e "${GREEN}✓ All MCP compliance checks passed.${NC}"
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
# Start MCP server if not already running
log_verbose "Checking MCP server status..."
if ! mcp_ping; then
    log_warning "MCP server not running at ${MCP_SERVER_HOST}:${MCP_SERVER_PORT}"
    echo ""
    echo "Please start the MCP server first:"
    echo "  bash scripts/start-mcp-server.sh"
    echo ""
    exit 2
fi

log_verbose "MCP server is running"

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