#!/usr/bin/env bash
# ==========================================================================
# validate-observatory.sh — Observatory Validation Suite
#
# Validates observatory facts, generation, and incremental mode.
# Sources common.sh, runs observatory.sh --force, validates 25+ fact files,
# checks staleness, tests incremental mode with cache hit ratio > 0,
# reads static-analysis.json for health_score, outputs JSON report.
#
# Usage:
#   ./scripts/validation/validate-observatory.sh
#
# Exit codes:
#   0 = All validations passed
#   1 = One or more validations failed
# ==========================================================================
set -uo pipefail

# ── Resolve script location and source common functions ─────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

# ── Constants ───────────────────────────────────────────────────────────────
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
FACTS_DIR="${REPO_ROOT}/docs/v6/latest/facts"
VALIDATION_DIR="${REPO_ROOT}/docs/v6/latest/validation"
REPORT_FILE="${VALIDATION_DIR}/observatory.json"

# ── Validation state ────────────────────────────────────────────────────────
declare -a VALIDATION_ERRORS=()
declare -a VALIDATION_WARNINGS=()
declare -i VALIDATIONS_PASSED=0
declare -i VALIDATIONS_FAILED=0
declare -i FACT_FILES_COUNT=0
declare -i CACHE_HIT_RATIO=0
declare -i HEALTH_SCORE=0

# ── Helper functions ────────────────────────────────────────────────────────
log_validation() {
    local status="$1"
    local message="$2"
    case "$status" in
        PASS) echo -e "  ${GREEN}OK${RESET} $message" ;;
        FAIL) echo -e "  ${RED}FAIL${RESET} $message"; VALIDATIONS_FAILED=$((VALIDATIONS_FAILED + 1)) ;;
        WARN) echo -e "  ${YELLOW}WARN${RESET} $message"; VALIDATION_WARNINGS+=("$message") ;;
    esac
    [[ "$status" == "PASS" ]] && VALIDATIONS_PASSED=$((VALIDATIONS_PASSED + 1))
}

# ── Test 1: Source common.sh ────────────────────────────────────────────────
test_common_sh_sourced() {
    log_section "Test 1: Source common.sh"

    # Verify common.sh functions are available
    if declare -f log_info >/dev/null 2>&1; then
        log_validation "PASS" "common.sh sourced successfully"
        return 0
    else
        log_validation "FAIL" "common.sh functions not available"
        VALIDATION_ERRORS+=("common.sh not sourced correctly")
        return 1
    fi
}

# ── Test 2: Run observatory.sh --force to regenerate facts ───────────────────
test_observatory_force_regeneration() {
    log_section "Test 2: Run observatory.sh --force"

    local observatory_script="${REPO_ROOT}/scripts/observatory/observatory.sh"

    if [[ ! -x "$observatory_script" ]]; then
        log_validation "FAIL" "Observatory script not found or not executable: $observatory_script"
        VALIDATION_ERRORS+=("observatory.sh not found or not executable")
        return 1
    fi

    log_info "Running observatory.sh with --force flag to regenerate all facts..."
    OBSERVATORY_FORCE=1 bash "$observatory_script" 2>&1 | tee /tmp/observatory-force-output.log

    local exit_code=${PIPESTATUS[0]}
    if [[ $exit_code -eq 0 ]]; then
        log_validation "PASS" "Observatory --force regeneration completed successfully"
    else
        log_validation "FAIL" "Observatory --force regeneration failed with exit code $exit_code"
        VALIDATION_ERRORS+=("observatory.sh --force failed")
    fi

    return $exit_code
}

