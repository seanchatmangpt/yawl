#!/bin/bash

# YAWL Benchmark Orchestration Script v6.0.0-GA
# ============================================
#
# This script runs comprehensive benchmarks with different profiles for YAWL v6.0.0-GA.
# Supports Development, CI, Production, and Custom profiles with configurable test selection.
#
# Usage:
#   ./scripts/run-benchmarks.sh [profile] [options]
#
# Profiles:
#   development    - Quick subset of tests (5-10 minutes)
#   ci            - Comprehensive tests (30-60 minutes)
#   production    - Full suite (2-4 hours)
#   custom        - Custom test selection
#
# Options:
#   -t, --tests TESTS       Comma-separated list of specific tests (for custom profile)
#   -o, --output-dir DIR    Output directory for results
#   -v, --verbose           Enable verbose logging
#   -j, --jmh-only         Run only JMH microbenchmarks
#   -i, --integration-only  Run only integration tests
#   -s, --stress-tests      Include stress tests
#   -c, --chaos-tests      Include chaos engineering tests
#   -r, --regression       Include regression detection
#   -p, --parallel N       Run tests in parallel (default: 4)
#   --dry-run              Show what would be executed without running
#   --skip-validation       Skip pre/post validation steps
#   --timeout SECONDS      Timeout for individual test (default: 3600)
#   -h, --help             Show this help message

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEFAULT_OUTPUT_DIR="${PROJECT_ROOT}/benchmark-results"
DEFAULT_PARALLEL=4
DEFAULT_TIMEOUT=3600
DRY_RUN=false
SKIP_VALIDATION=false

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Test categories for different profiles
declare -A TEST_CATEGORIES=(
    ["development"]="jmh-basic,integration-core,performance-baseline"
    ["ci"]="jmh-full,integration-all,performance-comprehensive,stress-basic"
    ["production"]="jmh-extended,integration-production,stress-advanced,chaos-engineering,regression-detection"
    ["custom"]=""
)

# Test configurations
declare -A TEST_CONFIGS=(
    ["jmh-basic"]="ConcurrencyBenchmarkSuite,MemoryUsageProfiler,ThreadContentionAnalyzer"
    ["jmh-full"]="ConcurrencyBenchmarkSuite,MemoryUsageProfiler,ThreadContentionAnalyzer,StructuredConcurrencyBenchmark,VirtualThreadScalingBenchmarks"
    ["jmh-extended"]="ConcurrencyBenchmarkSuite,MemoryUsageProfiler,ThreadContentionAnalyzer,StructuredConcurrencyBenchmark,VirtualThreadScalingBenchmarks,A2ACommunicationBenchmarks,MCPPerformanceBenchmarks"
    ["integration-core"]="BenchmarkConfig,BenchmarkRunner,PerformanceTest"
    ["integration-all"]="BenchmarkConfig,BenchmarkRunner,PerformanceTest,A2AIntegrationBenchmark,DatabaseConnectionBenchmark"
    ["integration-production"]="BenchmarkConfig,BenchmarkRunner,PerformanceTest,A2AIntegrationBenchmark,DatabaseConnectionBenchmark,ProductionTestRunner,MultiRegionTest"
    ["performance-baseline"]="EnginePerformanceBaseline,SimpleTest,ScalabilityTest"
    ["performance-comprehensive"]="EnginePerformanceBaseline,SimpleTest,ScalabilityTest,WorkflowThroughputBenchmark,LoadTestSuite"
    ["stress-basic"]="CaseCreationStressTest,WorkItemFloodTest,MemoryPressureTest"
    ["stress-advanced"]="CaseCreationStressTest,WorkItemFloodTest,MemoryPressureTest,LongRunningStressTest,MultiTenantStressTest,ProductionWorkloadStressTest"
    ["chaos-engineering"]="ServiceChaosTest,EnhancedChaosTest"
    ["regression-detection"]="HibernatePerformanceRegressionTest,MemoryLeakDetectionTest"
)

