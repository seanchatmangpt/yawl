#!/usr/bin/env bash
# ==========================================================================
# validate-all.sh - Master validation orchestrator with parallel execution
#
# Usage:
#   bash scripts/validation/validate-all.sh [OPTIONS]
#
# Options:
#   --parallel     Run validations in parallel (default)
#   --sequential   Run validations sequentially
#   --fail-fast    Stop on first failure
#   --no-fail-fast Continue even on failures (default for parallel)
#   --output-dir   Directory for output files (default: docs/validation)
#   --format       Output format: json, junit, both (default: both)
#   --help         Show this help
#
# Runs all validation suites:
#   1. Documentation validation
#   2. Observatory validation
#   3. Performance baseline validation
#   4. Release readiness check
#
# Output:
#   - validation-report.json    - Aggregated JSON results
#   - validation-report.xml     - JUnit XML for CI integration
#   - validation-report.md      - Human-readable summary
#
# Exit codes:
#   0 - All validations passed
#   1 - One or more validations failed
#   2 - Warnings only (no failures)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Source the aggregation library
source "${SCRIPT_DIR}/lib/output-aggregation.sh"

# Default options
PARALLEL=true
FAIL_FAST=false
OUTPUT_DIR="${PROJECT_ROOT}/docs/validation"
FORMAT="both"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# -------------------------------------------------------------------------
# Help text
# -------------------------------------------------------------------------
show_help() {
    cat << EOF
YAWL Master Validation Orchestrator

Usage: bash scripts/validation/validate-all.sh [OPTIONS]

Options:
  --parallel       Run validations in parallel (default)
  --sequential     Run validations sequentially
  --fail-fast      Stop on first failure
  --no-fail-fast   Continue even on failures (default for parallel)
  --output-dir DIR Directory for output files (default: docs/validation)
  --format FMT     Output format: json, junit, both (default: both)
  --help           Show this help

Validations:
  1. documentation  - Package-info, markdown links, XSD schemas
  2. observatory    - Fact freshness, hash verification
  3. performance    - Build time, observatory runtime
  4. release        - Complete release readiness

Examples:
  # Run all validations in parallel
  bash scripts/validation/validate-all.sh

  # Run sequentially with fail-fast
  bash scripts/validation/validate-all.sh --sequential --fail-fast

  # JSON output only for CI
  bash scripts/validation/validate-all.sh --format json --no-fail-fast
EOF
}

# -------------------------------------------------------------------------
# Parse arguments
# -------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --parallel)
            PARALLEL=true
            shift
            ;;
        --sequential)
            PARALLEL=false
            shift
            ;;
        --fail-fast)
            FAIL_FAST=true
            shift
            ;;
        --no-fail-fast)
            FAIL_FAST=false
            shift
            ;;
        --output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --format)
            FORMAT="$2"
            shift 2
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            show_help
            exit 1
            ;;
    esac
done

# Ensure output directory exists
mkdir -p "${OUTPUT_DIR}"

# -------------------------------------------------------------------------
# Run a single validation suite
# Arguments:
#   $1 - Suite name
#   $2 - Script path
# Returns: 0 on success, 1 on failure, 2 on warning
# -------------------------------------------------------------------------
run_validation() {
    local suite_name="$1"
    local script_path="$2"
    local start_time end_time duration

    echo -e "${BLUE}[RUNNING]${NC} ${suite_name}..."

    start_time=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))

    # Run the validation script and capture output
    local exit_code output
    if output=$("${script_path}" 2>&1); then
        exit_code=0
    else
        exit_code=$?
    fi

    end_time=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
    duration=$((end_time - start_time))

    # Add result to aggregation
    case $exit_code in
        0)
            agg_add_result "${suite_name}" "PASS" "All checks passed" "${duration}"
            echo -e "${GREEN}[PASSED]${NC} ${suite_name} (${duration}ms)"
            ;;
        2)
            agg_add_result "${suite_name}" "WARN" "Completed with warnings" "${duration}"
            echo -e "${YELLOW}[WARNING]${NC} ${suite_name} (${duration}ms)"
            ;;
        *)
            agg_add_result "${suite_name}" "FAIL" "Exit code: ${exit_code}" "${duration}"
            echo -e "${RED}[FAILED]${NC} ${suite_name} (${duration}ms)"
            # Store output for error reporting
            agg_add_error "${suite_name} failed with exit code ${exit_code}" "${output}"
            ;;
    esac

    return $exit_code
}

# -------------------------------------------------------------------------
# Run validation in background for parallel execution
# -------------------------------------------------------------------------
run_validation_async() {
    local suite_name="$1"
    local script_path="$2"
    local result_file="${OUTPUT_DIR}/.result-${suite_name}.json"

    (
        # Initialize local aggregation
        agg_init "${suite_name}" "${OUTPUT_DIR}"
        run_validation "${suite_name}" "${script_path}"
        local exit_code=$?
        agg_output_json "${result_file}" > /dev/null
        exit $exit_code
    ) &
    echo $!
}

# -------------------------------------------------------------------------
# Main
# -------------------------------------------------------------------------
echo ""
echo "========================================="
echo "  YAWL Master Validation"
echo "  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "========================================="
echo ""
echo "Mode: $([ "$PARALLEL" = true ] && echo "Parallel" || echo "Sequential")"
echo "Fail-fast: $([ "$FAIL_FAST" = true ] && echo "Enabled" || echo "Disabled")"
echo "Output: ${OUTPUT_DIR}"
echo ""

# Initialize master aggregation
agg_init "master-validation" "${OUTPUT_DIR}"

