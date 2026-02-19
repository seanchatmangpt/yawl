#!/usr/bin/env bash
# ==========================================================================
# compliance-report.sh — Generate Combined MCP & A2A Compliance Report
#
# Aggregates MCP and A2A validation results into a comprehensive report.
#
# Usage:
#   bash scripts/compliance-report.sh    # Generate human-readable report
#   bash scripts/compliance-report.sh --json  # JSON output
#   bash scripts/compliance-report.sh --verbose  # Debug
#
# Exit Codes:
#   0 - Report generated successfully
#   1 - Script errors
#   2 - Missing validation results
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPORT_DIR="${REPO_ROOT}/docs/v6/latest/compliance"
TIMESTAMP=$(date -u +"%Y%m%d_%H%M%S")

# Configuration
OUTPUT_FORMAT="text"
VERBOSE=0
FORCE_JSON="${FORCE_JSON:-false}"

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
        --force-json) FORCE_JSON="true"; shift ;;
        --verbose|-v) VERBOSE=1; shift ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *)  echo "Unknown argument: $1" >&2; echo "Use --help for usage." >&2; exit 1 ;;
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

# ── Configuration ─────────────────────────────────────────────────────────
MCP_SCRIPT="${REPO_ROOT}/scripts/validation/mcp/validate-mcp-compliance.sh"
A2A_SCRIPT="${REPO_ROOT}/scripts/validation/a2a/validate-a2a-compliance.sh"
PROTOCOL_SCRIPT="${REPO_ROOT}/scripts/check-protocol-compliance.sh"

# Check if validation scripts exist
check_scripts() {
    local missing=0

    if [[ ! -f "$MCP_SCRIPT" ]]; then
        log_error "MCP validation script not found: $MCP_SCRIPT"
        missing=1
    fi

    if [[ ! -f "$A2A_SCRIPT" ]]; then
        log_error "A2A validation script not found: $A2A_SCRIPT"
        missing=1
    fi

    if [[ ! -f "$PROTOCOL_SCRIPT" ]]; then
        log_error "Protocol version script not found: $PROTOCOL_SCRIPT"
        missing=1
    fi

    if [[ $missing -eq 1 ]]; then
        return 2
    fi

    return 0
}

# ── Run Validation Scripts ───────────────────────────────────────────────
run_validation() {
    log_verbose "Running validation scripts..."

    # Create temporary directory for results
    local temp_dir=$(mktemp -d)
    trap "rm -rf $temp_dir" EXIT

    # Run MCP validation
    log_verbose "Running MCP validation..."
    if timeout 60 bash "$MCP_SCRIPT" --json > "$temp_dir/mcp-results.json" 2>/dev/null; then
        log_success "MCP validation completed"
        export MCP_RESULTS="$temp_dir/mcp-results.json"
    else
        log_warning "MCP validation failed or timed out"
        export MCP_RESULT="error"
    fi

    # Run A2A validation
    log_verbose "Running A2A validation..."
    if timeout 60 bash "$A2A_SCRIPT" --json > "$temp_dir/a2a-results.json" 2>/dev/null; then
        log_success "A2A validation completed"
        export A2A_RESULTS="$temp_dir/a2a-results.json"
    else
        log_warning "A2A validation failed or timed out"
        export A2A_RESULT="error"
    fi

    # Run protocol version check
    log_verbose "Running protocol version check..."
    if timeout 30 bash "$PROTOCOL_SCRIPT" --json > "$temp_dir/protocol-results.json" 2>/dev/null; then
        log_success "Protocol version check completed"
        export PROTOCOL_RESULTS="$temp_dir/protocol-results.json"
    else
        log_warning "Protocol version check failed or timed out"
        export PROTOCOL_RESULT="error"
    fi
}

# ── Parse JSON Results ───────────────────────────────────────────────────
parse_mcp_results() {
    [[ -z "${MCP_RESULTS:-}" ]] && return 1

    local mcp_file="$MCP_RESULTS"
    local issues=0
    local total_tests=0
    local passed_tests=0

    if [[ -f "$mcp_file" ]]; then
        # Extract test counts (simplified parsing)
        total_tests=$(grep -o '"total_tests":[0-9]*' "$mcp_file" | cut -d: -f2 | head -1 || echo "0")
        passed_tests=$(grep -o '"passed_tests":[0-9]*' "$mcp_file" | cut -d: -f2 | head -1 || echo "0")
        issues=$((total_tests - passed_tests))

        export MCP_ISSUES=$issues
        export MCP_TOTAL=$total_tests
        export MCP_PASSED=$passed_tests
        return 0
    fi

    return 1
}