log() {
    local level="$1"
    shift
    local message="$*"

    case "$level" in
        "INFO")
            echo -e "${BLUE}[INFO]${NC} $message"
            ;;
        "WARN")
            echo -e "${YELLOW}[WARN]${NC} $message"
            ;;
        "ERROR")
            echo -e "${RED}[ERROR]${NC} $message"
            ;;
        "SUCCESS")
            echo -e "${GREEN}[SUCCESS]${NC} $message"
            ;;
        "DEBUG")
            if [[ "${VERBOSE:-false}" == "true" ]]; then
                echo -e "${CYAN}[DEBUG]${NC} $message"
            fi
            ;;
    esac
}

print_help() {
    cat << EOF
YAWL Benchmark Orchestration Script v6.0.0-GA
=============================================

Run comprehensive benchmarks with different profiles for YAWL v6.0.0-GA.

PROFILES:
  development    Quick subset of tests (5-10 minutes)
  ci            Comprehensive tests (30-60 minutes)
  production    Full suite (2-4 hours)
  custom        Custom test selection

USAGE:
  $0 [profile] [options]

EXAMPLES:
  $0 development
  $0 ci --verbose --parallel 8
  $0 production --stress-tests --chaos-tests
  $0 custom --tests "ConcurrencyBenchmarkSuite,A2ACommunicationBenchmarks" --jmh-only

OPTIONS:
  -t, --tests TESTS       Comma-separated list of specific tests (for custom profile)
  -o, --output-dir DIR    Output directory for results (default: $DEFAULT_OUTPUT_DIR)
  -v, --verbose           Enable verbose logging
  -j, --jmh-only         Run only JMH microbenchmarks
  -i, --integration-only  Run only integration tests
  -s, --stress-tests      Include stress tests
  -c, --chaos-tests      Include chaos engineering tests
  -r, --regression       Include regression detection
  -p, --parallel N       Run tests in parallel (default: $DEFAULT_PARALLEL)
  --dry-run              Show what would be executed without running
  --skip-validation       Skip pre/post validation steps
  --timeout SECONDS      Timeout for individual test (default: $DEFAULT_TIMEOUT)
  -h, --help             Show this help message

ENVIRONMENT VARIABLES:
  JAVA_HOME              Java home directory
  MAVEN_OPTS             Maven options
  BENCHMARK_CONFIG       Path to custom benchmark configuration
  SLACK_WEBHOOK_URL      Slack webhook for notifications
  EMAIL_RECIPIENTS       Comma-separated email addresses for alerts

EOF
}

check_requirements() {
    log "INFO" "Checking requirements..."

    # Check Java
    if [[ -z "${JAVA_HOME:-}" ]]; then
        log "WARN" "JAVA_HOME not set, using java from PATH"
        JAVA_CMD="java"
    else
        JAVA_CMD="$JAVA_HOME/bin/java"
    fi

    if ! command -v "$JAVA_CMD" >/dev/null 2>&1; then
        log "ERROR" "Java not found. Please set JAVA_HOME or ensure java is in PATH."
        exit 1
    fi

    # Check Maven
    if ! command -v mvn >/dev/null 2>&1; then
        log "ERROR" "Maven not found. Please install Maven."
        exit 1
    fi

    # Check required JARs
    local required_jars=(
        "target/benchmarks.jar"
        "target/classes/org/yawlfoundation/yawl/performance/BenchmarkConfig.class"
    )

    for jar in "${required_jars[@]}"; do
        if [[ ! -f "$PROJECT_ROOT/$jar" ]]; then
            log "WARN" "Required file not found: $jar. Building project..."
            cd "$PROJECT_ROOT"
            mvn clean compile -q
            break
        fi
    done

    log "INFO" "All requirements satisfied"
}

