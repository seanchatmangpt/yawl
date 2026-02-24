#!/usr/bin/env bash
set -euo pipefail
# ==========================================================================
# run-e2e-suite.sh — Run E2E MCP/A2A Workflow Test Suite
#
# Orchestrates execution of all E2E workflow tests.
#
# Usage: bash scripts/test-e2e-mcp-a2a/run-e2e-suite.sh [--all] [--mcp] [--a2a] [--json] [--verbose]
# ==========================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/lib"
REPORT_DIR="${REPO_ROOT}/docs/v6/latest/e2e-reports"

# Configuration
TEST_MCP=0
TEST_A2A=0
TEST_CONCURRENT=0
TEST_JSON=0
VERBOSE=0
FORCE_CLEAN=0

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
        --all) TEST_MCP=1; TEST_A2A=1; TEST_CONCURRENT=1; shift ;;
        --mcp) TEST_MCP=1; shift ;;
        --a2a) TEST_A2A=1; shift ;;
        --json) TEST_JSON=1; shift ;;
        --verbose) VERBOSE=1; shift ;;
        --concurrent) TEST_CONCURRENT=1; shift ;;
        --force-clean) FORCE_CLEAN=1; shift ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --all             Run all tests (MCP + A2A)"
            echo "  --mcp             Run only MCP workflow tests"
            echo "  --a2a             Run only A2A workflow tests"
            echo "  --concurrent      Run tests in parallel where possible"
            echo "  --json            Output results in JSON format"
            echo "  --verbose         Enable verbose logging"
            echo "  --force-clean     Clean previous test results"
            echo "  -h, --help        Show this help message"
            exit 0 ;;
        *)  log_error "Unknown argument: $1"; exit 1 ;;
    esac
done

# Set defaults if no tests specified
if [[ $TEST_MCP -eq 0 && $TEST_A2A -eq 0 ]]; then
    TEST_MCP=1
    TEST_A2A=1
    TEST_CONCURRENT=1
fi

# ─── Logging Functions ───────────────────────────────────────────────────
log_verbose() {
    [[ "$VERBOSE" -eq 1 ]] && echo "[VERBOSE] $*" >&2
}

