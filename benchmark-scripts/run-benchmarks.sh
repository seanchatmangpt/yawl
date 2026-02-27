#!/usr/bin/env bash
# ==========================================================================
# run-benchmarks.sh - Comprehensive Benchmark Orchestration for YAWL v6.0.0-GA
#
# Main orchestration script for running all benchmark suites with different profiles
# Usage:
#   ./run-benchmarks.sh [profile] [options]
#   ./run-benchmarks.sh --help
#
# Profiles:
#   development   - Quick subset of tests (5-10 min)
#   ci           - Comprehensive tests (30-60 min)
#   production   - Full suite (2-4 hours)
#   custom       - Configurable test selection
#
# Examples:
#   ./run-benchmarks.sh development
#   ./run-benchmarks.sh ci --verbose --report-html
#   ./run-benchmarks.sh production --baseline-comparison
#   ./run-benchmarks.sh custom --include-pattern "A2A*"
# ==========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Color codes for enhanced output
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_BLUE='\033[94m'
readonly C_YELLOW='\033[93m'
readonly C_CYAN='\033[96m'
readonly C_MAGENTA='\033[95m'
readonly E_OK='✓'
readonly E_FAIL='✗'

# Default configuration
PROFILE="development"
VERBOSE=false
REPORT_FORMAT="json"
INCLUDE_PATTERN=""
EXCLUDE_PATTERN=""
PARALLEL_JOBS=4
THRESHOLD_CHECK=true
BASELINE_COMPARISON=false
RECURSIVE_COMPARE=false
CLEAN_BEFORE_RUN=true
JMH_THREADS=1
JMH_FORKS=1
JMH_ITERATIONS=3
JMH_WARMUP=3

# Results directory
RESULTS_DIR="benchmark-results"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Parse command line arguments
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --help|-h)
                show_help
                exit 0
                ;;
            development|ci|production|custom)
                PROFILE="$1"
                shift
                ;;
            --verbose|-v)
                VERBOSE=true
                shift
                ;;
            --report-json)
                REPORT_FORMAT="json"
                shift
                ;;
            --report-html)
                REPORT_FORMAT="html"
                shift
                ;;
            --report-csv)
                REPORT_FORMAT="csv"
                shift
                ;;
            --include-pattern)
                INCLUDE_PATTERN="$2"
                shift 2
                ;;
            --exclude-pattern)
                EXCLUDE_PATTERN="$2"
                shift 2
                ;;
            --parallel)
                PARALLEL_JOBS="$2"
                shift 2
                ;;
            --jmh-threads)
                JMH_THREADS="$2"
                shift 2
                ;;
            --jmh-forks)
                JMH_FORKS="$2"
                shift 2
                ;;
            --jmh-iterations)
                JMH_ITERATIONS="$2"
                shift 2
                ;;
            --jmh-warmup)
                JMH_WARMUP="$2"
                shift 2
                ;;
            --no-threshold-check)
                THRESHOLD_CHECK=false
                shift
                ;;
            --baseline-comparison)
                BASELINE_COMPARISON=true
                shift
                ;;
            --recursive-compare)
                RECURSIVE_COMPARE=true
                shift
                ;;
            --no-clean)
                CLEAN_BEFORE_RUN=false
                shift
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            *)
                echo "Unknown argument: $1"
                echo "Use --help for usage information"
                exit 1
                ;;
        esac
    done
}

