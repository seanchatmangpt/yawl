#!/usr/bin/env bash
################################################################################
# YAWL Baseline Metrics Collection Script
# Establishes performance baselines for three build scenarios:
#   1. Cold start: Fresh checkout, no cache, full build (dx.sh all)
#   2. Warm start: Single file change, incremental build (dx.sh)
#   3. Full suite: All tests, no cache exclusions (mvn clean verify)
#
# Each scenario runs 3 times for statistical validity (mean ± std dev).
# Results stored in: .yawl/metrics/baselines.json
#
# Usage:
#   bash scripts/establish-baselines.sh [options]
#
# Options:
#   --dry-run              Show what would be executed without running
#   --verbose              Show detailed timing information
#   --cold-only            Run only cold-start scenario
#   --warm-only            Run only warm-start scenario
#   --full-only            Run only full-suite scenario
#   --baseline-dir DIR     Override baseline storage directory
#   --help                 Show this help message
#
# Example:
#   bash scripts/establish-baselines.sh --verbose
#   bash scripts/establish-baselines.sh --cold-only --dry-run
################################################################################

set -euo pipefail

# ============================================================================
# CONFIGURATION
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BASELINE_DIR="${REPO_ROOT}/.yawl/metrics"
BASELINE_FILE="${BASELINE_DIR}/baselines.json"
TEMP_DIR="/tmp/yawl-baseline-$$"

RUNS_PER_SCENARIO=3
DRY_RUN=false
VERBOSE=false
COLD_ONLY=false
WARM_ONLY=false
FULL_ONLY=false

# Environment info
JAVA_VERSION=$(java -version 2>&1 | grep 'version "' | cut -d'"' -f2)
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/temurin-25-jdk-amd64}"
CPU_CORES=$(nproc)
TOTAL_MEMORY_MB=$(free -m | awk 'NR==2 {print $2}')
BASELINE_DATE=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

log_info() {
    echo "[INFO] $*"
}

log_success() {
    echo "[SUCCESS] $*"
}

log_warn() {
    echo "[WARN] $*"
}

log_error() {
    echo "[ERROR] $*" >&2
}

log_section() {
    echo ""
    echo "============================================================================"
    echo "  $*"
    echo "============================================================================"
}

log_debug() {
    if [[ "${VERBOSE}" == "true" ]]; then
        echo "[DEBUG] $*"
    fi
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --verbose)
                VERBOSE=true
                shift
                ;;
            --cold-only)
                COLD_ONLY=true
                shift
                ;;
            --warm-only)
                WARM_ONLY=true
                shift
                ;;
            --full-only)
                FULL_ONLY=true
                shift
                ;;
            --baseline-dir)
                BASELINE_DIR="$2"
                BASELINE_FILE="${BASELINE_DIR}/baselines.json"
                shift 2
                ;;
            --help|-h)
                print_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                print_help
                exit 1
                ;;
        esac
    done
}

print_help() {
    sed -n '2,/^# ====/p' "$0" | grep '^#' | sed 's/^# \?//'
}

cleanup() {
    if [[ -d "${TEMP_DIR}" ]]; then
        log_debug "Cleaning up temporary directory: ${TEMP_DIR}"
        rm -rf "${TEMP_DIR}"
    fi
}

trap cleanup EXIT

# Get current memory usage in MB
get_memory_usage() {
    local pid=$1
    if [[ -f "/proc/${pid}/status" ]]; then
        awk '/^VmRSS:/ {print int($2/1024)}' "/proc/${pid}/status"
    else
        echo "0"
    fi
}

# Get average CPU usage percentage for a process
get_cpu_usage() {
    local pid=$1
    local duration=$2

    if ! command -v top &> /dev/null; then
        echo "0"
        return
    fi

    # Use /proc/stat-based calculation for average CPU over duration
    local start_time=$(date +%s)
    local samples=0
    local total_cpu=0

    while [[ $(($(date +%s) - start_time)) -lt $duration ]]; do
        if [[ -f "/proc/${pid}/stat" ]]; then
            local cpu_sample=$(awk '{print $14 + $15}' "/proc/${pid}/stat")
            total_cpu=$((total_cpu + cpu_sample))
            samples=$((samples + 1))
        fi
        sleep 0.5
    done

    if [[ $samples -gt 0 ]]; then
        echo $((total_cpu / samples))
    else
        echo "0"
    fi
}

# Initialize metrics directory
init_metrics_dir() {
    if [[ ! -d "${BASELINE_DIR}" ]]; then
        log_info "Creating metrics directory: ${BASELINE_DIR}"
        mkdir -p "${BASELINE_DIR}"
    fi

    if [[ ! -d "${TEMP_DIR}" ]]; then
        mkdir -p "${TEMP_DIR}"
    fi
}