# ── Test 3: Validate 25+ JSON fact files ─────────────────────────────────────
test_fact_files_validation() {
    log_section "Test 3: Validate 25+ JSON fact files"

    if [[ ! -d "$FACTS_DIR" ]]; then
        log_validation "FAIL" "Facts directory not found: $FACTS_DIR"
        VALIDATION_ERRORS+=("Facts directory missing")
        return 1
    fi

    # Required fact files (25+ files)
    local -a REQUIRED_FACT_FILES=(
        "modules.json"
        "gates.json"
        "deps-conflicts.json"
        "reactor.json"
        "shared-src.json"
        "tests.json"
        "static-analysis.json"
        "duplicates.json"
        "dual-family.json"
        "coverage.json"
        "docker-testing.json"
        "integration.json"
        "integration-facts.json"
        "a2a-auth.json"
        "a2a-auth-stats.json"
        "a2a-compliance-score.json"
        "a2a-handoff.json"
        "a2a-handoff-metrics.json"
        "a2a-modules.json"
        "a2a-server-status.json"
        "a2a-skill-metrics.json"
        "a2a-skills.json"
        "a2a-tests.json"
        "checkstyle-warnings.json"
        "pmd-violations.json"
        "spotbugs-findings.json"
    )

    local fact_count=0
    local invalid_count=0

    for fact_file in "${REQUIRED_FACT_FILES[@]}"; do
        local full_path="${FACTS_DIR}/${fact_file}"
        if [[ -f "$full_path" ]]; then
            fact_count=$((fact_count + 1))
            if jq empty "$full_path" 2>/dev/null; then
                log_validation "PASS" "Valid JSON: $fact_file"
            else
                log_validation "FAIL" "Invalid JSON: $fact_file"
                invalid_count=$((invalid_count + 1))
                VALIDATION_ERRORS+=("Invalid JSON in $fact_file")
            fi
        else
            log_validation "WARN" "Missing fact file: $fact_file"
        fi
    done

    # Also count any additional fact files in directory
    local additional_count
    additional_count=$(find "$FACTS_DIR" -name "*.json" -type f 2>/dev/null | wc -l | tr -d ' ')
    FACT_FILES_COUNT=$additional_count

    echo ""
    log_info "Total fact files found: $FACT_FILES_COUNT (required: 25+)"

    if [[ $FACT_FILES_COUNT -ge 25 ]]; then
        log_validation "PASS" "Sufficient fact files present ($FACT_FILES_COUNT >= 25)"
    else
        log_validation "FAIL" "Insufficient fact files ($FACT_FILES_COUNT < 25)"
        VALIDATION_ERRORS+=("Only $FACT_FILES_COUNT fact files found, need 25+")
        return 1
    fi

    if [[ $invalid_count -gt 0 ]]; then
        log_validation "FAIL" "$invalid_count invalid JSON files detected"
        return 1
    fi

    return 0
}

# ── Test 4: Run check-staleness.sh and expect exit code 0 ────────────────────
test_staleness_check() {
    log_section "Test 4: Run check-staleness.sh (expect exit 0)"

    local staleness_script="${REPO_ROOT}/scripts/observatory/check-staleness.sh"

    if [[ ! -x "$staleness_script" ]]; then
        log_validation "FAIL" "Staleness check script not found or not executable"
        VALIDATION_ERRORS+=("check-staleness.sh not found")
        return 1
    fi

    log_info "Running check-staleness.sh..."
    bash "$staleness_script" 2>&1 | tee /tmp/staleness-output.log
    local exit_code=${PIPESTATUS[0]}

    if [[ $exit_code -eq 0 ]]; then
        log_validation "PASS" "Staleness check passed (FRESH)"
    else
        log_validation "FAIL" "Staleness check failed with exit code $exit_code (facts may be stale)"
        VALIDATION_ERRORS+=("check-staleness.sh returned $exit_code")
    fi

    return $exit_code
}

# ── Test 5: Test incremental mode with cache hit ratio > 0 ───────────────────
test_incremental_mode_cache_hits() {
    log_section "Test 5: Test incremental mode (cache hit ratio > 0)"

    local observatory_script="${REPO_ROOT}/scripts/observatory/observatory.sh"

    # First, ensure cache exists by running once if not already
    log_info "Running observatory in incremental mode to test cache..."

    # Run without force flag (incremental mode)
    bash "$observatory_script" 2>&1 | tee /tmp/observatory-incremental-output.log
    local exit_code=${PIPESTATUS[0]}

    if [[ $exit_code -ne 0 ]]; then
        log_validation "FAIL" "Incremental observatory run failed"
        VALIDATION_ERRORS+=("Incremental mode failed")
        return 1
    fi

    # Extract cache hit ratio from output
    # Format: "Hit Ratio:     0.XX"
    local hit_ratio
    hit_ratio=$(grep -E "Hit Ratio:" /tmp/observatory-incremental-output.log 2>/dev/null | tail -1 | grep -oE '[0-9]+\.[0-9]+' || echo "0")

    log_info "Cache hit ratio detected: $hit_ratio"

    # Convert to integer for comparison (multiply by 100)
    CACHE_HIT_RATIO=$(echo "$hit_ratio * 100" | bc 2>/dev/null | cut -d'.' -f1 || echo "0")

    if [[ $CACHE_HIT_RATIO -gt 0 ]]; then
        log_validation "PASS" "Cache hit ratio > 0 (actual: ${hit_ratio})"
    else
        log_validation "WARN" "Cache hit ratio is 0 (incremental caching may not be working)"
    fi

    return 0
}

