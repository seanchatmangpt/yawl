#!/bin/bash
# ==========================================================================
# run-benchmarks.sh — YAWL Integration Performance Benchmark Suite
# ==========================================================================
#
# Comprehensive benchmark execution script for YAWL integration testing.
# Supports multiple benchmark types, parallel execution, and detailed reporting.
#
# Usage:
#   bash scripts/run-benchmarks.sh [options]
#
# Options:
#   --help                Show this help message
#   --type=<type>         Benchmark type: all, a2a, mcp, zai, stress (default: all)
#   --forks=<N>           Number of JVM forks (default: 1)
#   --warmup=<N>          Warmup iterations (default: 3)
#   --iterations=<N>      Measurement iterations (default: 5)
#   --threads=<N>         Number of threads (default: CPU count)
#   --timeout=<seconds>   Benchmark timeout (default: 600)
#   --output=<dir>        Output directory (default: benchmark-results-<timestamp>)
#   --jvm-opts=<opts>     Additional JVM options
#   --compare=<file>      Compare with baseline results file
#   --report              Generate HTML report after benchmarks
#   --verbose             Enable verbose output
#   --skip-compile        Skip Maven compilation
#   --profile=<name>      Maven profile to use (default: agent-dx)
#
# Exit Codes:
#   0 - All benchmarks completed successfully
#   1 - Benchmark execution failed
#   2 - Invalid arguments or configuration
#   3 - Compilation failed
#   4 - Timeout exceeded
#
# Environment Variables:
#   BENCHMARK_JVM_OPTS     Additional JVM options
#   BENCHMARK_OFFLINE      Set to 1 for offline mode
#   BENCHMARK_FAIL_FAST    Set to 1 to fail on first error
#
# ==========================================================================

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Default values
BENCHMARK_TYPE="all"
FORKS=1
WARMUP_ITERATIONS=3
MEASUREMENT_ITERATIONS=5
THREADS=$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)
TIMEOUT=600
OUTPUT_DIR=""
JVM_OPTS=""
COMPARE_FILE=""
GENERATE_REPORT=false
VERBOSE=false
SKIP_COMPILE=false
MAVEN_PROFILE="agent-dx"
OFFLINE_MODE=false
FAIL_FAST=false

# Benchmark class mapping
declare -A BENCHMARK_CLASSES=(
    ["a2a"]="org.yawlfoundation.yawl.integration.benchmark.IntegrationBenchmarks"
    ["mcp"]="org.yawlfoundation.yawl.integration.benchmark.IntegrationBenchmarks"
    ["zai"]="org.yawlfoundation.yawl.integration.benchmark.IntegrationBenchmarks"
    ["stress"]="org.yawlfoundation.yawl.integration.benchmark.StressTestBenchmarks"
    ["all"]="org.yawlfoundation.yawl.integration.benchmark.BenchmarkSuite"
)

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Logging functions
log_info() { echo -e "${BLUE}[INFO]${NC} $1" >&2; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1" >&2; }
log_error() { echo -e "${RED}[ERROR]${NC} $1" >&2; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1" >&2; }
log_benchmark() { echo -e "${CYAN}[BENCHMARK]${NC} $1" >&2; }

# Show help message
show_help() {
    cat << 'HELP_EOF'
YAWL Integration Performance Benchmark Suite
=============================================

Comprehensive benchmark execution script for YAWL integration testing.
Supports A2A, MCP, Z.ai, and stress test benchmarks.

Usage:
  bash scripts/run-benchmarks.sh [options]

Options:
  --help                Show this help message
  --type=<type>         Benchmark type: all, a2a, mcp, zai, stress (default: all)
  --forks=<N>           Number of JVM forks (default: 1)
  --warmup=<N>          Warmup iterations (default: 3)
  --iterations=<N>      Measurement iterations (default: 5)
  --threads=<N>         Number of threads (default: CPU count)
  --timeout=<seconds>   Benchmark timeout (default: 600)
  --output=<dir>        Output directory (default: benchmark-results-<timestamp>)
  --jvm-opts=<opts>     Additional JVM options
  --compare=<file>      Compare with baseline results file
  --report              Generate HTML report after benchmarks
  --verbose             Enable verbose output
  --skip-compile        Skip Maven compilation
  --profile=<name>      Maven profile to use (default: agent-dx)

Performance Targets:
  A2A:   >1000 req/s throughput, p95 latency <200ms
  MCP:   Tool execution p95 latency <100ms
  Z.ai:  Fast models p95 latency <100ms

Exit Codes:
  0 - All benchmarks completed successfully
  1 - Benchmark execution failed
  2 - Invalid arguments or configuration
  3 - Compilation failed
  4 - Timeout exceeded
HELP_EOF
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --help) show_help; exit 0 ;;
            --type=*) BENCHMARK_TYPE="${1#*=}" ;;
            --forks=*) FORKS="${1#*=}" ;;
            --warmup=*) WARMUP_ITERATIONS="${1#*=}" ;;
            --iterations=*) MEASUREMENT_ITERATIONS="${1#*=}" ;;
            --threads=*) THREADS="${1#*=}" ;;
            --timeout=*) TIMEOUT="${1#*=}" ;;
            --output=*) OUTPUT_DIR="${1#*=}" ;;
            --jvm-opts=*) JVM_OPTS="${1#*=}" ;;
            --compare=*) COMPARE_FILE="${1#*=}" ;;
            --report) GENERATE_REPORT=true ;;
            --verbose) VERBOSE=true ;;
            --skip-compile) SKIP_COMPILE=true ;;
            --profile=*) MAVEN_PROFILE="${1#*=}" ;;
            *) log_error "Unknown option: $1"; show_help; exit 2 ;;
        esac
        shift
    done

    # Validate benchmark type
    if [[ ! -v "BENCHMARK_CLASSES[$BENCHMARK_TYPE]" ]]; then
        log_error "Invalid benchmark type: $BENCHMARK_TYPE"
        log_info "Valid types: ${!BENCHMARK_CLASSES[*]}"
        exit 2
    fi

    # Set default output directory
    [[ -z "$OUTPUT_DIR" ]] && OUTPUT_DIR="${PROJECT_ROOT}/benchmark-results-${TIMESTAMP}"

    # Check environment variables
    [[ "${BENCHMARK_OFFLINE:-0}" == "1" ]] && OFFLINE_MODE=true
    [[ "${BENCHMARK_FAIL_FAST:-0}" == "1" ]] && FAIL_FAST=true
    [[ -n "${BENCHMARK_JVM_OPTS:-}" ]] && JVM_OPTS="${JVM_OPTS} ${BENCHMARK_JVM_OPTS}"
}