# Validation suites
declare -A SUITES=(
    ["documentation"]="${SCRIPT_DIR}/validate-documentation.sh"
    ["observatory"]="${SCRIPT_DIR}/validate-observatory.sh"
    ["performance"]="${SCRIPT_DIR}/validate-performance-baselines.sh"
    ["release"]="${SCRIPT_DIR}/validate-release.sh"
)

declare -A PIDS=()
declare -A EXIT_CODES=()
TOTAL_FAILED=0
TOTAL_WARNINGS=0

if [[ "$PARALLEL" = true ]]; then
    # -------------------------------------------------------------------------
    # Parallel Execution
    # -------------------------------------------------------------------------
    echo -e "${BLUE}Starting parallel validation...${NC}"
    echo ""

    # Launch all validations in parallel
    for suite_name in "${!SUITES[@]}"; do
        script_path="${SUITES[$suite_name]}"
        if [[ -f "$script_path" ]]; then
            pid=$(run_validation_async "${suite_name}" "${script_path}")
            PIDS["${suite_name}"]="${pid}"
            echo "  Started ${suite_name} (PID: ${pid})"
        else
            echo -e "  ${YELLOW}SKIPPED: ${suite_name} (script not found)${NC}"
            agg_add_result "${suite_name}" "SKIP" "Script not found: ${script_path}"
        fi
    done

    echo ""
    echo -e "${BLUE}Waiting for validations to complete...${NC}"
    echo ""

    # Wait for all processes and collect results
    for suite_name in "${!PIDS[@]}"; do
        pid="${PIDS[${suite_name}]}"
        wait "${pid}" 2>/dev/null
        exit_code=$?
        EXIT_CODES["${suite_name}"]="${exit_code}"

        # Merge results from sub-suite
        result_file="${OUTPUT_DIR}/.result-${suite_name}.json"
        if [[ -f "${result_file}" ]]; then
            agg_add_suite "${result_file}"
        fi

        case $exit_code in
            0) ;;
            2) TOTAL_WARNINGS=$((TOTAL_WARNINGS + 1)) ;;
            *) TOTAL_FAILED=$((TOTAL_FAILED + 1)) ;;
        esac

        if [[ "$FAIL_FAST" = true ]] && [[ $exit_code -ne 0 ]] && [[ $exit_code -ne 2 ]]; then
            echo -e "${RED}Fail-fast triggered, stopping remaining validations${NC}"
            # Kill remaining processes
            for other_pid in "${PIDS[@]}"; do
                [[ "${other_pid}" != "${pid}" ]] && kill "${other_pid}" 2>/dev/null || true
            done
            break
        fi
    done

else
    # -------------------------------------------------------------------------
    # Sequential Execution
    # -------------------------------------------------------------------------
    for suite_name in "${!SUITES[@]}"; do
        script_path="${SUITES[$suite_name]}"
        if [[ -f "$script_path" ]]; then
            run_validation "${suite_name}" "${script_path}"
            exit_code=$?
            EXIT_CODES["${suite_name}"]="${exit_code}"

            case $exit_code in
                0) ;;
                2) TOTAL_WARNINGS=$((TOTAL_WARNINGS + 1)) ;;
                *) TOTAL_FAILED=$((TOTAL_FAILED + 1)) ;;
            esac

            if [[ "$FAIL_FAST" = true ]] && [[ $exit_code -ne 0 ]] && [[ $exit_code -ne 2 ]]; then
                echo -e "${RED}Fail-fast triggered, stopping remaining validations${NC}"
                break
            fi
        else
            echo -e "${YELLOW}SKIPPED: ${suite_name} (script not found)${NC}"
            agg_add_result "${suite_name}" "SKIP" "Script not found: ${script_path}"
        fi
        echo ""
    done
fi

# -------------------------------------------------------------------------
# Generate Output
# -------------------------------------------------------------------------
echo ""
echo -e "${BLUE}Generating reports...${NC}"

case "$FORMAT" in
    json)
        agg_output_json "${OUTPUT_DIR}/validation-report.json" > /dev/null
        echo "  JSON: ${OUTPUT_DIR}/validation-report.json"
        ;;
    junit)
        agg_output_junit "${OUTPUT_DIR}/validation-report.xml" > /dev/null
        echo "  JUnit: ${OUTPUT_DIR}/validation-report.xml"
        ;;
    both)
        agg_output_json "${OUTPUT_DIR}/validation-report.json" > /dev/null
        agg_output_junit "${OUTPUT_DIR}/validation-report.xml" > /dev/null
        echo "  JSON: ${OUTPUT_DIR}/validation-report.json"
        echo "  JUnit: ${OUTPUT_DIR}/validation-report.xml"
        ;;
esac

# Generate markdown summary
agg_output_summary > "${OUTPUT_DIR}/validation-report.md"
echo "  Summary: ${OUTPUT_DIR}/validation-report.md"

# Output summary to console
echo ""
agg_output_summary

# -------------------------------------------------------------------------
# Cleanup
# -------------------------------------------------------------------------
rm -f "${OUTPUT_DIR}"/.result-*.json 2>/dev/null
rm -f "${OUTPUT_DIR}"/.agg-state-*.json 2>/dev/null
rm -f "${OUTPUT_DIR}"/.agg-lock-* 2>/dev/null

# -------------------------------------------------------------------------
# Exit with appropriate code
# -------------------------------------------------------------------------
if [[ $TOTAL_FAILED -gt 0 ]]; then
    exit 1
elif [[ $TOTAL_WARNINGS -gt 0 ]]; then
    exit 2
else
    exit 0
fi