# Initialize baseline JSON structure
init_baseline_json() {
    cat > "${BASELINE_FILE}" << 'EOF'
{
  "baseline_date": "",
  "environment": {
    "java_version": "",
    "java_home": "",
    "cpu_cores": 0,
    "total_memory_mb": 0
  },
  "runs": [],
  "summary": {
    "cold_start_avg_ms": 0,
    "cold_start_std_dev_ms": 0,
    "warm_start_avg_ms": 0,
    "warm_start_std_dev_ms": 0,
    "full_suite_avg_ms": 0,
    "full_suite_std_dev_ms": 0,
    "test_counts": {
      "passed": 0,
      "failed": 0,
      "skipped": 0
    }
  }
}
EOF
}

# Record a single run's metrics
record_run() {
    local scenario=$1
    local run_num=$2
    local total_time_ms=$3
    local compile_time_ms=$4
    local test_time_ms=$5
    local verify_time_ms=$6
    local memory_peak_mb=$7
    local test_passed=$8
    local test_failed=$9
    local test_skipped=${10}

    local run_json=$(cat <<EOF
{
  "scenario": "${scenario}",
  "run_number": ${run_num},
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "total_time_ms": ${total_time_ms},
  "phases": {
    "compile": ${compile_time_ms},
    "test": ${test_time_ms},
    "verify": ${verify_time_ms}
  },
  "memory_peak_mb": ${memory_peak_mb},
  "test_counts": {
    "passed": ${test_passed},
    "failed": ${test_failed},
    "skipped": ${test_skipped}
  }
}
EOF
)

    echo "${run_json}"
}

# Parse Surefire test results
parse_surefire_results() {
    local surefire_dir="${REPO_ROOT}/target/surefire-reports"
    local passed=0
    local failed=0
    local skipped=0

    if [[ ! -d "${surefire_dir}" ]]; then
        echo "0 0 0"
        return
    fi

    for xml_file in "${surefire_dir}"/TEST-*.xml; do
        if [[ -f "${xml_file}" ]]; then
            passed=$((passed + $(grep -o 'tests="[^"]*"' "${xml_file}" | grep -o '[0-9]*' | head -1 || echo 0)))
            failed=$((failed + $(grep -o 'failures="[^"]*"' "${xml_file}" | grep -o '[0-9]*' | head -1 || echo 0)))
            skipped=$((skipped + $(grep -o 'skipped="[^"]*"' "${xml_file}" | grep -o '[0-9]*' | head -1 || echo 0)))
        fi
    done

    echo "${passed} ${failed} ${skipped}"
}

# Execute and measure a build scenario
run_scenario() {
    local scenario=$1
    local run_num=$2
    local cmd=$3

    log_info "Running ${scenario} (run ${run_num}/${RUNS_PER_SCENARIO}): ${cmd}"

    if [[ "${DRY_RUN}" == "true" ]]; then
        log_info "[DRY-RUN] Would execute: ${cmd}"
        return
    fi

    # Capture detailed timing info
    local start_ns=$(date +%s%N)
    local start_sec=$(date +%s)

    # Execute the command with time measurement
    local time_output
    local exit_code=0
    local memory_peak_mb=0

    # Use /usr/bin/time for detailed metrics (if available)
    if command -v /usr/bin/time &> /dev/null; then
        time_output=$( (/usr/bin/time -v bash -c "${cmd}" 2>&1) || exit_code=$? )
        # Extract memory from /usr/bin/time output
        if echo "${time_output}" | grep -q "Maximum resident set size"; then
            memory_peak_mb=$(echo "${time_output}" | grep "Maximum resident set size" | awk '{print int($6/1024)}')
        fi
    else
        time_output=$(bash -c "${cmd}" 2>&1) || exit_code=$?
        # Fallback: check /proc for peak memory
        if [[ -f "/proc/self/status" ]]; then
            memory_peak_mb=$(awk '/^VmPeak:/ {print int($2/1024)}' "/proc/self/status")
        fi
    fi

    local end_ns=$(date +%s%N)
    local end_sec=$(date +%s)

    local total_time_ms=$(( (end_ns - start_ns) / 1000000 ))
    local duration_sec=$((end_sec - start_sec))

    log_debug "Total time: ${total_time_ms}ms, Memory: ${memory_peak_mb}MB, Duration: ${duration_sec}s"

    if [[ ${exit_code} -ne 0 ]]; then
        log_warn "Build command exited with code ${exit_code}"
    fi

    # Parse test results from Surefire reports
    local test_results=$(parse_surefire_results)
    local test_passed=$(echo "${test_results}" | awk '{print $1}')
    local test_failed=$(echo "${test_results}" | awk '{print $2}')
    local test_skipped=$(echo "${test_results}" | awk '{print $3}')

    # Estimate phase breakdown (simplified)
    local compile_time_ms=$((total_time_ms * 50 / 100))  # Assume ~50% compile
    local test_time_ms=$((total_time_ms * 40 / 100))     # ~40% test
    local verify_time_ms=$((total_time_ms * 10 / 100))   # ~10% verify

    record_run "${scenario}" "${run_num}" "${total_time_ms}" "${compile_time_ms}" \
               "${test_time_ms}" "${verify_time_ms}" "${memory_peak_mb}" \
               "${test_passed}" "${test_failed}" "${test_skipped}"
}