get_tests_for_profile() {
    local profile="$1"
    local include_stress="${2:-false}"
    local include_chaos="${3:-false}"
    local include_regression="${4:-false}"

    local tests="${TEST_CATEGORIES[$profile]}"
    if [[ -z "$tests" ]]; then
        log "ERROR" "Unknown profile: $profile"
        exit 1
    fi

    # Add additional tests based on flags
    if [[ "$include_stress" == "true" ]]; then
        if [[ "$profile" == "production" ]]; then
            tests="${tests},stress-advanced"
        else
            tests="${tests},stress-basic"
        fi
    fi

    if [[ "$include_chaos" == "true" ]]; then
        tests="${tests},chaos-engineering"
    fi

    if [[ "$include_regression" == "true" ]]; then
        tests="${tests},regression-detection"
    fi

    echo "$tests"
}

expand_test_categories() {
    local categories="$1"
    local expanded_tests=()

    IFS=',' read -ra category_array <<< "$categories"
    for category in "${category_array[@]}"; do
        if [[ -n "${TEST_CONFIGS[$category]}" ]]; then
            IFS=',' read -ra test_array <<< "${TEST_CONFIGS[$category]}"
            expanded_tests+=("${test_array[@]}")
        else
            # Direct test name
            expanded_tests+=("$category")
        fi
    done

    # Remove duplicates while preserving order
    local unique_tests=()
    local seen=()
    for test in "${expanded_tests[@]}"; do
        if [[ ! " ${seen[*]} " =~ " $test " ]]; then
            seen+=("$test")
            unique_tests+=("$test")
        fi
    done

    printf "%s\n" "${unique_tests[@]}"
}

run_jmh_benchmark() {
    local test="$1"
    local output_dir="$2"
    local timeout_seconds="$3"

    log "INFO" "Running JMH benchmark: $test"

    local test_output="$output_dir/${test,,}-results.json"
    local test_log="$output_dir/${test,,}-log.txt"

    # Run with timeout
    timeout $timeout_seconds \
        "$JAVA_CMD" -jar "$PROJECT_ROOT/target/benchmarks.jar" \
        -rf json \
        -rff "$test_output" \
        -wi 3 \
        -i 5 \
        -f 1 \
        -tu ms \
        -jvmArgs "-Xms2g" \
        -jvmArgs "-Xmx4g" \
        -jvmArgs "-XX:+UseZGC" \
        -jvmArgs "--enable-preview" \
        "$test" > "$test_log" 2>&1 &

    local pid=$!
    local result_code=0

    # Monitor progress
    while kill -0 $pid 2>/dev/null; do
        log "DEBUG" "Running $test... (PID: $pid)"
        sleep 30
    done

    wait $pid
    result_code=$?

    if [[ $result_code -eq 0 ]]; then
        log "SUCCESS" "Completed JMH benchmark: $test"
        return 0
    elif [[ $result_code -eq 124 ]]; then
        log "ERROR" "JMH benchmark $test timed out after $timeout_seconds seconds"
        return 1
    else
        log "ERROR" "JMH benchmark $test failed with exit code: $result_code"
        cat "$test_log" | tail -20 || true
        return 1
    fi
}

run_integration_test() {
    local test="$1"
    local output_dir="$2"
    local timeout_seconds="$3"

    log "INFO" "Running integration test: $test"

    local test_output="$output_dir/${test,,}-results.txt"
    local test_log="$output_dir/${test,,}-log.txt"

    # Run with timeout
    timeout $timeout_seconds \
        mvn test -Dtest="$test" -q \
        -Dspring.profiles.active=benchmark \
        -Dspring.test.execution.parallel.enabled=false \
        > "$test_log" 2>&1 &

    local pid=$!
    local result_code=0

    # Monitor progress
    while kill -0 $pid 2>/dev/null; do
        log "DEBUG" "Running integration test $test... (PID: $pid)"
        sleep 30
    done

    wait $pid
    result_code=$?

    if [[ $result_code -eq 0 ]]; then
        log "SUCCESS" "Completed integration test: $test"
        return 0
    elif [[ $result_code -eq 124 ]]; then
        log "ERROR" "Integration test $test timed out after $timeout_seconds seconds"
        return 1
    else
        log "ERROR" "Integration test $test failed with exit code: $result_code"
        cat "$test_log" | tail -20 || true
        return 1
    fi
}

