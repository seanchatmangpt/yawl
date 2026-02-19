#!/usr/bin/env bash
# ==========================================================================
# validate-all.sh - Master validation orchestrator with parallel execution
#
# Usage:
#   bash scripts/validation/validate-all.sh [OPTIONS]
#
# Options:
#   --parallel       Run validations in parallel (default)
#   --sequential     Run validations sequentially
#   --fail-fast      Stop on first failure
#   --no-fail-fast   Continue even on failures (default for parallel)
#   --output-dir DIR Directory for output files (default: docs/validation)
#   --format FMT     Output format: json, junit, both (default: both)
#   --a2a-only       Run only A2A validations
#   --mcp-only       Run only MCP validations
#   --quick          Skip time-intensive tests (chaos, stress)
#   --help           Show this help
#
# Runs all validation suites:
#   Core:
#     1. Documentation validation
#     2. Observatory validation
#     3. Performance baseline validation
#     4. Release readiness check
#   A2A:
#     5. A2A protocol tests
#     6. A2A auth tests
#     7. A2A skills tests
#     8. A2A handoff tests
#   MCP:
#     9. MCP STDIO tests
#     10. MCP tool tests
#   Chaos:
#     11. Chaos engineering tests
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
A2A_ONLY=false
MCP_ONLY=false
QUICK=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
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
  --a2a-only       Run only A2A validations (protocol, auth, skills, handoff)
  --mcp-only       Run only MCP validations (stdio, tools)
  --quick          Skip time-intensive tests (chaos, stress)
  --help           Show this help

Validations:
  Core:
    documentation  - Package-info, markdown links, XSD schemas
    observatory    - Fact freshness, hash verification
    performance    - Build time, observatory runtime
    release        - Complete release readiness

  A2A (Agent-to-Agent):
    a2a-protocol   - Agent discovery, message format, error handling
    a2a-auth       - SPIFFE, JWT, API Key, OAuth2 authentication
    a2a-skills     - launch_workflow, query_workflows, manage_workitems
    a2a-handoff    - JWT handoff tokens, session management

  MCP (Model Context Protocol):
    mcp-stdio      - STDIO transport, protocol handshake
    mcp-tools      - Tool registration, invocation, responses

  Chaos Engineering:
    chaos          - Network, CPU, memory stress, resilience tests

Examples:
  # Run all validations in parallel
  bash scripts/validation/validate-all.sh

  # Run sequentially with fail-fast
  bash scripts/validation/validate-all.sh --sequential --fail-fast

  # JSON output only for CI
  bash scripts/validation/validate-all.sh --format json --no-fail-fast

  # Quick validation (skip chaos/stress tests)
  bash scripts/validation/validate-all.sh --quick

  # A2A only during A2A development
  bash scripts/validation/validate-all.sh --a2a-only

  # MCP only during MCP development
  bash scripts/validation/validate-all.sh --mcp-only
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
        --a2a-only)
            A2A_ONLY=true
            shift
            ;;
        --mcp-only)
            MCP_ONLY=true
            shift
            ;;
        --quick)
            QUICK=true
            shift
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
# Build suites array based on options
# -------------------------------------------------------------------------
build_suites() {
    declare -A suites

    # Core validations (always run unless a2a-only or mcp-only)
    if [[ "$A2A_ONLY" = false ]] && [[ "$MCP_ONLY" = false ]]; then
        suites["documentation"]="${SCRIPT_DIR}/validate-documentation.sh"
        suites["observatory"]="${SCRIPT_DIR}/validate-observatory.sh"
        suites["performance"]="${SCRIPT_DIR}/validate-performance-baselines.sh"
        suites["release"]="${SCRIPT_DIR}/validate-release.sh"
    fi

    # A2A validations (run if --a2a-only or not --mcp-only)
    if [[ "$A2A_ONLY" = true ]] || [[ "$MCP_ONLY" = false ]]; then
        suites["a2a-protocol"]="${SCRIPT_DIR}/a2a/tests/test-protocol.sh"
        suites["a2a-auth"]="${SCRIPT_DIR}/a2a/tests/test-auth.sh"
        suites["a2a-skills"]="${SCRIPT_DIR}/a2a/tests/test-skills-validation.sh"
        suites["a2a-handoff"]="${SCRIPT_DIR}/a2a/tests/test-handoff-protocol.sh"
    fi

    # MCP validations (run if --mcp-only or not --a2a-only)
    if [[ "$MCP_ONLY" = true ]] || [[ "$A2A_ONLY" = false ]]; then
        suites["mcp-stdio"]="${SCRIPT_DIR}/mcp/tests/test-protocol-handshake.sh"
        suites["mcp-tools"]="${SCRIPT_DIR}/mcp/validate-mcp-compliance.sh"
    fi

    # Chaos engineering (skip if --quick)
    if [[ "$QUICK" = false ]] && [[ "$A2A_ONLY" = false ]] && [[ "$MCP_ONLY" = false ]]; then
        suites["chaos-engineering"]="${SCRIPT_DIR}/validate-chaos-stress.sh"
    fi

    # Return suites as nameref
    local -n suites_ref=$1
    for key in "${!suites[@]}"; do
        suites_ref["$key"]="${suites[$key]}"
    done
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
if [[ "$A2A_ONLY" = true ]]; then
    echo "Scope: A2A only"
elif [[ "$MCP_ONLY" = true ]]; then
    echo "Scope: MCP only"
elif [[ "$QUICK" = true ]]; then
    echo "Scope: Quick (no chaos/stress)"
else
    echo "Scope: All validations"
fi
echo ""

# Initialize master aggregation
agg_init "master-validation" "${OUTPUT_DIR}"

# Build validation suites based on options
declare -A SUITES=()
build_suites SUITES

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