# Validate environment
validate_environment() {
    log_info "Validating environment..."
    command -v java &> /dev/null || { log_error "Java is not installed"; exit 2; }
    command -v mvn &> /dev/null || { log_error "Maven is not installed"; exit 2; }
    
    cd "$PROJECT_ROOT"
    [[ ! -f "pom.xml" ]] && { log_error "Not in YAWL project root directory"; exit 2; }
    mkdir -p "$OUTPUT_DIR"
    log_success "Environment validation passed"
}

# Compile project
compile_project() {
    [[ "$SKIP_COMPILE" == true ]] && { log_info "Skipping compilation"; return 0; }
    log_info "Compiling project with profile: $MAVEN_PROFILE"
    
    local mvn_args="-T 1.5C compile test-compile -P $MAVEN_PROFILE -q"
    [[ "$OFFLINE_MODE" == true ]] && mvn_args="$mvn_args -o"
    [[ "$VERBOSE" == true ]] && mvn_args="${mvn_args/-q/}"
    
    cd "$PROJECT_ROOT"
    local compile_log="${OUTPUT_DIR}/compile.log"
    
    if ! mvn $mvn_args > "$compile_log" 2>&1; then
        log_error "Compilation failed. See $compile_log for details."
        [[ "$VERBOSE" == true ]] && cat "$compile_log" || tail -30 "$compile_log"
        exit 3
    fi
    log_success "Project compiled successfully"
}

# Run JMH benchmark
run_jmh_benchmark() {
    local pattern="$1"
    local results_file="$2"
    cd "$PROJECT_ROOT"
    
    mvn exec:java $
        -Dexec.mainClass="org.openjdk.jmh.Main" $
        -Dexec.classpathScope=test $
        -Dexec.args="$pattern -f $FORKS -wi $WARMUP_ITERATIONS -i $MEASUREMENT_ITERATIONS -t $THREADS -rff $results_file -rf json" $
        -q 2>&1
}

# Run benchmarks
run_benchmarks() {
    log_benchmark "Starting benchmark execution..."
    log_benchmark "Type: $BENCHMARK_TYPE, Forks: $FORKS, Threads: $THREADS"
    echo

    cd "$PROJECT_ROOT"
    local results_file="${OUTPUT_DIR}/benchmark-results-${TIMESTAMP}.json"
    local log_file="${OUTPUT_DIR}/benchmark-output-${TIMESTAMP}.log"
    local start_time=$(date +%s)

    local benchmark_pattern=""
    case "$BENCHMARK_TYPE" in
        a2a) benchmark_pattern="IntegrationBenchmarks.a2a.*" ;;
        mcp) benchmark_pattern="IntegrationBenchmarks.mcp.*" ;;
        zai) benchmark_pattern="IntegrationBenchmarks.zai.*" ;;
        stress) benchmark_pattern="StressTestBenchmarks.*" ;;
        all) benchmark_pattern=".*" ;;
    esac

    log_info "Running benchmarks with pattern: $benchmark_pattern"
    run_jmh_benchmark "$benchmark_pattern" "$results_file" >> "$log_file" 2>&1
    local exit_code=$?
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    if [[ $exit_code -eq 0 ]]; then
        log_success "Benchmarks completed in ${duration}s"
        log_info "Results saved to: $results_file"
    else
        log_error "Benchmarks failed after ${duration}s"
        [[ "$VERBOSE" == true ]] && [[ -f "$log_file" ]] && tail -50 "$log_file"
        return 1
    fi

    [[ -n "$COMPARE_FILE" ]] && [[ -f "$COMPARE_FILE" ]] && compare_with_baseline "$results_file" "$COMPARE_FILE"
    [[ "$GENERATE_REPORT" == true ]] && generate_html_report "$results_file"
    return $exit_code
}

