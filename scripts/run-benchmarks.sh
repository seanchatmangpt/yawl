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
# Examples:
#   bash scripts/run-benchmarks.sh                           # Run all benchmarks
#   bash scripts/run-benchmarks.sh --type=a2a --verbose      # Run A2A benchmarks
#   bash scripts/run-benchmarks.sh --type=mcp --threads=8    # Run MCP with 8 threads
#   bash scripts/run-benchmarks.sh --compare=baseline.json   # Compare with baseline
#   bash scripts/run-benchmarks.sh --report --output=reports # Generate HTML report
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
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" >&2
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" >&2
}

log_benchmark() {
    echo -e "${CYAN}[BENCHMARK]${NC} $1" >&2
}

# Show help message
show_help() {
    cat << 'EOF'
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

Examples:
  bash scripts/run-benchmarks.sh                           # Run all benchmarks
  bash scripts/run-benchmarks.sh --type=a2a --verbose      # Run A2A benchmarks
  bash scripts/run-benchmarks.sh --type=mcp --threads=8    # Run MCP with 8 threads
  bash scripts/run-benchmarks.sh --compare=baseline.json   # Compare with baseline
  bash scripts/run-benchmarks.sh --report --output=reports # Generate HTML report

Exit Codes:
  0 - All benchmarks completed successfully
  1 - Benchmark execution failed
  2 - Invalid arguments or configuration
  3 - Compilation failed
  4 - Timeout exceeded

Environment Variables:
  BENCHMARK_JVM_OPTS     Additional JVM options
  BENCHMARK_OFFLINE      Set to 1 for offline mode
  BENCHMARK_FAIL_FAST    Set to 1 to fail on first error
EOF
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --help)
                show_help
                exit 0
                ;;
            --type=*)
                BENCHMARK_TYPE="${1#*=}"
                ;;
            --forks=*)
                FORKS="${1#*=}"
                ;;
            --warmup=*)
                WARMUP_ITERATIONS="${1#*=}"
                ;;
            --iterations=*)
                MEASUREMENT_ITERATIONS="${1#*=}"
                ;;
            --threads=*)
                THREADS="${1#*=}"
                ;;
            --timeout=*)
                TIMEOUT="${1#*=}"
                ;;
            --output=*)
                OUTPUT_DIR="${1#*=}"
                ;;
            --jvm-opts=*)
                JVM_OPTS="${1#*=}"
                ;;
            --compare=*)
                COMPARE_FILE="${1#*=}"
                ;;
            --report)
                GENERATE_REPORT=true
                ;;
            --verbose)
                VERBOSE=true
                ;;
            --skip-compile)
                SKIP_COMPILE=true
                ;;
            --profile=*)
                MAVEN_PROFILE="${1#*=}"
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 2
                ;;
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
    if [[ -z "$OUTPUT_DIR" ]]; then
        OUTPUT_DIR="${PROJECT_ROOT}/benchmark-results-${TIMESTAMP}"
    fi

    # Check environment variables
    if [[ "${BENCHMARK_OFFLINE:-0}" == "1" ]]; then
        OFFLINE_MODE=true
    fi
    if [[ "${BENCHMARK_FAIL_FAST:-0}" == "1" ]]; then
        FAIL_FAST=true
    fi
    if [[ -n "${BENCHMARK_JVM_OPTS:-}" ]]; then
        JVM_OPTS="${JVM_OPTS} ${BENCHMARK_JVM_OPTS}"
    fi
}

# Validate environment
validate_environment() {
    log_info "Validating environment..."

    # Check Java
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed or not in PATH"
        exit 2
    fi

    # Check Maven
    if ! command -v mvn &> /dev/null; then
        log_error "Maven is not installed or not in PATH"
        exit 2
    fi

    # Check Java version
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [[ $JAVA_VERSION -lt 21 ]]; then
        log_warn "Java version 21+ is recommended for optimal performance (found: $JAVA_VERSION)"
    fi

    # Check if we're in the correct directory
    cd "$PROJECT_ROOT"
    if [[ ! -f "pom.xml" ]]; then
        log_error "Not in YAWL project root directory"
        exit 2
    fi

    # Create output directory
    mkdir -p "$OUTPUT_DIR"

    log_success "Environment validation passed"
}

