#!/usr/bin/env bash
# HyperStandardsValidator Plugin Adapter — Autonomous H-Guards Validation
#
# Purpose:
#   Wraps HyperStandardsValidator Java plugin to integrate with autonomous framework
#   - Accepts error trigger from analyze-errors.sh or decision-engine.sh
#   - Invokes HyperStandardsValidator on generated/source code
#   - Produces standardized error receipt JSON
#   - Routes violations to appropriate agents for remediation
#
# Input:
#   - Trigger JSON with error type (H_TODO, H_MOCK, etc.)
#   - Generated code path (from ggen or current src/)
#   - Context from decision-engine.sh
#
# Output:
#   - Guard receipt JSON (.claude/receipts/hyperstandards-receipt.json)
#   - Error analysis for remediation routing
#   - Exit code: 0 (GREEN), 1 (TRANSIENT), 2 (RED)
#
# Integration Points:
#   - Called by: decision-engine.sh (when H violations detected)
#   - Produces input for: remediate-violations.sh
#   - Publishes to: .claude/receipts/ for audit trail
#
# Author: Plugin Adaptation Framework
# Date: 2026-03-04

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# INITIALIZATION
# ──────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RECEIPTS_DIR="${PROJECT_ROOT}/.claude/receipts"
ADAPTERS_DIR="${SCRIPT_DIR}"

# Output paths
HYPERSTANDARDS_RECEIPT="${RECEIPTS_DIR}/hyperstandards-receipt.json"
GUARD_VIOLATIONS_FILE="/tmp/guard-violations-$$.json"

# Configuration
EMIT_DIR="${EMIT_DIR:-${PROJECT_ROOT}/yawl-engine/emit}"
VALIDATE_CLASS="org.yawlfoundation.yawl.ggen.validation.HyperStandardsValidator"

# Colors for output
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

mkdir -p "${RECEIPTS_DIR}"

# ──────────────────────────────────────────────────────────────────────────────
# LOGGING FUNCTIONS
# ──────────────────────────────────────────────────────────────────────────────

log_info() {
    echo -e "${BLUE}[validator-adapter]${NC} $*" >&2
}

log_warn() {
    echo -e "${YELLOW}[validator-adapter]${NC} $*" >&2
}

log_error() {
    echo -e "${RED}[validator-adapter]${NC} $*" >&2
}

log_success() {
    echo -e "${GREEN}[validator-adapter]${NC} $*" >&2
}

