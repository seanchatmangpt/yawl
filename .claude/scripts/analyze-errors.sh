#!/bin/bash
# Analyze-Errors Script — Autonomous Error Analysis & Classification
#
# Purpose:
#   Parse build logs, test output, and validation receipts to extract error details,
#   categorize by type (compilation, test, guard, invariant), extract root causes,
#   and emit structured error JSON for decision-engine consumption.
#
# Input Sources:
#   1. Maven compile logs (/tmp/maven-*.log)
#   2. JUnit test output (Maven Surefire)
#   3. Guard validation receipts (.claude/receipts/h-guards-receipt.json)
#   4. Invariant validation receipts (.claude/receipts/q-invariants-receipt.json)
#   5. Raw dx.sh output
#
# Output:
#   .claude/receipts/error-analysis-receipt.json with error categories, patterns, root causes
#
# Exit Codes:
#   0 = Analysis complete, no errors found (or errors analyzed successfully)
#   1 = Transient error (log parsing, file IO)
#   2 = Fatal error (invalid input, internal error)
#
# Author: Autonomous Decision System
# Version: 1.0

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RECEIPTS_DIR="${PROJECT_ROOT}/.claude/receipts"
RULES_FILE="${PROJECT_ROOT}/.claude/rules/decisions.toml"
ANALYSIS_RECEIPT="${RECEIPTS_DIR}/error-analysis-receipt.json"
TEMP_ERRORS="/tmp/yawl-errors-$$.json"

# Colors for output
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

mkdir -p "${RECEIPTS_DIR}"

# ──────────────────────────────────────────────────────────────────────────────
# HELPER FUNCTIONS
# ──────────────────────────────────────────────────────────────────────────────

log_info() {
    echo -e "${BLUE}[analyze-errors]${NC} $*"
}

log_error() {
    echo -e "${RED}[analyze-errors]${NC} $*" >&2
}

log_success() {
    echo -e "${GREEN}[analyze-errors]${NC} $*"
}

# Extract TOML value (simple parser for error patterns)
get_toml_value() {
    local file="$1"
    local section="$2"
    local key="$3"

    grep -A 50 "^\[${section}\]" "${file}" 2>/dev/null | \
        grep "^${key}\s*=" | \
        head -1 | \
        sed 's/.*=\s*"\(.*\)".*/\1/'
}