run_single_test() {
    local test="$1"
    local output_dir="$2"
    local test_type="$3"
    local timeout_seconds="$4"

    log "INFO" "Starting $test_type: $test"

    case "$test_type" in
        "jmh")
            run_jmh_benchmark "$test" "$output_dir" "$timeout_seconds"
            ;;
        "integration")
            run_integration_test "$test" "$output_dir" "$timeout_seconds"
            ;;
        *)
            log "ERROR" "Unknown test type: $test_type"
            return 1
            ;;
    esac
}

run_tests_parallel() {
    local tests=("$@")
    local output_dir="$1"
    shift
    local test_type="$1"
    shift
    local parallel="$1"
    shift
    local timeout_seconds="$1"

    local pids=()
    local results=()

    # Function to cleanup background processes
    cleanup_pids() {
        for pid in "${pids[@]}"; do
            if kill -0 $pid 2>/dev/null; then
                kill -9 $pid 2>/dev/null || true
            fi
        done
    }

    # Set trap to cleanup on exit
    trap cleanup_pids EXIT

    # Run tests in parallel
    local test_index=0
    for test in "${tests[@]}"; do
        if [[ ${#pids[@]} -ge $parallel ]]; then
            # Wait for a test to complete
            local pid_index=0
            while [[ ${#pids[@]} -ge $parallel ]]; do
                if ! kill -0 "${pids[$pid_index]}" 2>/dev/null; then
                    wait "${pids[$pid_index]}"
                    results+=($?)
                    unset pids[$pid_index]
                    pids=("${pids[@]}")  # Rebuild array
                    break
                fi
                sleep 1
                pid_index=$(( (pid_index + 1) % ${#pids[@]} ))
            done
        fi

        # Start new test
        log "INFO" "Starting test $((test_index + 1))/${#tests[@]}: $test"

        if [[ "$DRY_RUN" == "true" ]]; then
            log "INFO" "[DRY RUN] Would run $test_type: $test"
            results+=(0)
        else
            run_single_test "$test" "$output_dir" "$test_type" "$timeout_seconds" &
            pids+=($!)
            results+=($?)
        fi

        test_index=$((test_index + 1))
    done

    # Wait for all remaining tests
    for pid in "${pids[@]}"; do
        wait $pid
        results+=($?)
    done

    # Check if all tests passed
    local failed_count=0
    for result in "${results[@]}"; do
        if [[ $result -ne 0 ]]; then
            failed_count=$((failed_count + 1))
        fi
    done

    if [[ $failed_count -eq 0 ]]; then
        log "SUCCESS" "All $test_type tests passed"
        return 0
    else
        log "ERROR" "$failed_count out of ${#tests[@]} $test_type tests failed"
        return 1
    fi
}

generate_summary_report() {
    local output_dir="$1"
    local profile="$2"
    local test_categories="$3"

    local summary_file="$output_dir/benchmark-summary-$(date +%Y%m%d-%H%M%S).md"

    log "INFO" "Generating summary report: $summary_file"

    cat << EOF > "$summary_file"
# YAWL Benchmark Summary Report

*Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")*
*Profile: $profile*
*Test Categories: $test_categories*

## Overview

This report summarizes the benchmark execution for YAWL v6.0.0-GA.

## Test Results

| Test Category | Status | Details |
|--------------|--------|---------|
EOF

    # Process result files
    local result_count=0
    local failed_count=0

    while IFS= read -r -d '' result_file; do
        local test_name=$(basename "$result_file" | sed 's/-results\.\(json\|txt\)$//')
        local status="✅ Passed"
        local details=""

        if [[ "$result_file" == *.json ]]; then
            local success_rate=$(jq '.summary.success_rate // 0' "$result_file" 2>/dev/null || echo "N/A")
            if [[ "$success_rate" != "N/A" && $(echo "$success_rate < 95" | bc -l 2>/dev/null || echo "0") -eq 1 ]]; then
                status="❌ Failed"
                failed_count=$((failed_count + 1))
                details="Success rate: ${success_rate}%"
            fi
        else
            if grep -q "FAILED\|ERROR" "$result_file" 2>/dev/null; then
                status="❌ Failed"
                failed_count=$((failed_count + 1))
                details="Test failures detected"
            fi
        fi

        echo "| $test_name | $status | $details |" >> "$summary_file"
        result_count=$((result_count + 1))
    done < <(find "$output_dir" -name "*-results.*" -print0)

    # Add summary section
    cat << EOF >> "$summary_file"

## Summary

- **Total Tests Run**: $result_count
- **Passed Tests**: $((result_count - failed_count))
- **Failed Tests**: $failed_count
- **Success Rate**: $(((result_count - failed_count) * 100 / result_count))%

## Recommendations

EOF

    if [[ $failed_count -gt 0 ]]; then
        cat << EOF
### Immediate Actions
1. Review failed test logs in $output_dir/
2. Investigate performance regressions
3. Check system resource usage during test execution

### Performance Optimizations
EOF
    else
        cat << EOF
All tests passed successfully. Consider:
1. Increasing load in next test cycle
2. Adding more edge cases to test suite
3. Monitoring production metrics for consistency
EOF
    fi

    cat << EOF >> "$summary_file"

## Next Steps

1. [ ] Review detailed reports in $output_dir/
2. [ ] Address any failed tests
3. [ ] Schedule follow-up benchmark run
4. [ ] Update performance baselines

---
*Generated by YAWL Benchmark Orchestration Script v6.0.0-GA*
EOF
}

send_notification() {
    local profile="$1"
    local output_dir="$2"
    local success="$3"

    # Skip if no notification configured
    if [[ -z "${SLACK_WEBHOOK_URL:-}" && -z "${EMAIL_RECIPIENTS:-}" ]]; then
        return
    fi

    local subject="YAWL Benchmark Results - $profile"
    local message="Benchmark execution completed with profile: $profile"

    if [[ "$success" == "true" ]]; then
        message+="\nStatus: ✅ SUCCESS"
    else
        message+="\nStatus: ❌ FAILED"
    fi

    message+="\nResults directory: $output_dir"
    message+="\nTimestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"

    # Send Slack notification
    if [[ -n "${SLACK_WEBHOOK_URL:-}" ]]; then
        curl -s -X POST -H 'Content-type: application/json' \
            --data "{\"text\":\"$message\"}" \
            "$SLACK_WEBHOOK_URL" > /dev/null 2>&1 || true
    fi

    # Send email notification
    if [[ -n "${EMAIL_RECIPIENTS:-}" ]]; then
        echo "$message" | mailx -s "$subject" "$EMAIL_RECIPIENTS" > /dev/null 2>&1 || true
    fi
}

main() {
    local profile="${1:-development}"
    shift || true

    # Parse command line arguments
    local custom_tests=""
    local output_dir="$DEFAULT_OUTPUT_DIR"
    local jmh_only=false
    local integration_only=false
    local include_stress=false
    local include_chaos=false
    local include_regression=false
    local parallel="$DEFAULT_PARALLEL"
    local timeout_seconds="$DEFAULT_TIMEOUT"

    while [[ $# -gt 0 ]]; do
        case $1 in
            -t|--tests)
                custom_tests="$2"
                shift 2
                ;;
            -o|--output-dir)
                output_dir="$2"
                shift 2
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -j|--jmh-only)
                jmh_only=true
                shift
                ;;
            -i|--integration-only)
                integration_only=true
                shift
                ;;
            -s|--stress-tests)
                include_stress=true
                shift
                ;;
            -c|--chaos-tests)
                include_chaos=true
                shift
                ;;
            -r|--regression)
                include_regression=true
                shift
                ;;
            -p|--parallel)
                parallel="$2"
                shift 2
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --skip-validation)
                SKIP_VALIDATION=true
                shift
                ;;
            --timeout)
                timeout_seconds="$2"
                shift 2
                ;;
            -h|--help)
                print_help
                exit 0
                ;;
            *)
                log "ERROR" "Unknown option: $1"
                print_help
                exit 1
                ;;
        esac
    done

    log "INFO" "Starting YAWL Benchmark Orchestration"
    log "INFO" "Profile: $profile"
    log "INFO" "Output directory: $output_dir"
    log "INFO" "Parallel jobs: $parallel"

    # Validate profile
    if [[ -z "${TEST_CATEGORIES[$profile]:-}" ]]; then
        log "ERROR" "Unknown profile: $profile"
        print_help
        exit 1
    fi

    # Check requirements unless skipping validation
    if [[ "$SKIP_VALIDATION" != "true" ]]; then
        check_requirements
    fi

    # Create output directory
    mkdir -p "$output_dir"

    # Get test categories
    if [[ "$profile" == "custom" ]]; then
        if [[ -z "$custom_tests" ]]; then
            log "ERROR" "Custom profile requires --tests option"
            exit 1
        fi
        local test_categories="$custom_tests"
    else
        local test_categories=$(get_tests_for_profile "$profile" "$include_stress" "$include_chaos" "$include_regression")
    fi

    log "INFO" "Test categories: $test_categories"

    # Expand test categories to individual tests
    local expanded_tests=($(expand_test_categories "$test_categories"))

    log "INFO" "Expanded tests: ${expanded_tests[*]}"

    # Run tests based on type
    local overall_success=true

    if [[ "$jmh_only" == "true" ]]; then
        log "INFO" "Running JMH benchmarks only"
        local jmh_tests=()
        for test in "${expanded_tests[@]}"; do
            if [[ "$test" =~ .*Benchmark ]]; then
                jmh_tests+=("$test")
            fi
        done
        if [[ ${#jmh_tests[@]} -gt 0 ]]; then
            if ! run_tests_parallel "${jmh_tests[@]}" "$output_dir" "jmh" $parallel $timeout_seconds; then
                overall_success=false
            fi
        else
            log "WARN" "No JMH tests found in expanded list"
        fi
    elif [[ "$integration_only" == "true" ]]; then
        log "INFO" "Running integration tests only"
        local integration_tests=()
        for test in "${expanded_tests[@]}"; do
            if [[ ! "$test" =~ .*Benchmark ]]; then
                integration_tests+=("$test")
            fi
        done
        if [[ ${#integration_tests[@]} -gt 0 ]]; then
            if ! run_tests_parallel "${integration_tests[@]}" "$output_dir" "integration" $parallel $timeout_seconds; then
                overall_success=false
            fi
        else
            log "WARN" "No integration tests found in expanded list"
        fi
    else
        # Run all tests
        if ! run_tests_parallel "${expanded_tests[@]}" "$output_dir" "jmh" $parallel $timeout_seconds; then
            overall_success=false
        fi
        if ! run_tests_parallel "${expanded_tests[@]}" "$output_dir" "integration" $parallel $timeout_seconds; then
            overall_success=false
        fi
    fi

    # Generate summary report
    if [[ "$overall_success" == "true" ]]; then
        generate_summary_report "$output_dir" "$profile" "$test_categories"
    fi

    # Send notifications
    send_notification "$profile" "$output_dir" "$overall_success"

    # Final status
    if [[ "$overall_success" == "true" ]]; then
        log "SUCCESS" "Benchmark orchestration completed successfully!"
        log "INFO" "Results available in: $output_dir"
        exit 0
    else
        log "ERROR" "Benchmark orchestration completed with failures"
        log "INFO" "Check results in: $output_dir"
        exit 1
    fi
}

# Execute main function with all arguments
main "$@"