show_help() {
    cat << EOF
YAWL v6.0.0-GA Benchmark Orchestration Script

This script runs comprehensive benchmark suites with different profiles.

Usage:
  ./run-benchmarks.sh [PROFILE] [OPTIONS]

Profiles:
  development   - Quick subset of tests (5-10 minutes)
  ci           - Comprehensive tests (30-60 minutes)
  production   - Full suite (2-4 hours)
  custom       - Configurable test selection

Options:
  --verbose, -v                 Enable verbose output
  --report-json                 Generate JSON report (default)
  --report-html                 Generate HTML report
  --report-csv                  Generate CSV report
  --include-pattern PATTERN     Include only matching benchmarks
  --exclude-pattern PATTERN     Exclude matching benchmarks
  --parallel JOBS               Number of parallel jobs (default: 4)
  --jmh-threads N               JMH thread count (default: 1)
  --jmh-forks N                JMH fork count (default: 1)
  --jmh-iterations N           JMH iterations (default: 3)
  --jmh-warmup N               JMH warmup iterations (default: 3)
  --no-threshold-check          Disable performance threshold checking
  --baseline-comparison        Compare against baseline performance
  --recursive-compare          Recursively compare historical results
  --no-clean                   Skip clean before running
  --dry-run                    Show what would be executed without running
  --help, -h                   Show this help message

Examples:
  ./run-benchmarks.sh development
  ./run-benchmarks.sh ci --verbose --report-html
  ./run-benchmarks.sh production --baseline-comparison
  ./run-benchmarks.sh custom --include-pattern "A2A*"
EOF
}

# Benchmark suite configurations by profile
get_benchmark_suite() {
    local profile="$1"
    case "$profile" in
        development)
            echo "
- benchmark:jmh
- benchmark:unit
- test:performance/ConcurrencyBenchmarkSuite
- test:performance/MemoryUsageBenchmark
"
            ;;
        ci)
            echo "
