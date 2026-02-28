#!/bin/bash

##############################################################################
# YAWL Phase 4: Build Metrics Collection Script
# Purpose: Collect comprehensive build performance metrics for both sequential
#          and parallel execution modes
#
# Usage: bash scripts/collect-build-metrics.sh [options]
# Options:
#   --runs N          Number of runs per configuration (default: 5)
#   --fast            Quick test (2 runs instead of 5)
#   --sequential-only Only collect sequential metrics
#   --parallel-only   Only collect parallel metrics
#   --output FILE     Output JSON file (default: .claude/metrics/build-metrics-{date}.json)
#   --dry-run         Show what would be executed without running
#   --verbose         Print detailed timing information
#
# Example:
#   bash scripts/collect-build-metrics.sh --runs 5 --verbose
#   bash scripts/collect-build-metrics.sh --fast --output /tmp/metrics.json
##############################################################################

set -euo pipefail

# ============================================================================
# CONFIGURATION
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
METRICS_DIR="${PROJECT_ROOT}/.claude/metrics"
RUNS_COUNT=5
FAST_MODE=false
SEQUENTIAL_ONLY=false
PARALLEL_ONLY=false
DRY_RUN=false
VERBOSE=false
OUTPUT_FILE=""

# Create metrics directory if it doesn't exist
mkdir -p "${METRICS_DIR}"

# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

print_info() {
    echo "[INFO] $*"
}

print_success() {
    echo "[SUCCESS] $*"
}

print_warn() {
    echo "[WARN] $*"
}

print_error() {
    echo "[ERROR] $*" >&2
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --runs)
                RUNS_COUNT="$2"
                shift 2
                ;;
            --fast)
                FAST_MODE=true
                RUNS_COUNT=2
                shift
                ;;
            --sequential-only)
                SEQUENTIAL_ONLY=true
                shift
                ;;
            --parallel-only)
                PARALLEL_ONLY=true
                shift
                ;;
            --output)
                OUTPUT_FILE="$2"
                shift 2
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --verbose)
                VERBOSE=true
                shift
                ;;
            *)
                print_error "Unknown option: $1"
                print_usage
                exit 1
                ;;
        esac
    done
}

print_usage() {
    cat << 'EOF'
Usage: bash scripts/collect-build-metrics.sh [options]

Options:
  --runs N              Number of runs per configuration (default: 5)
  --fast                Quick test (2 runs instead of 5)
  --sequential-only     Only collect sequential metrics
  --parallel-only       Only collect parallel metrics
  --output FILE         Output JSON file (default: .claude/metrics/build-metrics-{date}.json)
  --dry-run             Show what would be executed without running
  --verbose             Print detailed timing information

Examples:
  bash scripts/collect-build-metrics.sh --runs 5 --verbose
  bash scripts/collect-build-metrics.sh --fast
  bash scripts/collect-build-metrics.sh --sequential-only --runs 3
EOF
}

# Get system information
get_system_info() {
    local cpu_count=1
    local memory_gb=0

    if [[ -f /proc/cpuinfo ]]; then
        cpu_count=$(grep -c "^processor" /proc/cpuinfo)
    elif command -v sysctl &> /dev/null; then
        cpu_count=$(sysctl -n hw.ncpu 2>/dev/null || echo 1)
    fi

    if [[ -f /proc/meminfo ]]; then
        memory_gb=$(awk '/MemTotal/ {printf "%.1f", $2/1024/1024}' /proc/meminfo)
    elif command -v sysctl &> /dev/null; then
        memory_gb=$(sysctl -n hw.memsize 2>/dev/null | awk '{printf "%.1f", $1/1024/1024/1024}' || echo "0")
    fi

    echo "{\"cpu_cores\": $cpu_count, \"memory_gb\": $memory_gb}"
}

# Parse Maven build log for timing information
parse_build_timing() {
    local log_file="$1"
    local build_time=0
    local unit_test_time=0
    local integration_test_time=0

    # Extract total time from Maven output
    if grep -q "BUILD SUCCESS" "${log_file}"; then
        build_time=$(grep "BUILD SUCCESS" "${log_file}" | head -1 | awk '{for(i=1;i<=NF;i++) if($i ~ /[0-9]+\.[0-9]+s/) print $(i)}' | sed 's/s//' | tail -1 || echo "0")
    fi

    # Extract test counts from Surefire/Failsafe reports
    if grep -q "Tests run:" "${log_file}"; then
        unit_test_time=$(grep "Tests run:" "${log_file}" | head -1 | awk '{for(i=1;i<=NF;i++) if($i ~ /[0-9]+\.[0-9]+s/) print $(i)}' | sed 's/s//' | tail -1 || echo "0")
    fi

    echo "{\"total\": $build_time, \"unit_tests\": $unit_test_time, \"integration_tests\": $integration_test_time}"
}

