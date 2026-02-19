#!/usr/bin/env bash
# ==========================================================================
# run-all-a2a-tests.sh — Master A2A Test Runner
#
# Orchestrates all A2A validation tests with parallel execution support:
#   - Authentication provider tests
#   - Skills validation tests
#   - Handoff protocol tests
#   - Performance benchmarks
#
# Usage:
#   bash scripts/validation/a2a/tests/run-all-a2a-tests.sh
#   bash scripts/validation/a2a/tests/run-all-a2a-tests.sh --parallel
#   bash scripts/validation/a2a/tests/run-all-a2a-tests.sh --sequential
#   bash scripts/validation/a2a/tests/run-all-a2a-tests.sh --json
#   bash scripts/validation/a2a/tests/run-all-a2a-tests.sh --output-dir /path/to/results
#
# Exit Codes:
#   0 - All tests passed
#   1 - One or more tests failed
#   2 - Configuration or setup error
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/../lib"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"

# Configuration
PARALLEL=true
OUTPUT_FORMAT="text"
OUTPUT_DIR="${REPO_ROOT}/docs/validation/a2a"
VERBOSE=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

# Test suites
declare -A TEST_SUITES=(
    ["authentication"]="${SCRIPT_DIR}/test-authentication-providers.sh"
    ["skills"]="${SCRIPT_DIR}/test-skills-validation.sh"
    ["skills-functional"]="${SCRIPT_DIR}/test-skills.sh"
    ["handoff"]="${SCRIPT_DIR}/test-handoff-protocol.sh"
    ["performance"]="${SCRIPT_DIR}/test-performance-benchmark.sh"
)

# Test results
declare -A SUITE_RESULTS=()
declare -A SUITE_DURATIONS=()
TOTAL_PASSED=0
TOTAL_FAILED=0

# ── Help Text ─────────────────────────────────────────────────────────────
show_help() {
    cat << EOF
YAWL A2A Validation Test Runner

Usage: bash scripts/validation/a2a/tests/run-all-a2a-tests.sh [OPTIONS]

Options:
  --parallel       Run tests in parallel (default)
  --sequential     Run tests sequentially
  --json           Output results as JSON
  --output-dir DIR Directory for output files (default: docs/validation/a2a)
  --verbose        Enable verbose output
  --help           Show this help

Test Suites:
  authentication     - API Key, JWT, SPIFFE authentication tests
  skills             - A2A skill registration and discovery validation
  skills-functional  - A2A skill functional tests (input/output/error/permission)
  handoff            - ADR-025 handoff protocol tests
  performance        - Latency, throughput, and concurrent tests

Examples:
  # Run all tests in parallel
  bash scripts/validation/a2a/tests/run-all-a2a-tests.sh

  # Run sequentially with JSON output
  bash scripts/validation/a2a/tests/run-all-a2a-tests.sh --sequential --json

  # Save results to custom directory
  bash scripts/validation/a2a/tests/run-all-a2a-tests.sh --output-dir ./test-results
EOF
}

# ── Argument Parsing ──────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --parallel)     PARALLEL=true; shift ;;
        --sequential)   PARALLEL=false; shift ;;
        --json)         OUTPUT_FORMAT="json"; shift ;;
        --output-dir)   OUTPUT_DIR="$2"; shift 2 ;;
        --verbose|-v)   VERBOSE=1; shift ;;
        --help|-h)      show_help; exit 0 ;;
        *)              echo "Unknown option: $1" >&2; show_help; exit 2 ;;
    esac
done

# Ensure output directory exists
mkdir -p "${OUTPUT_DIR}"

# ── Logging Functions ─────────────────────────────────────────────────────
log_info() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${CYAN}[INFO]${NC} $*"
}

log_success() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${GREEN}[PASS]${NC} $*"
}

log_error() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${RED}[FAIL]${NC} $*" >&2
}

log_section() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "\n${BOLD}${BLUE}=== $1 ===${NC}"
}

# ── Time Helper ───────────────────────────────────────────────────────────
get_time_ms() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        perl -MTime::HiRes=time -e 'printf "%.0f\n", time * 1000'
    else
        date +%s%3N
    fi
}