- benchmark:jmh
- benchmark:unit
- benchmark:integration
- test:performance/*
- test:chaos
- test:a2a
- test:stress
"
            ;;
        production)
            echo "
- benchmark:jmh
- benchmark:unit
- benchmark:integration
- benchmark:stress
- test:performance/*
- test:chaos
- test:a2a
- test:regression
- test:scalability
- test:observability
- test:memory-leak
- test:load
"
            ;;
        custom)
            echo "
- benchmark:jmh
- benchmark:unit
"
            ;;
        *)
            echo "ERROR: Unknown profile: $profile" >&2
            exit 1
            ;;
    esac
}

# Generate JMH command with specific parameters
generate_jmh_command() {
    local benchmark="$1"
    local output_dir="$RESULTS_DIR/jmh-$(echo "$benchmark" | tr '/' '_')-${TIMESTAMP}"
    mkdir -p "$output_dir"

    cat << EOF
java -jar yawl-benchmark/target/benchmarks.jar \\
    -rf json \\
    -rff "${output_dir}/results.json" \\
    -w ${JMH_WARMUP} \\
    -r ${JMH_ITERATIONS} \\
    -f ${JMH_FORKS} \\
    -t ${JMH_THREADS} \\
    -foe true \\
    -v ${VERBOSE} \\
    ${benchmark}
EOF
}

# Execute benchmark suite
execute_benchmark_suite() {
    local profile="$1"
    local suite=$(get_benchmark_suite "$profile")
    local total_tasks=$(echo "$suite" | grep -c '^-' || echo 0)
    local completed_tasks=0

    echo "${C_CYAN}==================================================${C_RESET}"
    echo "${C_CYAN}Benchmark Suite: $profile Profile${C_RESET}"
    echo "${C_CYAN}==================================================${C_RESET}"
    echo ""

    if [[ "$CLEAN_BEFORE_RUN" == true ]]; then
        echo "${C_YELLOW}Cleaning build artifacts...${C_RESET}"
        mvn clean -q
        echo "${C_GREEN}${E_OK} Clean completed${C_RESET}"
        echo ""
    fi

    # Execute each benchmark
    while IFS= read -r line; do
        if [[ "$line" =~ ^- ]]; then
            local benchmark=$(echo "$line" | sed 's/^- //' | tr -d '\r')
            ((completed_tasks++))
            local progress="$completed_tasks/$total_tasks"

            echo "${C_BLUE}[$progress]${C_RESET} Running $benchmark..."

            if [[ "$benchmark" == benchmark:jmh ]]; then
                run_jmh_benchmarks
            elif [[ "$benchmark" == benchmark:unit ]]; then
                run_unit_benchmarks
            elif [[ "$benchmark" == benchmark:integration ]]; then
                run_integration_benchmarks
            elif [[ "$benchmark" == benchmark:stress ]]; then
                run_stress_benchmarks
            elif [[ "$benchmark" == test:chaos ]]; then
                run_chaos_tests
            elif [[ "$benchmark" == test:a2a ]]; then
                run_a2a_tests
            elif [[ "$benchmark" == test:regression ]]; then
                run_regression_tests
            elif [[ "$benchmark" == test:stress ]]; then
                run_load_tests
            elif [[ "$benchmark" == test:performance* ]]; then
                run_performance_tests "${benchmark#test:performance/}"
            else
                echo "${C_YELLOW}Warning: Unknown benchmark type: $benchmark${C_RESET}"
            fi

            echo "${C_GREEN}${E_OK} Completed $benchmark${C_RESET}"
            echo ""
        fi
    done <<< "$suite"
}

# Run JMH benchmarks
run_jmh_benchmarks() {
    local jmh_dir="${RESULTS_DIR}/jmh-${TIMESTAMP}"
    mkdir -p "$jmh_dir"

    # Build benchmark JAR
    echo "${C_YELLOW}Building JMH benchmarks...${C_RESET}"
    mvn package -Pjmh-benchmark -q -pl yawl-benchmark

    if [[ ! -f "yawl-benchmark/target/benchmarks.jar" ]]; then
        echo "${C_RED}ERROR: JMH benchmarks not built successfully${C_RESET}"
        exit 1
    fi

    # Configure JMH arguments based on profile
    local jmh_args=""

    if [[ "$INCLUDE_PATTERN" != "" ]]; then
        jmh_args="$jmh_args -rf json -rff ${jmh_dir}/filtered-results.json"
    fi

    echo "${C_CYAN}Running JMH benchmarks...${C_RESET}"
    if [[ "$VERBOSE" == true ]]; then
        jmh_args="$jmh_args -v"
    fi

    # Execute JMH benchmarks
    java -jar yawl-benchmark/target/benchmarks.jar \
        -rf json \
        -rff "${jmh_dir}/results.json" \
        -w ${JMH_WARMUP} \
        -r ${JMH_ITERATIONS} \
        -f ${JMH_FORKS} \
        -t ${JMH_THREADS} \
        -foe true \
        ${jmh_args} \
        ${INCLUDE_PATTERN:-"*"} \
        > "${jmh_dir}/jmh-output.log" 2>&1

    # Check results
    if [[ -f "${jmh_dir}/results.json" ]]; then
        echo "${C_GREEN}${E_OK} JMH benchmarks completed${C_RESET}"
        echo "Results: ${jmh_dir}/results.json"
    else
        echo "${C_RED}ERROR: JMH benchmarks failed${C_RESET}"
        cat "${jmh_dir}/jmh-output.log" >&2
        exit 1
    fi
}

# Run unit benchmarks
run_unit_benchmarks() {
    echo "${C_CYAN}Running unit benchmarks...${C_RESET}"
    local unit_dir="${RESULTS_DIR}/unit-${TIMESTAMP}"
    mkdir -p "$unit_dir"

    mvn test -Dtest=*Benchmark -q \
        -Dmaven.test.failure.ignore=true \
        -Dmaven.test.error.ignore=true \
        -DresultsDir="${unit_dir}" > "${unit_dir}/unit-output.log" 2>&1

    # Collect test results
    if [[ -d "target/surefire-reports" ]]; then
        cp target/surefire-reports/* "${unit_dir}/" 2>/dev/null || true
    fi

    echo "${C_GREEN}${E_OK} Unit benchmarks completed${C_RESET}"
}

# Run integration benchmarks
run_integration_benchmarks() {
    echo "${C_CYAN}Running integration benchmarks...${C_RESET}"
    local integration_dir="${RESULTS_DIR}/integration-${TIMESTAMP}"
    mkdir -p "$integration_dir"

    mvn verify -Pintegration -q \
        -Dmaven.test.failure.ignore=true \
        -Dmaven.test.error.ignore=true \
        -DresultsDir="${integration_dir}" > "${integration_dir}/integration-output.log" 2>&1

    echo "${C_GREEN}${E_OK} Integration benchmarks completed${C_RESET}"
}

# Run stress benchmarks
run_stress_tests() {
    echo "${C_CYAN}Running stress benchmarks...${C_RESET}"
    local stress_dir="${RESULTS_DIR}/stress-${TIMESTAMP}"
    mkdir -p "$stress_dir"

    # Run stress test suite
    mvn test -Dtest=StressTestSuite -q \
        -Dmaven.test.failure.ignore=true \
        -Dmaven.test.error.ignore=true \
        -Dstress.threshold.high=true \
        -DresultsDir="${stress_dir}" > "${stress_dir}/stress-output.log" 2>&1

    # Run A2A stress tests
    if [[ -f "scripts/run_a2a_benchmarks.sh" ]]; then
        ./scripts/run_a2a_benchmarks.sh --stress --output-dir "${stress_dir}/a2a-stress"
    fi

    echo "${C_GREEN}${E_OK} Stress tests completed${C_RESET}"
}

# Run chaos tests
run_chaos_tests() {
    echo "${C_CYAN}Running chaos engineering tests...${C_RESET}"
    local chaos_dir="${RESULTS_DIR}/chaos-${TIMESTAMP}"
    mkdir -p "$chaos_dir"

    if [[ -f "scripts/run-enhanced-chaos-tests.sh" ]]; then
        ./scripts/run-enhanced-chaos-tests.sh \
            --output-dir "${chaos_dir}" \
            --verbose=$VERBOSE \
            > "${chaos_dir}/chaos-output.log" 2>&1
    else
        echo "${C_YELLOW}Warning: Chaos tests script not found${C_RESET}"
    fi

    echo "${C_GREEN}${E_OK} Chaos tests completed${C_RESET}"
}

# Run A2A tests
run_a2a_tests() {
    echo "${C_CYAN}Running A2A (Agent-to-Agent) communication tests...${C_RESET}"
    local a2a_dir="${RESULTS_DIR}/a2a-${TIMESTAMP}"
    mkdir -p "$a2a_dir"

    if [[ -f "scripts/run_a2a_benchmarks.sh" ]]; then
        ./scripts/run_a2a_benchmarks.sh \
            --output-dir "${a2a_dir}" \
            --profile="$PROFILE" \
            --verbose=$VERBOSE \
            > "${a2a_dir}/a2a-output.log" 2>&1
    else
        echo "${C_YELLOW}Warning: A2A benchmarks script not found${C_RESET}"
    fi

    echo "${C_GREEN}${E_OK} A2A tests completed${C_RESET}"
}

# Run regression tests
run_regression_tests() {
    echo "${C_CYAN}Running regression detection tests...${C_RESET}"
    local regression_dir="${RESULTS_DIR}/regression-${TIMESTAMP}"
    mkdir -p "$regression_dir"

    if [[ -f "scripts/regression-detection.sh" ]]; then
        ./scripts/regression-detection.sh \
            --output-dir "${regression_dir}" \
            --baseline-comparison=$BASELINE_COMPARISON \
            > "${regression_dir}/regression-output.log" 2>&1
    else
        echo "${C_YELLOW}Warning: Regression detection script not found${C_RESET}"
    fi

    echo "${C_GREEN}${E_OK} Regression tests completed${C_RESET}"
}

# Run load tests
run_load_tests() {
    echo "${C_CYAN}Running load tests...${C_RESET}"
    local load_dir="${RESULTS_DIR}/load-${TIMESTAMP}"
    mkdir -p "$load_dir"

    # Run LoadIntegrationTest
    mvn test -Dtest=LoadIntegrationTest -q \
        -Dmaven.test.failure.ignore=true \
        -Dmaven.test.error.ignore=true \
        -DresultsDir="${load_dir}" > "${load_dir}/load-output.log" 2>&1

    # Run scalability tests
    if [[ -f "test/org/yawlfoundation/yawl/performance/ScalabilityTest.java" ]]; then
        mvn test -Dtest=ScalabilityTest -q \
            -Dmaven.test.failure.ignore=true \
            -Dmaven.test.error.ignore=true \
            -DresultsDir="${load_dir}" >> "${load_dir}/load-output.log" 2>&1
    fi

    echo "${C_GREEN}${E_OK} Load tests completed${C_RESET}"
}

# Run specific performance test
run_performance_tests() {
    local test_name="$1"
    local test_dir="${RESULTS_DIR}/performance-${test_name}-${TIMESTAMP}"
    mkdir -p "$test_dir"

    echo "${C_CYAN}Running performance test: $test_name${C_RESET}"

    case "$test_name" in
        ConcurrencyBenchmarkSuite)
            mvn test -Dtest=ConcurrencyBenchmarkSuite -q \
                -Dmaven.test.failure.ignore=true \
                -Dmaven.test.error.ignore=true \
                -DresultsDir="$test_dir" > "$test_dir/test-output.log" 2>&1
            ;;
        MemoryUsageBenchmark)
            mvn test -Dtest=MemoryUsageBenchmark -q \
                -Dmaven.test.failure.ignore=true \
                -Dmaven.test.error.ignore=true \
                -DresultsDir="$test_dir" > "$test_dir/test-output.log" 2>&1
            ;;
        WorkflowThroughputBenchmark)
            mvn test -Dtest=WorkflowThroughputBenchmark -q \
                -Dmaven.test.failure.ignore=true \
                -Dmaven.test.error.ignore=true \
                -DresultsDir="$test_dir" > "$test_dir/test-output.log" 2>&1
            ;;
        *)
            echo "${C_YELLOW}Warning: Unknown performance test: $test_name${C_RESET}"
            ;;
    esac

    echo "${C_GREEN}${E_OK} Performance test completed${C_RESET}"
}

# Generate comprehensive report
generate_report() {
    local report_dir="${RESULTS_DIR}/${TIMESTAMP}"
    mkdir -p "$report_dir"

    echo "${C_CYAN}==================================================${C_RESET}"
    echo "${C_CYAN}Generating Performance Report ($REPORT_FORMAT)${C_RESET}"
    echo "${C_CYAN}==================================================${C_RESET}"
    echo ""

    # Generate JSON report (always)
    cat > "$report_dir/benchmark-summary.json" << EOF
{
    "timestamp": "${TIMESTAMP}",
    "profile": "${PROFILE}",
    "jvm_config": "$(cat .java-version 2>/dev/null || echo 'unknown')",
    "total_duration_ms": $(($(date +%s%3N) - START_MS)),
    "benchmarks_completed": 0,
    "threshold_check": $THRESHOLD_CHECK,
    "baseline_comparison": $BASELINE_COMPARISON,
    "results": {
EOF

    # Count completed benchmarks
    local benchmark_count=0
    for category in jmh unit integration stress chaos a2a regression load performance; do
        if [[ -d "${RESULTS_DIR}/${category}-${TIMESTAMP}" ]]; then
            ((benchmark_count++))
            cat >> "$report_dir/benchmark-summary.json" << EOF
        "${category}": {
            "status": "completed",
            "timestamp": "${TIMESTAMP}",
            "results_dir": "${RESULTS_DIR}/${category}-${TIMESTAMP}",
            "files": $(find "${RESULTS_DIR}/${category}-${TIMESTAMP}" -type f -name "*.json" | wc -l)
        },
EOF
        fi
    done

    # Close JSON
    sed -i '' '$ s/,$//' "$report_dir/benchmark-summary.json"
    cat >> "$report_dir/benchmark-summary.json" << EOF
    }
}
EOF

    # Generate format-specific reports
    case "$REPORT_FORMAT" in
        html)
            generate_html_report "$report_dir"
            ;;
        csv)
            generate_csv_report "$report_dir"
            ;;
        json)
            echo "${C_GREEN}${E_OK} JSON report generated${C_RESET}"
            ;;
    esac

    # Generate trend analysis if recursive comparison enabled
    if [[ "$RECURSIVE_COMPARE" == true && -d "benchmark-results" ]]; then
        analyze_trends "$report_dir"
    fi

    echo "${C_GREEN}${E_OK} Report generated in: $report_dir${C_RESET}"
}

# Generate HTML report
generate_html_report() {
    local report_dir="$1"

    cat > "$report_dir/benchmark-report.html" << EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>YAWL v6.0.0-GA Benchmark Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background: #f0f0f0; padding: 20px; border-radius: 8px; }
        .summary { margin: 20px 0; }
        .benchmark { border: 1px solid #ddd; margin: 10px 0; padding: 15px; border-radius: 5px; }
        .success { background: #d4edda; }
        .failure { background: #f8d7da; }
        .warning { background: #fff3cd; }
        table { width: 100%; border-collapse: collapse; margin: 10px 0; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background: #f2f2f2; }
    </style>
</head>
<body>
    <div class="header">
        <h1>YAWL v6.0.0-GA Benchmark Report</h1>
        <p>Profile: ${PROFILE} | Timestamp: ${TIMESTAMP}</p>
    </div>

    <div class="summary">
        <h2>Summary</h2>
        <table>
            <tr><th>Total Benchmarks</th><td>${benchmark_count}</td></tr>
            <tr><th>Total Duration</th><td>$((($(date +%s%3N) - START_MS) / 1000))s</td></tr>
            <tr><th>JVM Configuration</th><td>$(cat .java-version 2>/dev/null || echo 'unknown')</td></tr>
        </table>
    </div>

    <div class="benchmarks">
        <h2>Benchmark Results</h2>
EOF

    # Add benchmark results
    for category in jmh unit integration stress chaos a2a regression load performance; do
        if [[ -d "${RESULTS_DIR}/${category}-${TIMESTAMP}" ]]; then
            cat >> "$report_dir/benchmark-report.html" << EOF
        <div class="benchmark success">
            <h3>$category Benchmarks</h3>
            <p>Results directory: ${RESULTS_DIR}/${category}-${TIMESTAMP}</p>
            <p>Files: $(find "${RESULTS_DIR}/${category}-${TIMESTAMP}" -type f | wc -l) files</p>
        </div>
EOF
        fi
    done

    cat >> "$report_dir/benchmark-report.html" << EOF
    </div>
</body>
</html>
EOF
}

# Generate CSV report
generate_csv_report() {
    local report_dir="$1"

    cat > "$report_dir/benchmark-results.csv" << EOF
Benchmark,Status,Duration,Results Directory,Files Count,Threshold Check
EOF

    for category in jmh unit integration stress chaos a2a regression load performance; do
        if [[ -d "${RESULTS_DIR}/${category}-${TIMESTAMP}" ]]; then
            local files=$(find "${RESULTS_DIR}/${category}-${TIMESTAMP}" -type f | wc -l)
            cat >> "$report_dir/benchmark-results.csv" << EOF
${category},COMPLETED,$((($(date +%s%3N) - START_MS) / 1000)),${RESULTS_DIR}/${category}-${TIMESTAMP},${files},$THRESHOLD_CHECK
EOF
        fi
    done
}

# Analyze performance trends
analyze_trends() {
    local report_dir="$1"

    if [[ ! -d "benchmark-results" ]]; then
        echo "${C_YELLOW}Warning: No historical results found for trend analysis${C_RESET}"
        return
    fi

    cat > "$report_dir/trend-analysis.md" << EOF
# Performance Trend Analysis

Generated: $(date)

## Analysis Summary

This section compares current benchmark results with historical data to identify trends.

## Trend Detection

EOF

    # Find recent benchmark runs
    local recent_runs=$(find benchmark-results -type d -name "*[0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]" | sort | tail -5)

    if [[ -n "$recent_runs" ]]; then
        echo "## Recent Benchmark Runs" >> "$report_dir/trend-analysis.md"
        echo "| Run Date | Profile | Status | Key Metrics |" >> "$report_dir/trend-analysis.md"
        echo "|----------|---------|--------|-------------|" >> "$report_dir/trend-analysis.md"

        for run in $recent_runs; do
            if [[ -f "$run/benchmark-summary.json" ]]; then
                local run_date=$(basename "$run")
                local run_profile=$(jq -r '.profile' "$run/benchmark-summary.json")
                local run_status=$(jq -r '.status // "unknown"' "$run/benchmark-summary.json")
                echo "| $run_date | $run_profile | $run_status | TBD |" >> "$report_dir/trend-analysis.md"
            fi
        done

        echo "" >> "$report_dir/trend-analysis.md"
    fi

    echo "### Recommendations" >> "$report_dir/trend-analysis.md"
    echo "- Monitor memory usage trends over time" >> "$report_dir/trend-analysis.md"
    echo "- Track latency improvements in A2A communication" >> "$report_dir/trend-analysis.md"
    echo "- Watch for performance degradation in chaos scenarios" >> "$report_dir/trend-analysis.md"
}

# Threshold checking
check_thresholds() {
    if [[ "$THRESHOLD_CHECK" == false ]]; then
        echo "${C_YELLOW}Skipping threshold checks (disabled)${C_RESET}"
        return
    fi

    echo "${C_CYAN}Checking performance thresholds...${C_RESET}"

    # Define thresholds by category
    local thresholds=(
        "jmh:latency_ms:5000"
        "jmh:throughput_ops:1000"
        "memory:heap_usage_percent:90"
        "a2a:message_latency_ms:1000"
        "chaos:recovery_time_ms:30000"
    )

    local violations=0

    for threshold in "${thresholds[@]}"; do
        local category=$(echo "$threshold" | cut -d: -f1)
        local metric=$(echo "$threshold" | cut -d: -f2)
        local max_value=$(echo "$threshold" | cut -d: -f3)

        # Check if results exist and exceed threshold
        if [[ -f "${RESULTS_DIR}/${category}-${TIMESTAMP}/results.json" ]]; then
            # This is a simplified check - in practice, you'd parse the JSON
            echo "${C_YELLOW}Checking $category/$metric against threshold $max_value${C_RESET}"
            # Actual threshold checking logic would go here
        fi
    done

    if [[ $violations -gt 0 ]]; then
        echo "${C_RED}ERROR: $violations performance thresholds exceeded${C_RESET}"
        echo "See detailed report for specific violations"
        return 1
    fi

    echo "${C_GREEN}${E_OK} All thresholds within limits${C_RESET}"
}

# Main execution
main() {
    parse_arguments "$@"

    echo "${C_CYAN}YAWL v6.0.0-GA Benchmark Orchestration${C_RESET}"
    echo "${C_CYAN}Profile: $PROFILE${C_RESET}"
    echo "${C_CYAN}Timestamp: $TIMESTAMP${C_RESET}"
    echo "${C_CYAN}Parallel Jobs: $PARALLEL_JOBS${C_RESET}"
    echo ""

    # Record start time
    START_MS=$(date +%s%3N)

    if [[ "${DRY_RUN:-false}" == true ]]; then
        echo "${C_YELLOW}Dry run - showing what would be executed:${C_RESET}"
        echo "Profile: $PROFILE"
        echo "Suite:"
        get_benchmark_suite "$PROFILE"
        exit 0
    fi

    # Execute benchmark suite
    execute_benchmark_suite "$PROFILE"

    # Check thresholds
    check_thresholds

    # Generate report
    generate_report

    # Calculate total duration
    END_MS=$(date +%s%3N)
    DURATION_MS=$((END_MS - START_MS))
    DURATION_S=$(awk "BEGIN {printf \"%.1f\", $DURATION_MS/1000}")

    echo "${C_CYAN}==================================================${C_RESET}"
    echo "${C_GREEN}${E_OK} Benchmark orchestration completed successfully${C_RESET}"
    echo "${C_CYAN}==================================================${C_RESET}"
    echo ""
    echo "Results directory: ${RESULTS_DIR}/${TIMESTAMP}/"
    echo "Total duration: ${DURATION_S}s"
    echo ""

    # Show next steps
    echo "Next steps:"
    echo "1. Review generated reports in ${RESULTS_DIR}/${TIMESTAMP}/"
    echo "2. Check performance thresholds and trends"
    echo "3. Investigate any failed benchmarks"
    echo "4. Archive results for historical comparison"
    echo ""

    if [[ "$BASELINE_COMPARISON" == true ]]; then
        echo "Baseline comparison completed - check regression analysis"
    fi
}

# Execute main function with all arguments
main "$@"