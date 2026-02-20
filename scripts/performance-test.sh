#!/usr/bin/env bash
# ==========================================================================
# performance-test.sh — Java 25 Performance Validation Framework
#
# Runs YAWL performance benchmarks with different JVM configurations
# to validate optimization improvements.
#
# Usage:
#   bash scripts/performance-test.sh                    # All configs
#   bash scripts/performance-test.sh baseline            # Baseline only
#   bash scripts/performance-test.sh optimized           # Optimized only
#   bash scripts/performance-test.sh compare             # Side-by-side
#
# Output:
#   - Throughput metrics (ops/sec)
#   - GC statistics
#   - Memory footprint
#   - Improvement percentages
# ==========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'  # No Color

# Configuration variants to test
CONFIGS=("baseline" "optimized" "zgc" "virtual-threads")
RESULTS_DIR="${REPO_ROOT}/target/performance-results"
mkdir -p "${RESULTS_DIR}"

print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Baseline configuration (no optimizations)
run_baseline() {
    print_header "Baseline Configuration (No Optimizations)"

    local jvm_opts=(
        "-Xms2g"
        "-Xmx8g"
        "-XX:+TieredCompilation"
        "-XX:TieredStopAtLevel=4"
        "-Dfile.encoding=UTF-8"
        "--enable-preview"
    )

    JAVA_TOOL_OPTIONS="${jvm_opts[*]}" \
        mvn test -q \
            -Dtest=WorkflowThroughputBenchmark \
            -DargLine="${jvm_opts[*]}" \
            2>&1 | tee "${RESULTS_DIR}/baseline.log"
}

# Optimized configuration (all Java 25 features)
run_optimized() {
    print_header "Optimized Configuration (Compact Headers + Field Ordering)"

    local jvm_opts=(
        "-Xms2g"
        "-Xmx8g"
        "-XX:+UseCompactObjectHeaders"
        "-XX:+TieredCompilation"
        "-XX:TieredStopAtLevel=4"
        "-XX:CompileThreshold=10000"
        "-Dfile.encoding=UTF-8"
        "--enable-preview"
    )

    JAVA_TOOL_OPTIONS="${jvm_opts[*]}" \
        mvn test -q \
            -Dtest=WorkflowThroughputBenchmark \
            -DargLine="${jvm_opts[*]}" \
            2>&1 | tee "${RESULTS_DIR}/optimized.log"
}

# ZGC configuration
run_zgc() {
    print_header "ZGC Configuration (Ultra-Low Latency)"

    local jvm_opts=(
        "-Xms2g"
        "-Xmx8g"
        "-XX:+UseCompactObjectHeaders"
        "-XX:+UseZGC"
        "-XX:+UseStringDeduplication"
        "-XX:InitiatingHeapOccupancyPercent=35"
        "-XX:+TieredCompilation"
        "-XX:TieredStopAtLevel=4"
        "-Dfile.encoding=UTF-8"
        "--enable-preview"
    )

    JAVA_TOOL_OPTIONS="${jvm_opts[*]}" \
        mvn test -q \
            -Dtest=WorkflowThroughputBenchmark \
            -DargLine="${jvm_opts[*]}" \
            2>&1 | tee "${RESULTS_DIR}/zgc.log"
}

# Virtual threads configuration
run_virtual_threads() {
    print_header "Virtual Threads Configuration"

    local jvm_opts=(
        "-Xms2g"
        "-Xmx8g"
        "-XX:+UseCompactObjectHeaders"
        "-XX:+UseZGC"
        "-Djdk.virtualThreadScheduler.parallelism=auto"
        "-Djdk.virtualThreadScheduler.maxPoolSize=256"
        "-XX:+TieredCompilation"
        "-XX:TieredStopAtLevel=4"
        "-Dfile.encoding=UTF-8"
        "--enable-preview"
    )

    JAVA_TOOL_OPTIONS="${jvm_opts[*]}" \
        mvn test -q \
            -Dtest=WorkflowThroughputBenchmark \
            -DargLine="${jvm_opts[*]}" \
            2>&1 | tee "${RESULTS_DIR}/virtual-threads.log"
}

# Extract metrics from benchmark output
extract_metrics() {
    local logfile=$1
    local metric=$2

    grep "${metric}:" "${logfile}" | tail -1 | grep -oE '[0-9,]+' | head -1 | tr -d ','
}

# Compare results and calculate improvements
compare_results() {
    print_header "Performance Comparison"

    if [[ ! -f "${RESULTS_DIR}/baseline.log" ]] || [[ ! -f "${RESULTS_DIR}/optimized.log" ]]; then
        print_error "Missing baseline or optimized results"
        return 1
    fi

    local baseline_identity=$(extract_metrics "${RESULTS_DIR}/baseline.log" "Identity Creation" || echo "0")
    local optimized_identity=$(extract_metrics "${RESULTS_DIR}/optimized.log" "Identity Creation" || echo "0")

    if [[ $baseline_identity -gt 0 ]] && [[ $optimized_identity -gt 0 ]]; then
        local improvement=$(( (optimized_identity - baseline_identity) * 100 / baseline_identity ))
        echo -e "\nIdentity Creation:"
        echo "  Baseline:  $baseline_identity ops/sec"
        echo "  Optimized: $optimized_identity ops/sec"
        echo -e "  ${GREEN}Improvement: +${improvement}%${NC}"
    fi
}

# Main execution
main() {
    local mode="${1:-all}"

    case "${mode}" in
        baseline)
            run_baseline
            ;;
        optimized)
            run_optimized
            ;;
        zgc)
            run_zgc
            ;;
        virtual-threads)
            run_virtual_threads
            ;;
        compare)
            compare_results
            ;;
        all)
            run_baseline
            run_optimized
            run_zgc
            run_virtual_threads
            compare_results
            ;;
        *)
            echo "Usage: $0 {all|baseline|optimized|zgc|virtual-threads|compare}"
            exit 1
            ;;
    esac

    print_header "Results saved to: ${RESULTS_DIR}"
}

main "$@"