# Cleanup temp files on exit
cleanup() {
    rm -f "${GUARD_VIOLATIONS_FILE}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# ──────────────────────────────────────────────────────────────────────────────
# VALIDATION FUNCTIONS
# ──────────────────────────────────────────────────────────────────────────────

# Check if emit directory exists and has Java files
validate_emit_dir() {
    if [[ ! -d "${EMIT_DIR}" ]]; then
        log_warn "Emit directory not found: ${EMIT_DIR}"
        log_info "Skipping validation (no generated code to validate)"
        return 1
    fi

    if ! find "${EMIT_DIR}" -name "*.java" -type f | head -1 &>/dev/null; then
        log_info "No Java files in emit directory"
        return 1
    fi

    log_info "Found generated Java files in ${EMIT_DIR}"
    return 0
}

# Check if Maven is available
check_maven() {
    if ! command -v mvn &>/dev/null; then
        log_error "Maven not found. Cannot run HyperStandardsValidator."
        return 1
    fi
    return 0
}

# ──────────────────────────────────────────────────────────────────────────────
# VALIDATOR INVOCATION
# ──────────────────────────────────────────────────────────────────────────────

# Invoke HyperStandardsValidator and capture output
run_hyperstandards_validator() {
    log_info "Invoking HyperStandardsValidator on ${EMIT_DIR}..."

    local validator_output="/tmp/validator-output-$$.txt"
    local exit_code=0

    # Run validator and capture both stdout and stderr
    if mvn -q exec:java \
        -Dexec.mainClass="${VALIDATE_CLASS}" \
        -Dexec.args="${EMIT_DIR}" \
        -f "${PROJECT_ROOT}/pom.xml" \
        >"${validator_output}" 2>&1 || {
        exit_code=$?
        [[ ${exit_code} -ne 0 ]] && [[ ${exit_code} -ne 2 ]]
    }; then
        if [[ ${exit_code} -eq 1 ]]; then
            log_error "Validator failed with transient error"
            return 1
        fi
    fi

    cat "${validator_output}"
    rm -f "${validator_output}"
    return ${exit_code}
}

# Parse validator output into JSON receipt
parse_validator_output() {
    local validator_output="$1"
    local violation_count=0
    local violations_json="[]"

    # Count violations in output
    violation_count=$(echo "${validator_output}" | grep -c "H_TODO\|H_MOCK\|H_STUB\|H_EMPTY\|H_FALLBACK\|H_LIE\|H_SILENT" || true)

    # If violations found, parse them
    if [[ ${violation_count} -gt 0 ]]; then
        log_info "Found ${violation_count} guard violations"

        # Parse violations into JSON array
        violations_json=$(echo "${validator_output}" | jq -R '
            select(. | test("H_TODO|H_MOCK|H_STUB|H_EMPTY|H_FALLBACK|H_LIE|H_SILENT")) |
            . as $line |
            {
                pattern: ($line | match("[A-Z_]+") | .string),
                line: ($line | match("[0-9]+") | .string // "0"),
                content: $line,
                severity: "FAIL"
            }
        ' | jq -s '.')
    fi

    echo "${violations_json}"
}

# Create guard receipt JSON
create_receipt() {
    local violations_json="$1"
    local violation_count=$(echo "${violations_json}" | jq 'length')
    local status="GREEN"
    local error_message=""

    if [[ ${violation_count} -gt 0 ]]; then
        status="RED"
        error_message="Found ${violation_count} guard violations. Routing to remediation."
    fi

    local receipt=$(cat <<EOF
{
  "phase": "H_GUARDS",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "plugin": "HyperStandardsValidator",
  "emit_dir": "${EMIT_DIR}",
  "violations_count": ${violation_count},
  "violations": ${violations_json},
  "status": "${status}",
  "error_message": "${error_message}",
  "exit_code": $([ "${status}" = "GREEN" ] && echo "0" || echo "2"),
  "recommendations": [
    "For H_TODO: Remove comment or implement the functionality",
    "For H_MOCK: Delete mock class or provide real implementation",
    "For H_STUB: Replace empty return with throw UnsupportedOperationException",
    "For H_EMPTY: Implement method body or throw exception",
    "For H_FALLBACK: Rethrow exception instead of returning fake data",
    "For H_LIE: Update code to match documentation",
    "For H_SILENT: Throw exception instead of logging"
  ],
  "next_step": "Route violations to $([ "${status}" = "RED" ] && echo "remediation agents" || echo "none (clean)")"
}
EOF
)

    echo "${receipt}"
}

# ──────────────────────────────────────────────────────────────────────────────
# MAIN FLOW
# ──────────────────────────────────────────────────────────────────────────────

main() {
    log_info "Starting HyperStandardsValidator plugin adapter"
    log_info "Configuration:"
    log_info "  - Project root: ${PROJECT_ROOT}"
    log_info "  - Emit dir: ${EMIT_DIR}"
    log_info "  - Validator class: ${VALIDATE_CLASS}"

    # Validate environment
    if ! validate_emit_dir; then
        log_warn "Emit directory validation failed, creating minimal receipt"
        local receipt=$(create_receipt "[]")
        echo "${receipt}" > "${HYPERSTANDARDS_RECEIPT}"
        log_info "Receipt written to: ${HYPERSTANDARDS_RECEIPT}"
        exit 0
    fi

    if ! check_maven; then
        log_error "Maven validation failed"
        exit 1
    fi

    # Run validator
    local validator_output=""
    if ! validator_output=$(run_hyperstandards_validator); then
        log_error "Validator execution failed"
        exit 1
    fi

    log_info "Validator execution completed"

    # Parse output
    local violations_json=$(parse_validator_output "${validator_output}")
    log_info "Parsed $(echo "${violations_json}" | jq 'length') violations"

    # Create receipt
    local receipt=$(create_receipt "${violations_json}")

    # Write receipt
    echo "${receipt}" > "${HYPERSTANDARDS_RECEIPT}"
    log_success "Receipt written to: ${HYPERSTANDARDS_RECEIPT}"

    # Extract exit code from receipt
    local exit_code=$(echo "${receipt}" | jq -r '.exit_code')

    # Log summary
    log_info "Summary:"
    echo "${receipt}" | jq '{
        status: .status,
        violations: .violations_count,
        phase: .phase,
        exit_code: .exit_code
    }' | sed 's/^/  /'

    exit ${exit_code}
}

# Run main function
main "$@"