# ── Run Single Test Suite ─────────────────────────────────────────────────
run_suite() {
    local suite_name="$1"
    local suite_script="$2"
    local start_time end_time duration exit_code output

    echo -e "${BLUE}[RUNNING]${NC} ${suite_name}..."

    start_time=$(get_time_ms)

    # Run the test suite
    if [[ "$OUTPUT_FORMAT" == "json" ]]; then
        output=$("${suite_script}" --json 2>&1) || true
    else
        output=$("${suite_script}" 2>&1) || true
    fi
    exit_code=$?

    end_time=$(get_time_ms)
    duration=$((end_time - start_time))

    SUITE_DURATIONS["${suite_name}"]=${duration}

    case $exit_code in
        0)
            SUITE_RESULTS["${suite_name}"]="PASS"
            echo -e "${GREEN}[PASSED]${NC} ${suite_name} (${duration}ms)"
            ;;
        *)
            SUITE_RESULTS["${suite_name}"]="FAIL"
            echo -e "${RED}[FAILED]${NC} ${suite_name} (${duration}ms)"
            ;;
    esac

    # Save output to file
    echo "$output" > "${OUTPUT_DIR}/${suite_name}-results.json"

    return $exit_code
}

# ── Run Suite in Background (Parallel) ─────────────────────────────────────
run_suite_async() {
    local suite_name="$1"
    local suite_script="$2"

    (
        run_suite "$suite_name" "$suite_script"
        echo "${suite_name}:$?" > "${OUTPUT_DIR}/.result-${suite_name}"
    ) &

    echo $!
}

# ── Aggregate Results ─────────────────────────────────────────────────────
aggregate_results() {
    local results_json="{"
    local first=true

    for suite_name in "${!SUITE_RESULTS[@]}"; do
        [[ "$first" == "false" ]] && results_json+=","
        first=false

        local result="${SUITE_RESULTS[$suite_name]}"
        local duration="${SUITE_DURATIONS[$suite_name]}"

        results_json+="\"${suite_name}\":{\"status\":\"${result}\",\"duration_ms\":${duration}}"
    done

    results_json+="}"
    echo "$results_json"
}

# ── Output Functions ───────────────────────────────────────────────────────
output_json() {
    local results_json
    results_json=$(aggregate_results)

    cat << JSON_EOF
{
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "server": {
    "host": "${A2A_SERVER_HOST:-localhost}",
    "port": ${A2A_SERVER_PORT:-8080}
  },
  "execution_mode": "$([ "$PARALLEL" = true ] && echo "parallel" || echo "sequential")",
  "suites": ${results_json},
  "summary": {
    "total_suites": ${#SUITE_RESULTS[@]},
    "passed": $(for r in "${SUITE_RESULTS[@]}"; do [[ "$r" == "PASS" ]] && echo 1; done | wc -l | tr -d ' '),
    "failed": $(for r in "${SUITE_RESULTS[@]}"; do [[ "$r" == "FAIL" ]] && echo 1; done | wc -l | tr -d ' ')
  },
  "status": $([[ "${SUITE_RESULTS[*]}" =~ FAIL ]] && echo "\"FAIL\"" || echo "\"PASS\"")
}
JSON_EOF
}

output_text() {
    echo ""
    echo "==========================================="
    echo "  YAWL A2A Validation Summary"
    echo "  $(date -u +'%Y-%m-%d %H:%M:%S UTC')"
    echo "==========================================="
    echo ""

    local passed=0
    local failed=0

    for suite_name in "${!SUITE_RESULTS[@]}"; do
        local result="${SUITE_RESULTS[$suite_name]}"
        local duration="${SUITE_DURATIONS[$suite_name]}"

        if [[ "$result" == "PASS" ]]; then
            echo -e "  ${GREEN}[PASS]${NC} ${suite_name} (${duration}ms)"
            ((passed++)) || true
        else
            echo -e "  ${RED}[FAIL]${NC} ${suite_name} (${duration}ms)"
            ((failed++)) || true
        fi
    done

    echo ""
    echo "Suites: ${#SUITE_RESULTS[@]} | Passed: ${passed} | Failed: ${failed}"
    echo ""

    if [[ $failed -eq 0 ]]; then
        echo -e "${GREEN}All A2A validation tests passed.${NC}"
        return 0
    else
        echo -e "${RED}${failed} A2A validation suite(s) failed.${NC}"
        return 1
    fi
}

# ── Check Prerequisites ───────────────────────────────────────────────────
check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check for jq
    if ! command -v jq &> /dev/null; then
        echo "[ERROR] jq is required for A2A validation tests" >&2
        echo "Install with: brew install jq (macOS) or apt install jq (Linux)" >&2
        exit 2
    fi

    # Check for curl
    if ! command -v curl &> /dev/null; then
        echo "[ERROR] curl is required for A2A validation tests" >&2
        exit 2
    fi

    # Source A2A common functions to check server
    source "${LIB_DIR}/a2a-common.sh" 2>/dev/null || {
        echo "[ERROR] A2A client library not found: ${LIB_DIR}/a2a-common.sh" >&2
        exit 2
    }

    # Check server availability
    if ! a2a_ping; then
        echo "[WARN] A2A server not running at ${A2A_SERVER_HOST:-localhost}:${A2A_SERVER_PORT:-8080}" >&2
        echo "[INFO] Some tests may fail. Start the server with: bash scripts/start-a2a-server.sh" >&2
    fi

    log_info "Prerequisites OK"
}