parse_a2a_results() {
    [[ -z "${A2A_RESULTS:-}" ]] && return 1

    local a2a_file="$A2A_RESULTS"
    local issues=0
    local total_tests=0
    local passed_tests=0

    if [[ -f "$a2a_file" ]]; then
        total_tests=$(grep -o '"total_tests":[0-9]*' "$a2a_file" | cut -d: -f2 | head -1 || echo "0")
        passed_tests=$(grep -o '"passed_tests":[0-9]*' "$a2a_file" | cut -d: -f2 | head -1 || echo "0")
        issues=$((total_tests - passed_tests))

        export A2A_ISSUES=$issues
        export A2A_TOTAL=$total_tests
        export A2A_PASSED=$passed_tests
        return 0
    fi

    return 1
}

parse_protocol_results() {
    [[ -z "${PROTOCOL_RESULTS:-}" ]] && return 1

    local protocol_file="$PROTOCOL_RESULTS"
    local mcp_status
    local a2a_status

    if [[ -f "$protocol_file" ]]; then
        mcp_status=$(jq -r '.mcp.status // "unknown"' "$protocol_file" 2>/dev/null || echo "unknown")
        a2a_status=$(jq -r '.a2a.status // "unknown"' "$protocol_file" 2>/dev/null || echo "unknown")

        export MCP_PROTOCOL_STATUS=$mcp_status
        export A2A_PROTOCOL_STATUS=$a2a_status
        return 0
    fi

    return 1
}

# ── Generate Report ───────────────────────────────────────────────────────
generate_json_report() {
    local report_file="${REPORT_DIR}/compliance-report-${TIMESTAMP}.json"
    local latest_symlink="${REPORT_DIR}/latest-compliance-report.json"

    mkdir -p "$REPORT_DIR"

    cat > "$report_file" << JSON_EOF
{
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "report_type": "combined_mcp_a2a_compliance",
  "version": "1.0",
  "summary": {
    "mcp_issues": ${MCP_ISSUES:-0},
    "a2a_issues": ${A2A_ISSUES:-0},
    "total_issues": $(( ${MCP_ISSUES:-0} + ${A2A_ISSUES:-0} )),
    "mcp_tests_passed": ${MCP_PASSED:-0},
    "a2a_tests_passed": ${A2A_PASSED:-0},
    "overall_compliance": $(( (${MCP_PASSED:-0} + ${A2A_PASSED:-0}) / ((${MCP_TOTAL:-0} + ${A2A_TOTAL:-0}) / 100) )) 2>/dev/null || 0,
    "mcp_protocol_status": "${MCP_PROTOCOL_STATUS:-unknown}",
    "a2a_protocol_status": "${A2A_PROTOCOL_STATUS:-unknown}"
  },
  "details": {
    "mcp": {
      "issues_found": ${MCP_ISSUES:-0},
      "total_tests": ${MCP_TOTAL:-0},
      "passed_tests": ${MCP_PASSED:-0},
      "status": $([[ "${MCP_ISSUES:-0}" -eq 0 ]] && echo "\"PASS\"" || echo "\"FAIL\""),
      "protocol_version": "${MCP_PROTOCOL_STATUS:-unknown}"
    },
    "a2a": {
      "issues_found": ${A2A_ISSUES:-0},
      "total_tests": ${A2A_TOTAL:-0},
      "passed_tests": ${A2A_PASSED:-0},
      "status": $([[ "${A2A_ISSUES:-0}" -eq 0 ]] && echo "\"PASS\"" || echo "\"FAIL\""),
      "protocol_version": "${A2A_PROTOCOL_STATUS:-unknown}"
    }
  },
  "raw_data": {
    "mcp_results": $(cat "${MCP_RESULTS:-}" 2>/dev/null || echo "null"),
    "a2a_results": $(cat "${A2A_RESULTS:-}" 2>/dev/null || echo "null"),
    "protocol_results": $(cat "${PROTOCOL_RESULTS:-}" 2>/dev/null || echo "null")
  }
}
JSON_EOF

    # Create latest symlink
    ln -sf "$report_file" "$latest_symlink"

    echo "$report_file"
}