# Compare with baseline
compare_with_baseline() {
    local current_file="$1"
    local baseline_file="$2"
    log_info "Comparing with baseline: $baseline_file"
    local comparison_file="${OUTPUT_DIR}/comparison-${TIMESTAMP}.json"
    
    java -cp "target/test-classes:target/classes" $
        org.yawlfoundation.yawl.integration.benchmark.PerformanceRegressionDetector $
        "$baseline_file" "$current_file" "$comparison_file" 2>/dev/null || {
        log_warn "Could not run performance comparison"
        return 0
    }
    [[ -f "$comparison_file" ]] && log_success "Comparison saved to: $comparison_file"
}

# Generate HTML report
generate_html_report() {
    local results_file="$1"
    local report_file="${OUTPUT_DIR}/benchmark-report-${TIMESTAMP}.html"
    log_info "Generating HTML report..."
    
    local total_benchmarks=0 avg_latency=0 max_latency=0
    command -v jq &> /dev/null && [[ -f "$results_file" ]] && {
        total_benchmarks=$(jq 'length' "$results_file" 2>/dev/null || echo "0")
        avg_latency=$(jq '[.[].primaryMetric.score] | add / length' "$results_file" 2>/dev/null || echo "0")
        max_latency=$(jq '[.[].primaryMetric.score] | max' "$results_file" 2>/dev/null || echo "0")
    }

    cat > "$report_file" << HTMLEOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>YAWL Benchmark Report - $TIMESTAMP</title>
    <style>
        body { font-family: -apple-system, sans-serif; margin: 20px; background: #f5f5f5; }
        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; border-radius: 10px; margin-bottom: 20px; }
        .stat-card { background: white; padding: 20px; border-radius: 10px; margin: 10px 0; }
        .section { background: white; padding: 20px; border-radius: 10px; margin: 20px 0; }
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; }
    </style>
</head>
<body>
    <div class="header"><h1>YAWL Benchmark Report</h1><div>Generated: $(date)</div></div>
    <div class="stat-card"><h3>Total Benchmarks</h3><div style="font-size:32px">$total_benchmarks</div></div>
    <div class="stat-card"><h3>Average Latency</h3><div style="font-size:32px">${avg_latency}ms</div></div>
    <div class="stat-card"><h3>Max Latency</h3><div style="font-size:32px">${max_latency}ms</div></div>
    <div class="section"><h2>Configuration</h2><ul>
        <li>Benchmark Type: $BENCHMARK_TYPE</li>
        <li>JVM Forks: $FORKS</li>
        <li>Threads: $THREADS</li>
        <li>Maven Profile: $MAVEN_PROFILE</li>
    </ul></div>
</body>
</html>
HTMLEOF
    log_success "HTML report generated: $report_file"
}

# Display final summary
display_summary() {
    echo
    echo "========================================"
    echo "Benchmark Execution Summary"
    echo "========================================"
    echo "  Type:           $BENCHMARK_TYPE"
    echo "  Output Dir:     $OUTPUT_DIR"
    echo "  Profile:        $MAVEN_PROFILE"
    echo "  Threads:        $THREADS"
    echo "  Forks:          $FORKS"
    echo "========================================"
}

# Cleanup function
cleanup() {
    local exit_code=$?
    jobs -p | xargs -r kill 2>/dev/null || true
    exit $exit_code
}

# Main execution
main() {
    trap cleanup EXIT
    parse_args "$@"
    
    echo
    echo "=== YAWL Integration Performance Benchmark Suite ==="
    echo "Timestamp: $(date)"
    echo "Java: $(java -version 2>&1 | head -1)"
    echo

    validate_environment
    compile_project
    
    if run_benchmarks; then
        display_summary
        log_success "All benchmarks completed successfully!"
        exit 0
    else
        display_summary
        log_error "Benchmark execution failed"
        exit 1
    fi
}

main "$@"