# Create error JSON object
create_error() {
    local error_type="$1"
    local severity="$2"
    local file="$3"
    local line="$4"
    local message="$5"
    local root_cause="$6"

    cat <<EOF
{
  "error_type": "${error_type}",
  "severity": "${severity}",
  "file": "${file}",
  "line": ${line:-0},
  "message": "$(echo "${message}" | sed 's/"/\\"/g')",
  "root_cause": "${root_cause}",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF
}

# ──────────────────────────────────────────────────────────────────────────────
# PHASE 1: ANALYZE MAVEN COMPILATION ERRORS
# ──────────────────────────────────────────────────────────────────────────────

analyze_compilation_errors() {
    log_info "Analyzing Maven compilation errors..."

    local error_count=0
    local maven_logs=$(find /tmp -name "maven-*.log" -type f -mmin -30 2>/dev/null || true)

    if [[ -z "${maven_logs}" ]]; then
        log_info "  No recent Maven logs found"
        return 0
    fi

    while IFS= read -r maven_log; do
        [[ -z "${maven_log}" ]] && continue

        log_info "  Parsing: ${maven_log}"

        # Syntax errors: [ERROR] /path/to/File.java:[line]: error: message
        while IFS= read -r line; do
            if echo "${line}" | grep -qE "^\[ERROR\].*\.java:[0-9]+:\s*error:"; then
                ((error_count++))

                local file=$(echo "${line}" | sed -E 's/.*\[ERROR\]\s+([^:]+):.*/\1/')
                local line_num=$(echo "${line}" | sed -E 's/.*\.java:([0-9]+):.*/\1/')
                local msg=$(echo "${line}" | sed 's/.*error: //')

                # Classify by error pattern
                local root_cause="syntax_error"
                if echo "${msg}" | grep -qE "cannot find symbol|incompatible types"; then
                    root_cause="type_error"
                elif echo "${msg}" | grep -q "import"; then
                    root_cause="missing_import"
                elif echo "${msg}" | grep -q "symbol not found"; then
                    root_cause="reference_error"
                fi

                create_error "compilation" "FAIL" "${file}" "${line_num}" "${msg}" "${root_cause}" >> "${TEMP_ERRORS}"
                echo "" >> "${TEMP_ERRORS}"

            fi
        done < "${maven_log}"

    done <<< "${maven_logs}"

    log_info "  Found ${error_count} compilation errors"
}

# ──────────────────────────────────────────────────────────────────────────────
# PHASE 2: ANALYZE JUNIT TEST FAILURES
# ──────────────────────────────────────────────────────────────────────────────

analyze_test_failures() {
    log_info "Analyzing JUnit test failures..."

    local surefire_dir="${PROJECT_ROOT}/target/surefire-reports"
    if [[ ! -d "${surefire_dir}" ]]; then
        log_info "  No Surefire reports found (tests may not have run)"
        return 0
    fi

    local error_count=0
    local test_logs=$(find "${surefire_dir}" -name "*.txt" -type f 2>/dev/null || true)

    if [[ -z "${test_logs}" ]]; then
        log_info "  No test failure logs found"
        return 0
    fi

    while IFS= read -r test_log; do
        [[ -z "${test_log}" ]] && continue

        log_info "  Parsing test log: $(basename ${test_log})"

        # Extract test class name from filename
        local test_class=$(basename "${test_log}" .txt)

        # Look for failure patterns
        if grep -q "FAILURE" "${test_log}"; then
            ((error_count++))

            local failure_msg=$(grep -A 3 "FAILURE" "${test_log}" | head -5 | tr '\n' ' ')

            # Classify by failure type
            local root_cause="test_failure"
            if echo "${failure_msg}" | grep -qE "AssertionError|expected.*but was"; then
                root_cause="assertion_failure"
            elif echo "${failure_msg}" | grep -qE "Timeout|timeout expired"; then
                root_cause="timeout"
            elif echo "${failure_msg}" | grep -qE "ResourceLeakDetector|leak detected"; then
                root_cause="resource_leak"
            elif echo "${failure_msg}" | grep -qE "Exception|Error"; then
                root_cause="exception"
            fi

            create_error "test" "FAIL" "${test_class}" "0" "${failure_msg}" "${root_cause}" >> "${TEMP_ERRORS}"
            echo "" >> "${TEMP_ERRORS}"
        fi
    done <<< "${test_logs}"

    log_info "  Found ${error_count} test failures"
}

# ──────────────────────────────────────────────────────────────────────────────
# PHASE 3: ANALYZE GUARD VALIDATION VIOLATIONS
# ──────────────────────────────────────────────────────────────────────────────

analyze_guard_violations() {
    log_info "Analyzing guard validation violations (H phase)..."

    local guard_receipt="${RECEIPTS_DIR}/h-guards-receipt.json"
    if [[ ! -f "${guard_receipt}" ]]; then
        log_info "  No guard receipt found (H phase may not have run)"
        return 0
    fi

    # Simple approach: count violations and note them
    local violation_count=0
    if command -v jq &>/dev/null; then
        violation_count=$(jq '.violations | length' "${guard_receipt}" 2>/dev/null || echo "0")

        if [[ ${violation_count} -gt 0 ]]; then
            # Get first violation for logging
            local first_pattern=$(jq -r '.violations[0].pattern' "${guard_receipt}" 2>/dev/null || echo "UNKNOWN")
            log_info "  Parsing ${violation_count} guard violations (first: ${first_pattern})"

            # Mark that we found violations
            {
                echo "{"
                echo "  \"error_type\": \"guard\","
                echo "  \"severity\": \"FAIL\","
                echo "  \"file\": \"violations_found\","
                echo "  \"line\": ${violation_count},"
                echo "  \"message\": \"${violation_count} guard violations detected\","
                echo "  \"root_cause\": \"${first_pattern}\""
                echo "}"
            } >> "${TEMP_ERRORS}"
        fi
    else
        violation_count=$(grep -c '"pattern"' "${guard_receipt}" 2>/dev/null || echo "0")
        log_info "  Found ${violation_count} guard violations (parsed without jq)"
    fi
}

# ──────────────────────────────────────────────────────────────────────────────
# PHASE 4: ANALYZE INVARIANT VALIDATION VIOLATIONS
# ──────────────────────────────────────────────────────────────────────────────

analyze_invariant_violations() {
    log_info "Analyzing invariant validation violations (Q phase)..."

    local invariant_receipt="${RECEIPTS_DIR}/q-invariants-receipt.json"
    if [[ ! -f "${invariant_receipt}" ]]; then
        log_info "  No invariant receipt found (Q phase may not have run)"
        return 0
    fi

    # Simple approach: count violations and note them
    local violation_count=0
    if command -v jq &>/dev/null; then
        violation_count=$(jq '.violations | length' "${invariant_receipt}" 2>/dev/null || echo "0")

        if [[ ${violation_count} -gt 0 ]]; then
            # Get first violation for logging
            local first_pattern=$(jq -r '.violations[0].pattern' "${invariant_receipt}" 2>/dev/null || echo "UNKNOWN")
            log_info "  Parsing ${violation_count} invariant violations (first: ${first_pattern})"

            # Mark that we found violations
            {
                echo "{"
                echo "  \"error_type\": \"invariant\","
                echo "  \"severity\": \"FAIL\","
                echo "  \"file\": \"violations_found\","
                echo "  \"line\": ${violation_count},"
                echo "  \"message\": \"${violation_count} invariant violations detected\","
                echo "  \"root_cause\": \"${first_pattern}\""
                echo "}"
            } >> "${TEMP_ERRORS}"
        fi
    else
        violation_count=$(grep -c '"pattern"' "${invariant_receipt}" 2>/dev/null || echo "0")
        log_info "  Found ${violation_count} invariant violations (parsed without jq)"
    fi
}

# ──────────────────────────────────────────────────────────────────────────────
# PHASE 5: ANALYZE BUILD LOGS (dx.sh OUTPUT)
# ──────────────────────────────────────────────────────────────────────────────

analyze_dx_output() {
    log_info "Analyzing dx.sh output..."

    local dx_log="${PROJECT_ROOT}/.claude/logs/dx-current.log"
    if [[ ! -f "${dx_log}" ]]; then
        log_info "  No dx.sh log found (build may not have run)"
        return 0
    fi

    local error_count=0

    # Look for phase failures in dx.sh output
    if grep -q "RED\|FAIL\|ERROR" "${dx_log}"; then
        ((error_count++))

        # Extract phase that failed
        local failed_phase=$(grep -E "Phase.*RED\|Phase.*FAIL" "${dx_log}" | head -1 | sed 's/.*Phase //' | sed 's/ RED.*//')
        local error_line=$(grep -E "RED\|FAIL\|ERROR" "${dx_log}" | head -1)

        create_error "build_phase" "FAIL" "dx.sh" "0" "${error_line}" "phase_${failed_phase}" >> "${TEMP_ERRORS}"
        echo "" >> "${TEMP_ERRORS}"
    fi

    log_info "  Analyzed dx.sh output"
}

# ──────────────────────────────────────────────────────────────────────────────
# PHASE 6: AGGREGATE ERRORS & CREATE RECEIPT
# ──────────────────────────────────────────────────────────────────────────────

aggregate_errors() {
    log_info "Aggregating error analysis..."

    local total_errors=0
    local compilation_count=0
    local test_count=0
    local guard_count=0
    local invariant_count=0
    local error_json="[]"

    # Count and format errors from temp file
    if [[ -f "${TEMP_ERRORS}" ]] && [[ -s "${TEMP_ERRORS}" ]]; then
        # Count total errors
        total_errors=$(grep -c '"error_type"' "${TEMP_ERRORS}" || echo "0")

        # Count by type
        compilation_count=$(grep -c '"compilation"' "${TEMP_ERRORS}" || echo "0")
        test_count=$(grep -c '"test"' "${TEMP_ERRORS}" || echo "0")
        guard_count=$(grep -c '"guard"' "${TEMP_ERRORS}" || echo "0")
        invariant_count=$(grep -c '"invariant"' "${TEMP_ERRORS}" || echo "0")

        # Build JSON array from errors (simple approach)
        if command -v jq &>/dev/null; then
            error_json="$(jq -s '.' "${TEMP_ERRORS}" 2>/dev/null || echo '[]')"
        else
            # Fallback: just mark that errors were found
            error_json="[{\"count\": ${total_errors}, \"note\": \"jq unavailable\"}]"
        fi
    fi

    local by_type="{
  \"compilation\": ${compilation_count},
  \"test\": ${test_count},
  \"guard\": ${guard_count},
  \"invariant\": ${invariant_count},
  \"total\": ${total_errors}
}"

    # Determine overall status
    local status="GREEN"
    if [[ ${total_errors} -gt 0 ]]; then
        status="RED"
    fi

    # Create receipt
    cat > "${ANALYSIS_RECEIPT}" <<EOF
{
  "phase": "error-analysis",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "status": "${status}",
  "total_errors": ${total_errors},
  "errors_by_type": ${by_type},
  "errors": ${error_json},
  "analysis_duration_ms": $((ANALYSIS_END - ANALYSIS_START)),
  "receipt_file": "${ANALYSIS_RECEIPT}"
}
EOF

    log_success "Created error analysis receipt: ${ANALYSIS_RECEIPT}"
    log_info "  Total errors found: ${total_errors}"
    log_info "  Status: ${status}"
}