# Execute build and capture metrics
run_build() {
    local mode="$1"  # "sequential" or "parallel"
    local run_number="$2"
    local log_file="${METRICS_DIR}/.build-${mode}-run${run_number}.log"

    print_info "[$mode Run $run_number/$RUNS_COUNT] Starting build..."

    if [[ "$DRY_RUN" == "true" ]]; then
        print_info "[DRY_RUN] Would execute: mvn clean verify $([[ "$mode" == "parallel" ]] && echo "-P integration-parallel" || echo "")"
        echo "0.0"
        return 0
    fi

    cd "${PROJECT_ROOT}"

    # Record start time
    local start_time=$(date +%s%N)

    # Run the build
    if [[ "$mode" == "parallel" ]]; then
        timeout 600 mvn -q clean verify -P integration-parallel \
            -DskipITs=false \
            -Dorg.slf4j.simpleLogger.defaultLogLevel=warn \
            > "${log_file}" 2>&1 || true
    else
        timeout 600 mvn -q clean verify \
            -DskipITs=false \
            -Dorg.slf4j.simpleLogger.defaultLogLevel=warn \
            > "${log_file}" 2>&1 || true
    fi

    # Record end time
    local end_time=$(date +%s%N)
    local elapsed_ns=$((end_time - start_time))
    local elapsed_s=$(echo "scale=2; $elapsed_ns / 1000000000" | bc)

    if [[ "$VERBOSE" == "true" ]]; then
        print_info "  Total time: ${elapsed_s}s"
        if grep -q "BUILD SUCCESS" "${log_file}"; then
            print_success "  Build SUCCESS"
        else
            print_warn "  Build FAILED or SKIPPED (see log)"
        fi
    fi

    echo "$elapsed_s"
}

# Collect CPU and memory during build (requires monitoring in background)
monitor_build_resources() {
    local mode="$1"
    local run_number="$2"
    local pid="$3"
    local metrics_file="${METRICS_DIR}/.build-${mode}-run${run_number}-resources.json"

    local max_rss=0
    local max_cpu=0
    local samples=0

    # Monitor until process ends (simplified version)
    # Note: Full implementation would use proper resource monitoring
    if [[ -d "/proc/$pid" ]]; then
        if [[ -f "/proc/$pid/status" ]]; then
            max_rss=$(grep VmRSS "/proc/$pid/status" 2>/dev/null | awk '{print $2}' || echo "0")
        fi
    fi

    echo "{\"max_memory_kb\": $max_rss, \"samples\": $samples}"
}

# Collect build results
collect_run_metrics() {
    local mode="$1"
    local run_number="$2"
    local elapsed_time="$3"
    local log_file="${METRICS_DIR}/.build-${mode}-run${run_number}.log"

    # Check build success
    local build_success="false"
    if grep -q "BUILD SUCCESS" "${log_file}"; then
        build_success="true"
    fi

    # Parse test counts
    local unit_test_count=$(grep -o "Tests run: [0-9]*" "${log_file}" 2>/dev/null | head -1 | awk '{print $3}' || echo "0")
    local test_failures=$(grep -o "Failures: [0-9]*" "${log_file}" 2>/dev/null | head -1 | awk '{print $2}' || echo "0")

    echo "{\"run\": $run_number, \"elapsed_seconds\": $elapsed_time, \"success\": $build_success, \"unit_tests\": $unit_test_count, \"failures\": $test_failures}"
}