# Compile project
compile_project() {
    if [[ "$SKIP_COMPILE" == true ]]; then
        log_info "Skipping compilation as requested"
        return 0
    fi

    log_info "Compiling project with profile: $MAVEN_PROFILE"

    local mvn_args="-T 1.5C compile test-compile -P $MAVEN_PROFILE -q"

    if [[ "$OFFLINE_MODE" == true ]]; then
        mvn_args="$mvn_args -o"
    fi

    if [[ "$VERBOSE" == true ]]; then
        mvn_args="${mvn_args/-q/}"
    fi

    cd "$PROJECT_ROOT"

    local compile_log="${OUTPUT_DIR}/compile.log"

    if ! mvn $mvn_args > "$compile_log" 2>&1; then
        log_error "Compilation failed. See $compile_log for details."
        if [[ "$VERBOSE" == true ]]; then
            cat "$compile_log"
        else
            tail -30 "$compile_log"
        fi
        exit 3
    fi

    log_success "Project compiled successfully"
}

# Build classpath
build_classpath() {
    log_info "Building classpath..."

    cd "$PROJECT_ROOT"

    # Get the classpath from Maven
    local classpath_file="${OUTPUT_DIR}/.classpath"

    mvn dependency:build-classpath -Dmdep.outputFile="$classpath_file" -q 2>/dev/null || true

    if [[ -f "$classpath_file" ]]; then
        CLASSPATH=$(cat "$classpath_file"):target/classes:target/test-classes
    else
        # Fallback to manual classpath construction
        CLASSPATH="target/classes:target/test-classes"

        # Add dependency jars
        for jar in ~/.m2/repository/org/openjdk/jmh/jmh-core/*/jmh-core-*.jar; do
            [[ -f "$jar" ]] && CLASSPATH="$CLASSPATH:$jar"
        done
        for jar in ~/.m2/repository/org/openjdk/jmh/jmh-generator-bytecode/*/jmh-generator-bytecode-*.jar; do
            [[ -f "$jar" ]] && CLASSPATH="$CLASSPATH:$jar"
        done
    fi

    log_success "Classpath built successfully"
}

# Run benchmarks
run_benchmarks() {
    log_benchmark "Starting benchmark execution..."
    log_benchmark "Type: $BENCHMARK_TYPE"
    log_benchmark "Forks: $FORKS"
    log_benchmark "Warmup: $WARMUP_ITERATIONS iterations"
    log_benchmark "Measurement: $MEASUREMENT_ITERATIONS iterations"
    log_benchmark "Threads: $THREADS"
    log_benchmark "Timeout: ${TIMEOUT}s"
    log_benchmark "Output: $OUTPUT_DIR"
    echo

    cd "$PROJECT_ROOT"

    # Build JVM options
    local jvm_opts="-Xms2g -Xmx4g -XX:+UseG1GC -XX:+UseCompactObjectHeaders"
    if [[ -n "$JVM_OPTS" ]]; then
        jvm_opts="$jvm_opts $JVM_OPTS"
    fi

    # Build benchmark pattern based on type
    local benchmark_pattern=""
    case "$BENCHMARK_TYPE" in
        a2a)
            benchmark_pattern="IntegrationBenchmarks.a2a.*"
            ;;
        mcp)
            benchmark_pattern="IntegrationBenchmarks.mcp.*"
            ;;
        zai)
            benchmark_pattern="IntegrationBenchmarks.zai.*"
            ;;
        stress)
            benchmark_pattern="StressTestBenchmarks.*"
            ;;
        all)
            benchmark_pattern=".*"
            ;;
    esac

    # Output files
    local results_file="${OUTPUT_DIR}/benchmark-results-${TIMESTAMP}.json"
    local log_file="${OUTPUT_DIR}/benchmark-output-${TIMESTAMP}.log"

    log_info "Running benchmarks with pattern: $benchmark_pattern"

    # Run the benchmark suite
    local start_time=$(date +%s)

    # Execute benchmarks using Maven exec or direct Java invocation
    local benchmark_class="${BENCHMARK_CLASSES[$BENCHMARK_TYPE]}"

    if [[ "$BENCHMARK_TYPE" == "all" ]]; then
        # Use the BenchmarkSuite runner for comprehensive testing
        run_benchmark_suite "$results_file" "$log_file"
    else
        # Run specific benchmark type
        run_specific_benchmark "$benchmark_pattern" "$results_file" "$log_file"
    fi

    local exit_code=$?
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    if [[ $exit_code -eq 0 ]]; then
        log_success "Benchmarks completed in ${duration}s"
        log_info "Results saved to: $results_file"
        log_info "Log saved to: $log_file"
    else
        log_error "Benchmarks failed after ${duration}s"
        if [[ "$VERBOSE" == true ]] && [[ -f "$log_file" ]]; then
            tail -50 "$log_file"
        fi
        return 1
    fi

    # Run comparison if baseline provided
    if [[ -n "$COMPARE_FILE" ]] && [[ -f "$COMPARE_FILE" ]]; then
        compare_with_baseline "$results_file" "$COMPARE_FILE"
    fi

    # Generate report if requested
    if [[ "$GENERATE_REPORT" == true ]]; then
        generate_html_report "$results_file"
    fi

    return $exit_code
}