log_info() {
    echo -e "${CYAN}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[OK]${NC} $*"
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

log_section() {
    echo -e "\n${BOLD}${BLUE}=== $1 ===${NC}"
}

# ─── Environment Setup ───────────────────────────────────────────────────
setup_environment() {
    log_section "Environment Setup"

    # Create report directory
    mkdir -p "$REPORT_DIR"
    mkdir -p "${TEMP_DIR:-/tmp}"

    # Clean up previous results if requested
    if [[ "$FORCE_CLEAN" -eq 1 ]]; then
        log_verbose "Cleaning up previous test results..."
        rm -rf "${TEMP_DIR:-/tmp}/e2e-test-*" 2>/dev/null
        rm -f "${REPORT_DIR}/latest" 2>/dev/null
    fi

    # Check required servers
    local missing_servers=0

    if [[ $TEST_MCP -eq 1 ]] && ! nc -z localhost 9090 2>/dev/null; then
        log_error "MCP server not running on localhost:9090"
        missing_servers=1
    fi

    if [[ $TEST_A2A -eq 1 ]] && ! nc -z localhost 8081 2>/dev/null; then
        log_error "A2A server not running on localhost:8081"
        missing_servers=1
    fi

    if [[ $missing_servers -eq 1 ]]; then
        echo ""
        log_warning "Please ensure servers are running:"
        log_info "MCP Server:  bash scripts/start-mcp-server.sh"
        log_info "A2A Server:  bash scripts/start-a2a-server.sh"
        echo ""
        exit 1
    fi

    # Check Python for XML to JSON conversion
    if ! command -v python3 >/dev/null 2>&1; then
        log_warning "Python3 not found. XML to JSON conversion will be disabled."
    fi

    log_success "Environment setup complete"
}

# ─── Test Execution Functions ────────────────────────────────────────────
run_mcp_tests() {
    log_section "Running MCP Workflow Tests"
    local start_time=$(date +%s)
    local mcp_report="${REPORT_DIR}/mcp-workflow-$(date +%Y%m%d_%H%M%S).json"

    cd "${SCRIPT_DIR}"

    if [[ "$TEST_JSON" -eq 1 ]]; then
        bash test-mcp-workflow.sh --json > "$mcp_report" 2>&1
    else
        bash test-mcp-workflow.sh --verbose 2>&1 | tee "${mcp_report%.json}.txt"
    fi

    local exit_code=$?
    local duration=$(( $(date +%s) - start_time ))

    # Create summary
    cat > "${mcp_report%.json}_summary.json" << JSON_EOF
{
  "test_type": "mcp-workflow",
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "duration_seconds": $duration,
  "exit_code": $exit_code,
  "json_report": "$mcp_report",
  "text_report": "${mcp_report%.json}.txt"
}
JSON_EOF

    ln -sf "$mcp_report" "${REPORT_DIR}/latest-mcp.json"
    ln -sf "${mcp_report%.json}_summary.json" "${REPORT_DIR}/latest-mcp-summary.json"

    return $exit_code
}

run_a2a_tests() {
    log_section "Running A2A Workflow Tests"
    local start_time=$(date +%s)
    local a2a_report="${REPORT_DIR}/a2a-workflow-$(date +%Y%m%d_%H%M%S).json"

    cd "${SCRIPT_DIR}"

    if [[ "$TEST_JSON" -eq 1 ]]; then
        bash test-a2a-workflow.sh --json > "$a2a_report" 2>&1
    else
        bash test-a2a-workflow.sh --verbose 2>&1 | tee "${a2a_report%.json}.txt"
    fi

    local exit_code=$?
    local duration=$(( $(date +%s) - start_time ))

    # Create summary
    cat > "${a2a_report%.json}_summary.json" << JSON_EOF
{
  "test_type": "a2a-workflow",
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "duration_seconds": $duration,
  "exit_code": $exit_code,
  "json_report": "$a2a_report",
  "text_report": "${a2a_report%.json}.txt"
}
JSON_EOF

    ln -sf "$a2a_report" "${REPORT_DIR}/latest-a2a.json"
    ln -sf "${a2a_report%.json}_summary.json" "${REPORT_DIR}/latest-a2a-summary.json"

    return $exit_code
}

run_concurrent_tests() {
    log_section "Running Tests Concurrently"

    # Run both tests in background
    local mcp_result
    local a2a_result

    if [[ $TEST_MCP -eq 1 ]]; then
        run_mcp_tests &
        mcp_result=$!
    fi

    if [[ $TEST_A2A -eq 1 ]]; then
        run_a2a_tests &
        a2a_result=$!
    fi

    # Wait for both to complete
    local overall_result=0

    if [[ -n "${mcp_result:-}" ]]; then
        wait $mcp_result
        local mcp_exit=$?
        if [[ $mcp_exit -ne 0 ]]; then
            log_warning "MCP tests failed with exit code $mcp_exit"
            overall_result=1
        fi
    fi

    if [[ -n "${a2a_result:-}" ]]; then
        wait $a2a_result
        local a2a_exit=$?
        if [[ $a2a_exit -ne 0 ]]; then
            log_warning "A2A tests failed with exit code $a2a_exit"
            overall_result=1
        fi
    fi

    return $overall_result
}

# ─── Report Generation ──────────────────────────────────────────────────
generate_combined_report() {
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local combined_report="${REPORT_DIR}/combined-e2e-${timestamp}.json"

    log_section "Generating Combined Report"

    # Find latest test reports
    local latest_mcp="${REPORT_DIR}/latest-mcp-summary.json"
    local latest_a2a="${REPORT_DIR}/latest-a2a-summary.json"

    # Create combined JSON report
    cat > "$combined_report" << JSON_EOF
{
  "test_type": "combined-e2e",
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "summary": {
    "mcp_tests": $(cat "$latest_mcp" 2>/dev/null || echo "{}"),
    "a2a_tests": $(cat "$latest_a2a" 2>/dev/null || echo "{}"),
    "overall_status": $(([[ $? -eq 0 ]] && echo '"PASS"' || echo '"FAIL"'))
  },
  "raw_data": {
    "mcp_results": $(cat "${REPORT_DIR}/latest-mcp.json" 2>/dev/null || echo "null"),
    "a2a_results": $(cat "${REPORT_DIR}/latest-a2a.json" 2>/dev/null || echo "null")
  }
}
JSON_EOF

    # Create symlink to latest
    ln -sf "$combined_report" "${REPORT_DIR}/latest-combined.json"

    # Generate text summary
    generate_text_summary "$combined_report"
}

generate_text_summary() {
    local report_file="$1"
    local text_summary="${REPORT_DIR}/E2E_TEST_SUMMARY.md"

    log_verbose "Generating text summary: $text_summary"

    # Extract values from JSON
    local mcp_status=$(jq -r '.summary.mcp_tests.exit_code // "N/A"' "$report_file" 2>/dev/null || echo "N/A")
    local a2a_status=$(jq -r '.summary.a2a_tests.exit_code // "N/A"' "$report_file" 2>/dev/null || echo "N/A")

    cat > "$text_summary" << TEXT_EOF
# E2E Workflow Test Summary

Generated: $(date -u +'%Y-%m-%d %H:%M:%S UTC')
Test ID: combined-e2e-$(date +%Y%m%d_%H%M%S)

## Test Results

| Test Suite | Status | Duration |
|------------|--------|----------|
| MCP Workflow | $([[ "$mcp_status" == "0" ]] && echo "✅ PASS" || echo "❌ FAIL") | $(jq -r '.summary.mcp_tests.duration_seconds // "N/A"' "$report_file" 2>/dev/null || echo "N/A")s |
| A2A Workflow | $([[ "$a2a_status" == "0" ]] && echo "✅ PASS" || echo "❌ FAIL") | $(jq -r '.summary.a2a_tests.duration_seconds // "N/A"' "$report_file" 2>/dev/null || echo "N/A")s |

## Overall Status

$([[ "$mcp_status" == "0" && "$a2a_status" == "0" ]] && echo "🎉 All tests passed!" || echo "⚠️ Some tests failed")

## Detailed Reports

- MCP Results: [latest-mcp.json](${REPORT_DIR}/latest-mcp.json)
- A2A Results: [latest-a2a.json](${REPORT_DIR}/latest-a2a.json)
- Combined Report: [combined-e2e-$(date +%Y%m%d_%H%M%S).json]($combined_report)

---
*Generated by run-e2e-suite.sh*
TEXT_EOF

    ln -sf "$text_summary" "${REPORT_DIR}/latest.md"
}

# ─── Main Execution ─────────────────────────────────────────────────────
main() {
    log_info "Starting E2E Test Suite"

    # Setup environment
    setup_environment

    # Run tests
    local overall_result=0

    if [[ "$TEST_CONCURRENT" -eq 1 ]]; then
        if ! run_concurrent_tests; then
            overall_result=1
        fi
    else
        if [[ $TEST_MCP -eq 1 ]]; then
            if ! run_mcp_tests; then
                overall_result=1
            fi
        fi

        if [[ $TEST_A2A -eq 1 ]]; then
            if ! run_a2a_tests; then
                overall_result=1
            fi
        fi
    fi

    # Generate reports if tests were run
    if [[ $TEST_MCP -eq 1 || $TEST_A2A -eq 1 ]]; then
        generate_combined_report
    fi

    # Final result
    log_section "Test Suite Complete"
    if [[ $overall_result -eq 0 ]]; then
        log_success "All tests passed!"
        log_info "Reports available in: $REPORT_DIR"
        exit 0
    else
        log_error "Some tests failed. Check reports for details."
        exit 1
    fi
}

# Execute main function
main "$@"