# Generate summary statistics
calculate_statistics() {
    local mode="$1"
    local times=("${@:2}")

    if [[ ${#times[@]} -eq 0 ]]; then
        echo '{"error": "No data collected"}'
        return 1
    fi

    local total=0
    local min=999999
    local max=0
    local count=0

    for time in "${times[@]}"; do
        if [[ -n "$time" && "$time" != "null" ]]; then
            total=$(echo "$total + $time" | bc)
            min=$(echo "if ($time < $min) $time else $min" | bc)
            max=$(echo "if ($time > $max) $time else $max" | bc)
            ((count++))
        fi
    done

    if [[ $count -eq 0 ]]; then
        echo '{"error": "No valid times collected"}'
        return 1
    fi

    local mean=$(echo "scale=2; $total / $count" | bc)
    local stddev=0

    # Simple stddev calculation
    local sum_sq=0
    for time in "${times[@]}"; do
        if [[ -n "$time" && "$time" != "null" ]]; then
            local diff=$(echo "$time - $mean" | bc)
            sum_sq=$(echo "$sum_sq + ($diff * $diff)" | bc)
        fi
    done

    if [[ $count -gt 1 ]]; then
        stddev=$(echo "scale=2; sqrt($sum_sq / ($count - 1))" | bc)
    fi

    cat << EOF
{
  "mode": "$mode",
  "count": $count,
  "mean_seconds": $mean,
  "stddev_seconds": $stddev,
  "min_seconds": $min,
  "max_seconds": $max,
  "median_seconds": $(echo "${times[*]}" | tr ' ' '\n' | sort -n | awk '{if(NR==int((1+NF)/2)) print}')
}
EOF
}

# ============================================================================
# MAIN EXECUTION
# ============================================================================

main() {
    parse_arguments "$@"

    print_info "YAWL Phase 4: Build Metrics Collection"
    print_info "========================================"
    print_info "Project: $PROJECT_ROOT"
    print_info "Runs per config: $RUNS_COUNT"
    print_info "Metrics dir: $METRICS_DIR"

    if [[ -z "$OUTPUT_FILE" ]]; then
        OUTPUT_FILE="${METRICS_DIR}/build-metrics-$(date +%Y%m%d-%H%M%S).json"
    fi

    print_info "Output file: $OUTPUT_FILE"

    # Get system info
    print_info ""
    print_info "System Information:"
    local sys_info=$(get_system_info)
    print_info "$sys_info"

    # Collect sequential metrics (unless --parallel-only)
    local seq_times=()
    if [[ "$PARALLEL_ONLY" != "true" ]]; then
        print_info ""
        print_info "Collecting SEQUENTIAL metrics ($RUNS_COUNT runs)..."

        for ((i = 1; i <= RUNS_COUNT; i++)); do
            elapsed=$(run_build "sequential" "$i")
            seq_times+=("$elapsed")
            sleep 2  # Cool-down between runs
        done

        print_success "Sequential collection complete"
    fi

    # Collect parallel metrics (unless --sequential-only)
    local par_times=()
    if [[ "$SEQUENTIAL_ONLY" != "true" ]]; then
        print_info ""
        print_info "Collecting PARALLEL metrics ($RUNS_COUNT runs)..."

        for ((i = 1; i <= RUNS_COUNT; i++)); do
            elapsed=$(run_build "parallel" "$i")
            par_times+=("$elapsed")
            sleep 2  # Cool-down between runs
        done

        print_success "Parallel collection complete"
    fi

    # Calculate statistics
    print_info ""
    print_info "Calculating statistics..."

    local seq_stats="{}"
    local par_stats="{}"

    if [[ ${#seq_times[@]} -gt 0 ]]; then
        seq_stats=$(calculate_statistics "sequential" "${seq_times[@]}")
    fi

    if [[ ${#par_times[@]} -gt 0 ]]; then
        par_stats=$(calculate_statistics "parallel" "${par_times[@]}")
    fi

    # Calculate speedup
    local speedup="null"
    local improvement_pct="null"

    if [[ ${#seq_times[@]} -gt 0 && ${#par_times[@]} -gt 0 ]]; then
        local seq_mean=$(echo "$seq_stats" | grep -o '"mean_seconds": [0-9.]*' | awk -F: '{print $2}' | tr -d ' ')
        local par_mean=$(echo "$par_stats" | grep -o '"mean_seconds": [0-9.]*' | awk -F: '{print $2}' | tr -d ' ')

        if (( $(echo "$par_mean > 0" | bc -l) )); then
            speedup=$(echo "scale=2; $seq_mean / $par_mean" | bc)
            improvement_pct=$(echo "scale=1; (($seq_mean - $par_mean) / $seq_mean) * 100" | bc)
        fi
    fi

    # Write output JSON
    cat > "${OUTPUT_FILE}" << EOF
{
  "phase": "Phase 4 - Build Metrics Collection",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "collection_date": "$(date +%Y-%m-%d)",
  "system_info": $sys_info,
  "runs_per_config": $RUNS_COUNT,
  "sequential": $seq_stats,
  "parallel": $par_stats,
  "speedup_factor": $speedup,
  "improvement_percentage": $improvement_pct,
  "confidence_level": 0.95,
  "notes": "Phase 3 baseline: 1.4× speedup (28% improvement). Phase 4 validates consistency over time."
}
EOF

    print_success ""
    print_success "Metrics collection complete!"
    print_success "Output: ${OUTPUT_FILE}"
    print_success ""

    # Print summary
    if [[ -f "${OUTPUT_FILE}" ]]; then
        print_info "Summary:"
        print_info "========="
        if [[ "$VERBOSE" == "true" ]]; then
            cat "${OUTPUT_FILE}"
        else
            print_info "Sequential mean: $(echo "$seq_stats" | grep -o '"mean_seconds": [0-9.]*' | awk -F: '{print $2}')s"
            print_info "Parallel mean:   $(echo "$par_stats" | grep -o '"mean_seconds": [0-9.]*' | awk -F: '{print $2}')s"
            print_info "Speedup:         ${speedup}x"
            print_info "Improvement:     ${improvement_pct}%"
        fi
    fi
}

# Run main
main "$@"