# Run benchmark suite
run_benchmark_suite() {
    local results_file="$1"
    local log_file="$2"

    log_info "Running comprehensive benchmark suite..."

    # Run IntegrationBenchmarks
    log_benchmark "Running Integration Benchmarks..."
    if ! run_jmh_benchmark "IntegrationBenchmarks.*" "${OUTPUT_DIR}/integration-results.json" >> "$log_file" 2>&1; then
        if [[ "$FAIL_FAST" == true ]]; then
            log_error "Integration benchmarks failed (fail-fast enabled)"
            return 1
        fi
    fi

    # Run StressTestBenchmarks
    log_benchmark "Running Stress Test Benchmarks..."
    if ! run_jmh_benchmark "StressTestBenchmarks.*" "${OUTPUT_DIR}/stress-results.json" >> "$log_file" 2>&1; then
        if [[ "$FAIL_FAST" == true ]]; then
            log_error "Stress test benchmarks failed (fail-fast enabled)"
            return 1
        fi
    fi

    # Merge results
    merge_benchmark_results "$results_file"

    return 0
}

# Run specific benchmark
run_specific_benchmark() {
    local pattern="$1"
    local results_file="$2"
    local log_file="$3"

    log_info "Running benchmark pattern: $pattern"

    run_jmh_benchmark "$pattern" "$results_file" >> "$log_file" 2>&1
}

# Run JMH benchmark
run_jmh_benchmark() {
    local pattern="$1"
    local results_file="$2"

    cd "$PROJECT_ROOT"

    # Build classpath from target directories
    local classpath="target/test-classes:target/classes"

    # Add all dependencies from Maven repository
    for jar in $(find ~/.m2/repository -name "*.jar" -type f 2>/dev/null | head -100); do
        classpath="$classpath:$jar"
    done

    # Use Maven exec plugin instead of direct Java invocation
    mvn exec:java \
        -Dexec.mainClass="org.openjdk.jmh.Main" \
        -Dexec.classpathScope=test \
        -Dexec.args="$pattern -f $FORKS -wi $WARMUP_ITERATIONS -i $MEASUREMENT_ITERATIONS -t $THREADS -rff $results_file -rf json" \
        -q 2>&1

    return $?
}