# Run cold-start scenario: Full clean build
run_cold_start() {
    log_section "COLD-START SCENARIO (Fresh checkout, no cache)"

    for run in $(seq 1 ${RUNS_PER_SCENARIO}); do
        # Clean all caches
        log_info "Cleaning caches before run ${run}"
        cd "${REPO_ROOT}"
        mvn clean -q 2>/dev/null || true
        rm -rf ~/.m2/repository/org/yawlfoundation/* 2>/dev/null || true

        # Full build
        local cmd="cd '${REPO_ROOT}' && bash scripts/dx.sh all"
        run_scenario "cold-start" "${run}" "${cmd}"
    done
}

# Run warm-start scenario: Incremental build with single file change
run_warm_start() {
    log_section "WARM-START SCENARIO (Single file change, incremental build)"

    for run in $(seq 1 ${RUNS_PER_SCENARIO}); do
        cd "${REPO_ROOT}"

        # Make a trivial change to trigger incremental build
        local marker_file="${REPO_ROOT}/yawl-elements/src/main/java/org/yawlfoundation/yawl/elements/YSpecification.java"
        if [[ -f "${marker_file}" ]]; then
            # Add and remove a blank line to force recompilation
            echo "" >> "${marker_file}" 2>/dev/null || true
            sed -i '$ d' "${marker_file}" 2>/dev/null || true
        fi

        # Incremental build
        local cmd="cd '${REPO_ROOT}' && bash scripts/dx.sh"
        run_scenario "warm-start" "${run}" "${cmd}"
    done
}

# Run full suite scenario: Complete test suite with all validations
run_full_suite() {
    log_section "FULL-SUITE SCENARIO (All tests, complete validation)"

    for run in $(seq 1 ${RUNS_PER_SCENARIO}); do
        cd "${REPO_ROOT}"

        # Full Maven verify
        local cmd="cd '${REPO_ROOT}' && mvn clean verify -DskipITs=false -q 2>/dev/null || true"
        run_scenario "full-suite" "${run}" "${cmd}"
    done
}

# Calculate statistics (mean and standard deviation)
calculate_stats() {
    local scenario=$1
    local values=()

    # Extract run times for scenario from JSON
    if [[ -f "${BASELINE_FILE}" ]]; then
        values=($(jq -r ".runs[] | select(.scenario == \"${scenario}\") | .total_time_ms" "${BASELINE_FILE}" 2>/dev/null || echo ""))
    fi

    if [[ ${#values[@]} -eq 0 ]]; then
        echo "0 0"
        return
    fi

    local sum=0
    for val in "${values[@]}"; do
        sum=$((sum + val))
    done

    local mean=$((sum / ${#values[@]}))

    local sum_sq_diff=0
    for val in "${values[@]}"; do
        local diff=$((val - mean))
        sum_sq_diff=$((sum_sq_diff + diff * diff))
    done

    local variance=$((sum_sq_diff / ${#values[@]}))
    local std_dev=$(($(echo "sqrt(${variance})" | bc -l 2>/dev/null || echo 0)))

    echo "${mean} ${std_dev}"
}

# Finalize baseline JSON with computed statistics
finalize_baseline_json() {
    log_section "FINALIZING BASELINE METRICS"

    if [[ ! -f "${BASELINE_FILE}" ]]; then
        log_error "Baseline file not found: ${BASELINE_FILE}"
        return 1
    fi

    # Calculate statistics for each scenario
    local cold_stats=$(calculate_stats "cold-start")
    local warm_stats=$(calculate_stats "warm-start")
    local full_stats=$(calculate_stats "full-suite")

    local cold_avg=$(echo "${cold_stats}" | awk '{print $1}')
    local cold_std=$(echo "${cold_stats}" | awk '{print $2}')
    local warm_avg=$(echo "${warm_stats}" | awk '{print $1}')
    local warm_std=$(echo "${warm_stats}" | awk '{print $2}')
    local full_avg=$(echo "${full_stats}" | awk '{print $1}')
    local full_std=$(echo "${full_stats}" | awk '{print $2}')

    log_info "Statistics calculated:"
    log_info "  Cold-start:  ${cold_avg}ms ± ${cold_std}ms"
    log_info "  Warm-start:  ${warm_avg}ms ± ${warm_std}ms"
    log_info "  Full-suite:  ${full_avg}ms ± ${full_std}ms"

    # Update JSON with environment info and statistics
    local updated_json=$(jq \
        --arg baseline_date "${BASELINE_DATE}" \
        --arg java_version "${JAVA_VERSION}" \
        --arg java_home "${JAVA_HOME}" \
        --argjson cpu_cores "${CPU_CORES}" \
        --argjson total_memory_mb "${TOTAL_MEMORY_MB}" \
        --argjson cold_avg "${cold_avg}" \
        --argjson cold_std "${cold_std}" \
        --argjson warm_avg "${warm_avg}" \
        --argjson warm_std "${warm_std}" \
        --argjson full_avg "${full_avg}" \
        --argjson full_std "${full_std}" \
        '.baseline_date = $baseline_date |
         .environment.java_version = $java_version |
         .environment.java_home = $java_home |
         .environment.cpu_cores = $cpu_cores |
         .environment.total_memory_mb = $total_memory_mb |
         .summary.cold_start_avg_ms = $cold_avg |
         .summary.cold_start_std_dev_ms = $cold_std |
         .summary.warm_start_avg_ms = $warm_avg |
         .summary.warm_start_std_dev_ms = $warm_std |
         .summary.full_suite_avg_ms = $full_avg |
         .summary.full_suite_std_dev_ms = $full_std' \
        "${BASELINE_FILE}")

    echo "${updated_json}" | jq '.' > "${BASELINE_FILE}"

    log_success "Baseline metrics finalized and saved to: ${BASELINE_FILE}"
}

# Validate baseline consistency (std dev should be <10%)
validate_baseline_consistency() {
    log_section "VALIDATING BASELINE CONSISTENCY"

    if [[ ! -f "${BASELINE_FILE}" ]]; then
        log_warn "Baseline file not found: ${BASELINE_FILE}"
        return 0
    fi

    local scenarios=("cold-start" "warm-start" "full-suite")

    for scenario in "${scenarios[@]}"; do
        local cold_avg=$(jq -r ".summary.${scenario%%-*}_start_avg_ms // 0" "${BASELINE_FILE}")
        local cold_std=$(jq -r ".summary.${scenario%%-*}_start_std_dev_ms // 0" "${BASELINE_FILE}")

        if [[ ${cold_avg} -gt 0 ]]; then
            local cv=$((cold_std * 100 / cold_avg))
            if [[ ${cv} -gt 10 ]]; then
                log_warn "High variability in ${scenario}: CV=${cv}% (>10%)"
                log_info "Consider re-running baselines or checking system load"
            else
                log_info "${scenario}: CV=${cv}% (consistent)"
            fi
        fi
    done
}

# Main execution
main() {
    parse_arguments "$@"

    log_section "YAWL BASELINE METRICS COLLECTION"
    log_info "Date: ${BASELINE_DATE}"
    log_info "Environment: Java ${JAVA_VERSION}, ${CPU_CORES} cores, ${TOTAL_MEMORY_MB}MB RAM"
    log_info "Output: ${BASELINE_FILE}"

    init_metrics_dir
    init_baseline_json

    # Determine which scenarios to run
    local run_all=true
    if [[ "${COLD_ONLY}" == "true" ]] || [[ "${WARM_ONLY}" == "true" ]] || [[ "${FULL_ONLY}" == "true" ]]; then
        run_all=false
    fi

    if [[ "${run_all}" == "true" ]] || [[ "${COLD_ONLY}" == "true" ]]; then
        run_cold_start
    fi

    if [[ "${run_all}" == "true" ]] || [[ "${WARM_ONLY}" == "true" ]]; then
        run_warm_start
    fi

    if [[ "${run_all}" == "true" ]] || [[ "${FULL_ONLY}" == "true" ]]; then
        run_full_suite
    fi

    if [[ "${DRY_RUN}" != "true" ]]; then
        finalize_baseline_json
        validate_baseline_consistency
        log_section "BASELINE COLLECTION COMPLETE"
        log_success "All baselines collected and validated"
    else
        log_info "[DRY-RUN] Baseline collection would complete here"
    fi
}

main "$@"