# ──────────────────────────────────────────────────────────────────────────────
# MAIN EXECUTION
# ──────────────────────────────────────────────────────────────────────────────

main() {
    log_info "Starting error analysis..."

    ANALYSIS_START=$(date +%s%N)

    # Initialize temp file
    > "${TEMP_ERRORS}"

    # Run all analysis phases
    analyze_compilation_errors
    analyze_test_failures
    analyze_guard_violations
    analyze_invariant_violations
    analyze_dx_output

    ANALYSIS_END=$(date +%s%N)

    # Aggregate and report
    aggregate_errors

    # Display summary
    echo ""
    echo "═══════════════════════════════════════════════════════════════════════════"
    echo "📋 Error Analysis Report"
    echo "───────────────────────────────────────────────────────────────────────────"

    if command -v jq &>/dev/null; then
        jq '.errors_by_type, .status' "${ANALYSIS_RECEIPT}" 2>/dev/null || cat "${ANALYSIS_RECEIPT}"
    else
        cat "${ANALYSIS_RECEIPT}"
    fi

    echo "═══════════════════════════════════════════════════════════════════════════"
    echo ""

    # Cleanup
    rm -f "${TEMP_ERRORS}"

    # Determine exit code based on status
    if grep -q '"GREEN"' "${ANALYSIS_RECEIPT}"; then
        exit 0
    else
        exit 2
    fi
}

# Execute
main "$@"