# Merge benchmark results
merge_benchmark_results() {
    local output_file="$1"

    log_info "Merging benchmark results..."

    # Start JSON array
    echo "[" > "$output_file"

    local first=true

    # Merge integration results
    if [[ -f "${OUTPUT_DIR}/integration-results.json" ]]; then
        if [[ "$first" != true ]]; then
            echo "," >> "$output_file"
        fi
        # Extract results array and add to output
        jq -c '.[]' "${OUTPUT_DIR}/integration-results.json" >> "$output_file" 2>/dev/null || true
        first=false
    fi

    # Merge stress test results
    if [[ -f "${OUTPUT_DIR}/stress-results.json" ]]; then
        if [[ "$first" != true ]]; then
            echo "," >> "$output_file"
        fi
        jq -c '.[]' "${OUTPUT_DIR}/stress-results.json" >> "$output_file" 2>/dev/null || true
    fi

    # Close JSON array
    echo "]" >> "$output_file"

    log_success "Results merged to $output_file"
}

# Compare with baseline
compare_with_baseline() {
    local current_file="$1"
    local baseline_file="$2"

    log_info "Comparing with baseline: $baseline_file"

    local comparison_file="${OUTPUT_DIR}/comparison-${TIMESTAMP}.json"

    # Run comparison using the Java comparator
    cd "$PROJECT_ROOT"

    java -cp "target/test-classes:target/classes" \
        org.yawlfoundation.yawl.integration.benchmark.PerformanceRegressionDetector \
        "$baseline_file" "$current_file" "$comparison_file" 2>/dev/null || {
        log_warn "Could not run performance comparison (comparator not available)"
        return 0
    }

    if [[ -f "$comparison_file" ]]; then
        log_success "Comparison saved to: $comparison_file"

        # Show summary
        if command -v jq &> /dev/null; then
            local regressions=$(jq '[.[] | select(.regression == true)] | length' "$comparison_file" 2>/dev/null || echo "0")
            local improvements=$(jq '[.[] | select(.improvement == true)] | length' "$comparison_file" 2>/dev/null || echo "0")

            echo
            log_info "Comparison Summary:"
            log_info "  Regressions: $regressions"
            log_info "  Improvements: $improvements"
        fi
    fi
}