# ── Main Execution ────────────────────────────────────────────────────────
main() {
    echo ""
    echo "==========================================="
    echo "  YAWL A2A Validation Test Runner"
    echo "  $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    echo "==========================================="
    echo ""
    echo "Mode: $([ "$PARALLEL" = true ] && echo "Parallel" || echo "Sequential")"
    echo "Output: ${OUTPUT_DIR}"
    echo ""

    # Check prerequisites
    check_prerequisites

    echo ""
    echo "Running test suites..."
    echo ""

    if [[ "$PARALLEL" == true ]]; then
        # Parallel execution
        declare -A PIDS=()

        for suite_name in "${!TEST_SUITES[@]}"; do
            suite_script="${TEST_SUITES[$suite_name]}"
            if [[ -f "$suite_script" ]]; then
                pid=$(run_suite_async "$suite_name" "$suite_script")
                PIDS["${suite_name}"]="${pid}"
            else
                echo -e "${YELLOW}[SKIP]${NC} ${suite_name} (script not found)"
                SUITE_RESULTS["${suite_name}"]="SKIP"
            fi
        done

        # Wait for all processes
        for suite_name in "${!PIDS[@]}"; do
            wait "${PIDS[$suite_name]}" 2>/dev/null || true
        done

        # Collect results from files
        for suite_name in "${!TEST_SUITES[@]}"; do
            result_file="${OUTPUT_DIR}/.result-${suite_name}"
            if [[ -f "$result_file" ]]; then
                read -r name exit_code < <(cat "$result_file" | tr ':' ' ')
                if [[ "$exit_code" == "0" ]]; then
                    SUITE_RESULTS["${suite_name}"]="PASS"
                else
                    SUITE_RESULTS["${suite_name}"]="FAIL"
                fi
            fi
        done
    else
        # Sequential execution
        for suite_name in "${!TEST_SUITES[@]}"; do
            suite_script="${TEST_SUITES[$suite_name]}"
            if [[ -f "$suite_script" ]]; then
                run_suite "$suite_name" "$suite_script" || true
            else
                echo -e "${YELLOW}[SKIP]${NC} ${suite_name} (script not found)"
                SUITE_RESULTS["${suite_name}"]="SKIP"
            fi
        done
    fi

    # Output results
    if [[ "$OUTPUT_FORMAT" == "json" ]]; then
        output_json
    else
        output_text
    fi

    # Cleanup
    rm -f "${OUTPUT_DIR}"/.result-* 2>/dev/null || true

    # Exit with appropriate code
    for result in "${SUITE_RESULTS[@]}"; do
        [[ "$result" == "FAIL" ]] && exit 1
    done
    exit 0
}

main