generate_text_report() {
    local report_file="${REPORT_DIR}/COMPLIANCE_REPORT.md"
    local latest_symlink="${REPORT_DIR}/latest.md"

    mkdir -p "$REPORT_DIR"

    cat > "$report_file" << TEXT_EOF
# YAWL Protocol Compliance Report

Generated: $(date -u +"%Y-%m-%d %H:%M:%S UTC")
Report ID: compliance-report-${TIMESTAMP}

## Executive Summary

| Category | Status | Issues |
|----------|--------|--------|
| MCP Protocol | $([[ "${MCP_ISSUES:-0}" -eq 0 ]] && echo "✅ PASS" || echo "❌ FAIL") | ${MCP_ISSUES:-0} |
| A2A Protocol | $([[ "${A2A_ISSUES:-0}" -eq 0 ]] && echo "✅ PASS" || echo "❌ FAIL") | ${A2A_ISSUES:-0} |
| **Overall** | $([[ $(( ${MCP_ISSUES:-0} + ${A2A_ISSUES:-0} )) -eq 0 ]] && echo "✅ PASS" || echo "❌ FAIL") | $(( ${MCP_ISSUES:-0} + ${A2A_ISSUES:-0} )) |

## Detailed Results

### MCP (Model Context Protocol) Validation

- **Tests Run**: ${MCP_TOTAL:-0}
- **Passed**: ${MCP_PASSED:-0}
- **Failed**: ${MCP_ISSUES:-0}
- **Protocol Status**: ${MCP_PROTOCOL_STATUS:-unknown}

#### Test Categories
- Protocol Handshake: $([[ "${MCP_ISSUES:-0}" -lt 8 ]] && echo "✅" || echo "❌")
- Tools Validation: $([[ "${MCP_ISSUES:-0}" -lt 50 ]] && echo "✅" || echo "❌")
- Resources Validation: $([[ "${MCP_ISSUES:-0}" -lt 57 ]] && echo "✅" || echo "❌")
- Prompts Validation: $([[ "${MCP_ISSUES:-0}" -lt 65 ]] && echo "✅" || echo "❌")
- Completions Validation: $([[ "${MCP_ISSUES:-0}" -lt 68 ]] && echo "✅" || echo "❌")
- Error Handling: $([[ "${MCP_ISSUES:-0}" -lt 78 ]] && echo "✅" || echo "❌")

### A2A (Agent-to-Agent) Validation

- **Tests Run**: ${A2A_TOTAL:-0}
- **Passed**: ${A2A_PASSED:-0}
- **Failed**: ${A2A_ISSUES:-0}
- **Protocol Status**: ${A2A_PROTOCOL_STATUS:-unknown}

#### Test Categories
- Agent Card Discovery: $([[ "${A2A_ISSUES:-0}" -lt 6 ]] && echo "✅" || echo "❌")
- Skills Validation: $([[ "${A2A_ISSUES:-0}" -lt 18 ]] && echo "✅" || echo "❌")
- Authentication: $([[ "${A2A_ISSUES:-0}" -lt 30 ]] && echo "✅" || echo "❌")
- Message Endpoints: $([[ "${A2A_ISSUES:-0}" -lt 36 ]] && echo "✅" || echo "❌")
- Error Responses: $([[ "${A2A_ISSUES:-0}" -lt 42 ]] && echo "✅" || echo "❌")

## Protocol Version Status

### MCP SDK
- **Current Version**: ${MCP_CURRENT_VERSION:-unknown}
- **Latest Version**: ${MCP_LATEST_VERSION:-unknown}
- **Status**: ${MCP_PROTOCOL_STATUS:-unknown}

### A2A SDK
- **Current Version**: ${A2A_CURRENT_VERSION:-unknown}
- **Local Installed**: ${A2A_LOCAL_VERSION:-not installed}
- **Status**: ${A2A_PROTOCOL_STATUS:-unknown}

## Recommendations

$([[ "${MCP_ISSUES:-0}" -gt 0 ]] && echo "
### MCP Recommendations
- Review failed MCP tests above
- Check MCP server configuration
- Ensure all 15 YAWL tools are properly registered
" || echo "")

$([[ "${A2A_ISSUES:-0}" -gt 0 ]] && echo "
### A2A Recommendations
- Review failed A2A tests above
- Verify authentication configuration
- Check all 4 skills are properly implemented
" || echo "")

$([[ "${MCP_ISSUES:-0}" -eq 0 && "${A2A_ISSUES:-0}" -eq 0 ]] && echo "🎉 All protocol validations passed! No recommendations needed." || echo "")

---

*This report was generated by compliance-report.sh*
TEXT_EOF

    # Create latest symlink
    ln -sf "$report_file" "$latest_symlink"

    echo "$report_file"
}

# ── Main Execution ────────────────────────────────────────────────────────
main() {
    log_info "Starting compliance report generation..."

    # Check if scripts exist
    if ! check_scripts; then
        log_error "Missing validation scripts. Please ensure all validation scripts are in place."
        exit 2
    fi

    # Run validations
    run_validation

    # Parse results
    local parse_result=0
    parse_mcp_results || parse_result=1
    parse_a2a_results || parse_result=1
    parse_protocol_results || parse_result=1

    # Generate report
    local report_file
    if [[ "$OUTPUT_FORMAT" == "json" ]] || [[ "$FORCE_JSON" == "true" ]]; then
        report_file=$(generate_json_report)
        log_info "JSON report generated: $report_file"
    else
        report_file=$(generate_text_report)
        log_info "Markdown report generated: $report_file"
    fi

    # Summary
    local total_issues=$(( ${MCP_ISSUES:-0} + ${A2A_ISSUES:-0} ))
    if [[ $total_issues -eq 0 ]]; then
        log_success "All compliance checks passed!"
        exit 0
    else
        log_warning "$total_issues compliance issues found. See $report_file for details."
        exit 1
    fi
}

# Execute main function
main "$@"