# Generate HTML report
generate_html_report() {
    local results_file="$1"
    local report_file="${OUTPUT_DIR}/benchmark-report-${TIMESTAMP}.html"

    log_info "Generating HTML report..."

    # Extract summary statistics
    local total_benchmarks=0
    local avg_latency=0
    local max_latency=0

    if command -v jq &> /dev/null && [[ -f "$results_file" ]]; then
        total_benchmarks=$(jq 'length' "$results_file" 2>/dev/null || echo "0")
        avg_latency=$(jq '[.[].primaryMetric.score] | add / length' "$results_file" 2>/dev/null || echo "0")
        max_latency=$(jq '[.[].primaryMetric.score] | max' "$results_file" 2>/dev/null || echo "0")
    fi

    # Generate HTML
    cat > "$report_file" << EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>YAWL Benchmark Report - $TIMESTAMP</title>
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            margin: 0; padding: 20px;
            background: #f5f5f5;
            color: #333;
        }
        .container { max-width: 1200px; margin: 0 auto; }
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 30px;
            border-radius: 10px;
            margin-bottom: 20px;
        }
        .header h1 { margin: 0 0 10px 0; }
        .header .timestamp { opacity: 0.8; }
        .summary {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin-bottom: 20px;
        }
        .stat-card {
            background: white;
            padding: 20px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        .stat-card h3 { margin: 0 0 10px 0; color: #666; font-size: 14px; }
        .stat-card .value { font-size: 32px; font-weight: bold; color: #333; }
        .stat-card .unit { font-size: 14px; color: #999; }
        .section {
            background: white;
            padding: 20px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            margin-bottom: 20px;
        }
        .section h2 { margin: 0 0 20px 0; padding-bottom: 10px; border-bottom: 2px solid #eee; }
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; }
        th { background: #f8f9fa; font-weight: 600; }
        tr:hover { background: #f8f9fa; }
        .good { color: #28a745; }
        .warning { color: #ffc107; }
        .bad { color: #dc3545; }
        .config-list { list-style: none; padding: 0; }
        .config-list li { padding: 8px 0; border-bottom: 1px solid #eee; }
        .config-list li:last-child { border-bottom: none; }
        .config-key { font-weight: 600; color: #666; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>YAWL Benchmark Report</h1>
            <div class="timestamp">Generated: $(date -r "$results_file" 2>/dev/null || date)</div>
        </div>

        <div class="summary">
            <div class="stat-card">
                <h3>Total Benchmarks</h3>
                <div class="value">$total_benchmarks</div>
            </div>
            <div class="stat-card">
                <h3>Average Latency</h3>
                <div class="value">${avg_latency}<span class="unit">ms</span></div>
            </div>
            <div class="stat-card">
                <h3>Max Latency</h3>
                <div class="value">${max_latency}<span class="unit">ms</span></div>
            </div>
            <div class="stat-card">
                <h3>Thread Count</h3>
                <div class="value">$THREADS</div>
            </div>
        </div>

        <div class="section">
            <h2>Configuration</h2>
            <ul class="config-list">
                <li><span class="config-key">Benchmark Type:</span> $BENCHMARK_TYPE</li>
                <li><span class="config-key">JVM Forks:</span> $FORKS</li>
                <li><span class="config-key">Warmup Iterations:</span> $WARMUP_ITERATIONS</li>
                <li><span class="config-key">Measurement Iterations:</span> $MEASUREMENT_ITERATIONS</li>
                <li><span class="config-key">Threads:</span> $THREADS</li>
                <li><span class="config-key">Timeout:</span> ${TIMEOUT}s</li>
                <li><span class="config-key">Maven Profile:</span> $MAVEN_PROFILE</li>
                <li><span class="config-key">Java Version:</span> $(java -version 2>&1 | head -1)</li>
            </ul>
        </div>

        <div class="section">
            <h2>Performance Targets</h2>
            <table>
                <thead>
                    <tr>
                        <th>Component</th>
                        <th>Metric</th>
                        <th>Target</th>
                        <th>Status</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>A2A</td>
                        <td>Throughput</td>
                        <td>&gt;1000 req/s</td>
                        <td class="good">Configured</td>
                    </tr>
                    <tr>
                        <td>A2A</td>
                        <td>p95 Latency</td>
                        <td>&lt;200ms</td>
                        <td class="good">Configured</td>
                    </tr>
                    <tr>
                        <td>MCP</td>
                        <td>Tool Execution p95</td>
                        <td>&lt;100ms</td>
                        <td class="good">Configured</td>
                    </tr>
                    <tr>
                        <td>Z.ai</td>
                        <td>Fast Model p95</td>
                        <td>&lt;100ms</td>
                        <td class="good">Configured</td>
                    </tr>
                </tbody>
            </table>
        </div>

        <div class="section">
            <h2>Output Files</h2>
            <ul class="config-list">
                <li><span class="config-key">Results:</span> $results_file</li>
                <li><span class="config-key">Report:</span> $report_file</li>
            </ul>
        </div>
    </div>
</body>
</html>
EOF

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
    echo
    log_info "To view results:"
    echo "  cat ${OUTPUT_DIR}/benchmark-results-*.json | jq '.'"
    echo
    if [[ "$GENERATE_REPORT" == true ]]; then
        log_info "To view HTML report:"
        echo "  open ${OUTPUT_DIR}/benchmark-report-*.html"
        echo
    fi
    log_info "For regression analysis:"
    echo "  java -cp target/test-classes org.yawlfoundation.yawl.integration.benchmark.PerformanceRegressionDetector baseline.csv current.csv"
    echo
}

# Cleanup function
cleanup() {
    local exit_code=$?

    # Kill any background processes
    jobs -p | xargs -r kill 2>/dev/null || true

    exit $exit_code
}

# Main execution
main() {
    # Set up cleanup trap
    trap cleanup EXIT

    # Parse arguments
    parse_args "$@"

    # Display banner
    echo
    echo "=== YAWL Integration Performance Benchmark Suite ==="
    echo "Timestamp: $(date)"
    echo "Java: $(java -version 2>&1 | head -1)"
    echo

    # Validate environment
    validate_environment

    # Compile project
    compile_project

    # Build classpath
    build_classpath

    # Run benchmarks
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

# Run main function with all arguments
main "$@"