# ── Test 6: Read static-analysis.json and report health_score ────────────────
test_static_analysis_health_score() {
    log_section "Test 6: Read static-analysis.json health_score"

    local static_analysis_file="${FACTS_DIR}/static-analysis.json"

    if [[ ! -f "$static_analysis_file" ]]; then
        log_validation "WARN" "static-analysis.json not found, skipping health score check"
        return 0
    fi

    # Extract health_score using jq
    HEALTH_SCORE=$(jq -r '.health_score // 0' "$static_analysis_file" 2>/dev/null || echo "0")

    log_info "Health score from static-analysis.json: $HEALTH_SCORE"

    # Validate health_score is a valid number
    if [[ ! "$HEALTH_SCORE" =~ ^[0-9]+$ ]]; then
        log_validation "FAIL" "Invalid health_score value: $HEALTH_SCORE"
        VALIDATION_ERRORS+=("Invalid health_score in static-analysis.json")
        return 1
    fi

    # Report health status
    if [[ $HEALTH_SCORE -ge 90 ]]; then
        log_validation "PASS" "Health score: $HEALTH_SCORE (EXCELLENT)"
    elif [[ $HEALTH_SCORE -ge 70 ]]; then
        log_validation "PASS" "Health score: $HEALTH_SCORE (GOOD)"
    elif [[ $HEALTH_SCORE -ge 50 ]]; then
        log_validation "WARN" "Health score: $HEALTH_SCORE (FAIR)"
    else
        log_validation "FAIL" "Health score: $HEALTH_SCORE (POOR)"
        VALIDATION_ERRORS+=("Low health score: $HEALTH_SCORE")
        return 1
    fi

    return 0
}

# ── Generate JSON report ────────────────────────────────────────────────────
generate_json_report() {
    log_section "Generating JSON Report"

    mkdir -p "$VALIDATION_DIR"

    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    # Build fact files list as JSON array
    local fact_files_json="[]"
    if [[ -d "$FACTS_DIR" ]]; then
        fact_files_json=$(find "$FACTS_DIR" -name "*.json" -type f -exec basename {} \; 2>/dev/null | jq -R . | jq -s . || echo "[]")
    fi

    # Build errors array
    local errors_json="[]"
    if [[ ${#VALIDATION_ERRORS[@]} -gt 0 ]]; then
        errors_json=$(printf '%s\n' "${VALIDATION_ERRORS[@]}" | jq -R . | jq -s .)
    fi

    # Build warnings array
    local warnings_json="[]"
    if [[ ${#VALIDATION_WARNINGS[@]} -gt 0 ]]; then
        warnings_json=$(printf '%s\n' "${VALIDATION_WARNINGS[@]}" | jq -R . | jq -s .)
    fi

    # Generate final JSON report
    cat > "$REPORT_FILE" << REPORT_EOF
{
  "test_type": "observatory-validation",
  "timestamp": "${timestamp}",
  "total_tests": $((VALIDATIONS_PASSED + VALIDATIONS_FAILED)),
  "passed": ${VALIDATIONS_PASSED},
  "failed": ${VALIDATIONS_FAILED},
  "health_score": ${HEALTH_SCORE},
  "cache_hit_ratio": ${CACHE_HIT_RATIO},
  "fact_files_count": ${FACT_FILES_COUNT},
  "fact_files": ${fact_files_json},
  "warnings": ${warnings_json},
  "errors": ${errors_json},
  "status": "$([[ $VALIDATIONS_FAILED -eq 0 ]] && echo "GREEN" || echo "RED")"
}
REPORT_EOF

    log_info "JSON report written to: $REPORT_FILE"
}

# ── Main execution ──────────────────────────────────────────────────────────
main() {
    local start_time
    start_time=$(date +%s)

    log_header "YAWL Observatory Validation Suite"
    echo ""

    # Run all validation tests
    test_common_sh_sourced
    test_observatory_force_regeneration
    test_fact_files_validation
    test_staleness_check
    test_incremental_mode_cache_hits
    test_static_analysis_health_score

    # Generate JSON report
    generate_json_report

    # Output summary
    local end_time duration
    end_time=$(date +%s)
    duration=$((end_time - start_time))

    echo ""
    log_header "Validation Complete"
    echo ""
    echo "  Total Tests:  $((VALIDATIONS_PASSED + VALIDATIONS_FAILED))"
    echo "  Passed:       ${VALIDATIONS_PASSED}"
    echo "  Failed:       ${VALIDATIONS_FAILED}"
    echo "  Warnings:     ${#VALIDATION_WARNINGS[@]}"
    echo "  Fact Files:   ${FACT_FILES_COUNT}"
    echo "  Health Score: ${HEALTH_SCORE}"
    echo "  Cache Hits:   ${CACHE_HIT_RATIO}%"
    echo "  Duration:     ${duration}s"
    echo "  Report:       ${REPORT_FILE}"
    echo ""

    # Return exit code based on validation results
    if [[ $VALIDATIONS_FAILED -gt 0 ]]; then
        log_error "Validation FAILED with ${VALIDATIONS_FAILED} errors"
        return 1
    else
        log_success "All validations PASSED"
        return 0
    fi
}

# Execute main function
main "$@"
