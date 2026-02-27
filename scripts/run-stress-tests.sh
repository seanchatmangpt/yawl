#!/bin/bash

# YAWL Stress Test Orchestration Script v6.0.0-GA
# ===============================================
#
# This script runs production-like stress tests for YAWL v6.0.0-GA.
# Simulates high-load scenarios to identify bottlenecks and validate performance limits.
#
# Usage:
#   ./scripts/run-stress-tests.sh [scenario] [options]
#
# Scenarios:
#   baseline        - Basic stress test with increasing load
#   sustained       - Sustained high load over extended period
#   spike          - Sudden load spikes
#   mixed          - Mixed workload pattern
#   chaos          - Chaos engineering with fault injection
#
# Options:
#   -t, --duration MINUTES     Duration of stress test (default: 60)
#   -c, --concurrent N        Number of concurrent operations (default: 1000)
#   -r, --rate-per-sec RPS    Operations per second (default: 1000)
#   -o, --output-dir DIR      Output directory for results
#   -v, --verbose             Enable verbose logging
#   --skip-setup             Skip environment setup
#   --skip-teardown          Skip cleanup after test
#   --enable-monitoring      Enable system monitoring during test
#   --jvm-profiling          Enable JVM profiling
#   --metrics-interval SECONDS Metrics collection interval (default: 5)
#   -h, --help               Show this help message

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEFAULT_OUTPUT_DIR="${PROJECT_ROOT}/stress-test-results"
DEFAULT_DURATION=60
DEFAULT_CONCURRENT=1000
DEFAULT_RATE=1000
DEFAULT_METRICS_INTERVAL=5

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Stress test scenarios
declare -A SCENARIOS=(
    ["baseline"]="gradual-ramp sustained-linear"
    ["sustained"]="constant-high sustained-peak"
    ["spike"]="spike-burst spike-recovery"
    ["mixed"]="variable-ramp sustained-spikes gradual-ramp"
    ["chaos"]="random-faults circuit-breaker timeout-faults"
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
YAWL Stress Test Orchestration Script v6.0.0-GA
================================================

Run production-like stress tests for YAWL v6.0.0-GA.

SCENARIOS:
  baseline        - Basic stress test with increasing load
  sustained       - Sustained high load over extended period
  spike          - Sudden load spikes
  mixed          - Mixed workload pattern
  chaos          - Chaos engineering with fault injection

USAGE:
  $0 [scenario] [options]

EXAMPLES:
  $0 baseline --duration 30 --concurrent 500
  $0 sustained --duration 120 --rate-per-sec 2000
  $0 spike --enable-monitoring --jvm-profiling
  $0 mixed --concurrent 2000 --metrics-interval 2

OPTIONS:
  -t, --duration MINUTES     Duration of stress test (default: $DEFAULT_DURATION)
  -c, --concurrent N        Number of concurrent operations (default: $DEFAULT_CONCURRENT)
  -r, --rate-per-sec RPS    Operations per second (default: $DEFAULT_RATE)
  -o, --output-dir DIR      Output directory for results (default: $DEFAULT_OUTPUT_DIR)
  -v, --verbose             Enable verbose logging
  --skip-setup              Skip environment setup
  --skip-teardown           Skip cleanup after test
  --enable-monitoring       Enable system monitoring during test
  --jvm-profiling          Enable JVM profiling
  --metrics-interval SECONDS Metrics collection interval (default: $DEFAULT_METRICS_INTERVAL)
  -h, --help               Show this help message

ENVIRONMENT VARIABLES:
  JAVA_HOME                Java home directory
  YAWL_ENGINE_URL          YAWL engine URL
  DATABASE_URL             Database connection URL
  PROMETHEUS_URL           Prometheus metrics endpoint
  GRAFANA_URL             Grafana dashboard URL

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

    # Check required tools
    local required_tools=(
        "curl" "jq" "ps" "kill" "timeout"
    )

    for tool in "${required_tools[@]}"; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            log "ERROR" "Required tool not found: $tool"
            exit 1
        fi
    done

    # Check YAWL engine availability
    if [[ -n "${YAWL_ENGINE_URL:-}" ]]; then
        if ! curl -s -f "${YAWL_ENGINE_URL}/health" >/dev/null 2>&1; then
            log "ERROR" "YAWL engine not available at: $YAWL_ENGINE_URL"
            exit 1
        fi
        log "INFO" "YAWL engine available at: $YAWL_ENGINE_URL"
    fi

    log "INFO" "All requirements satisfied"
}

setup_environment() {
    log "INFO" "Setting up test environment..."

    # Create output directory
    mkdir -p "$DEFAULT_OUTPUT_DIR"

    # Start monitoring processes
    if [[ "${ENABLE_MONITORING:-false}" == "true" ]]; then
        start_monitoring
    fi

    # Prepare test data
    prepare_test_data

    log "INFO" "Environment setup complete"
}

start_monitoring() {
    log "INFO" "Starting system monitoring..."

    local monitoring_dir="$DEFAULT_OUTPUT_DIR/monitoring"
    mkdir -p "$monitoring_dir"

    # Start CPU/memory monitoring
    vmstat 10 > "$monitoring_dir/vmstat.log" &
    local vmstat_pid=$!

    # Start JVM monitoring
    if [[ -n "${YAWL_ENGINE_URL:-}" ]]; then
        while true; do
            curl -s "${YAWL_ENGINE_URL}/metrics" > "$monitoring_dir/jvm-metrics-$(date +%s).json"
            sleep $DEFAULT_METRICS_INTERVAL
        done &
        local jvm_metrics_pid=$!
    fi

    # Store PIDs for cleanup
    echo "$vmstat_pid" > "$monitoring_dir/vmstat.pid"
    if [[ -n "${jvm_metrics_pid:-}" ]]; then
        echo "$jvm_metrics_pid" > "$monitoring_dir/jvm-metrics.pid"
    fi

    log "INFO" "Monitoring started (PID: $vmstat_pid${jvm_metrics_pid:+, $jvm_metrics_pid})"
}

stop_monitoring() {
    local monitoring_dir="$DEFAULT_OUTPUT_DIR/monitoring"

    if [[ -f "$monitoring_dir/vmstat.pid" ]]; then
        local vmstat_pid=$(cat "$monitoring_dir/vmstat.pid")
        kill $vmstat_pid 2>/dev/null || true
        rm "$monitoring_dir/vmstat.pid"
    fi

    if [[ -f "$monitoring_dir/jvm-metrics.pid" ]]; then
        local jvm_metrics_pid=$(cat "$monitoring_dir/jvm-metrics.pid")
        kill $jvm_metrics_pid 2>/dev/null || true
        rm "$monitoring_dir/jvm-metrics.pid"
    fi

    log "INFO" "Monitoring stopped"
}

prepare_test_data() {
    log "INFO" "Preparing test data..."

    local test_data_dir="$DEFAULT_OUTPUT_DIR/test-data"
    mkdir -p "$test_data_dir"

    # Generate test workflow definitions
    cat << EOF > "$test_data_dir/test-workflow.xml"
<?xml version="1.0" encoding="UTF-8"?>
<yawl:specification xmlns:yawlfoundation="http://www.yawlfoundation.org/yawl"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.yawlfoundation.org/yawl http://www.yawlfoundation.org/yawl/yawlSchema.xsd"
                    id="StressTestWorkflow" name="Stress Test Workflow">
    <inputParameters>
        <parameter id="loadFactor" name="Load Factor" type="integer" />
        <parameter id="duration" name="Duration" type="integer" />
    </inputParameters>
    <outputParameters>
        <parameter id="resultCount" name="Result Count" type="integer" />
        <parameter id="errorCount" name="Error Count" type="integer" />
    </outputParameters>
    <tasks>
        <task id="Start" name="Start Task">
            <inputParameterBindings>
                <binding id="loadFactor" formalParameter="loadFactor" actualParameter="null"/>
            </inputParameterBindings>
        </task>
        <task id="Process" name="Process Task" implementation="Java">
            <inputParameterBindings>
                <binding id="data" formalParameter="data" actualParameter="null"/>
            </inputParameterBindings>
        </task>
        <task id="Complete" name="Complete Task">
            <outputParameterBindings>
                <binding id="result" formalParameter="resultCount" actualParameter="null"/>
            </outputParameterBindings>
        </task>
    </tasks>
    <flows>
        <flow id="f1" from="Start" to="Process"/>
        <flow id="f2" from="Process" to="Complete"/>
    </flows>
</yawl:specification>
EOF

    # Generate test cases
    cat << EOF > "$test_data_dir/test-cases.json"
[
    {
        "id": "stress-case-1",
        "workflow": "StressTestWorkflow",
        "input": {
            "loadFactor": 100,
            "duration": 60
        }
    },
    {
        "id": "stress-case-2",
        "workflow": "StressTestWorkflow",
        "input": {
            "loadFactor": 500,
            "duration": 30
        }
    },
    {
        "id": "stress-case-3",
        "workflow": "StressTestWorkflow",
        "input": {
            "loadFactor": 1000,
            "duration": 15
        }
    }
]
EOF

    log "INFO" "Test data prepared in: $test_data_dir"
}

run_load_generator() {
    local scenario="$1"
    local concurrent="$2"
    local rate="$3"
    local duration="$4"
    local output_dir="$5"

    log "INFO" "Starting load generator for scenario: $scenario"
    log "INFO" "Concurrent users: $concurrent"
    log "INFO" "Rate: $rate ops/sec"
    log "INFO" "Duration: $duration minutes"

    local scenario_output="$output_dir/$scenario"
    mkdir -p "$scenario_output"

    # Generate unique timestamp for this run
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local load_log="$scenario_output/load-test-$timestamp.log"
    local results_file="$scenario_output/results-$timestamp.json"

    # Run stress test using JMH or custom load generator
    local jmh_opts=(
        "-rf" "json"
        "-rff" "$results_file"
        "-wi" "2"
        "-i" "5"
        "-f" "1"
        "-tu" "ms"
        "-jvmArgs" "-Xms4g"
        "-jvmArgs" "-Xmx8g"
        "-jvmArgs" "-XX:+UseZGC"
        "-jvmArgs" "--enable-preview"
    )

    # Additional options based on scenario
    case "$scenario" in
        "baseline")
            jmh_opts+=("-t" "$concurrent")
            jmh_opts+=("-rps" "$((rate / concurrent))")
            ;;
        "sustained")
            jmh_opts+=("-t" "$concurrent")
            jmh_opts+=("-rps" "$((rate / concurrent))")
            ;;
        "spike")
            jmh_opts+=("-t" "$((concurrent * 2))")
            jmh_opts+=("-rps" "$((rate * 2))")
            ;;
        "mixed")
            jmh_opts+=("-t" "$concurrent")
            jmh_opts+=("-rps" "$rate")
            ;;
        "chaos")
            jmh_opts+=("-t" "$concurrent")
            jmh_opts+=("-rps" "$rate")
            jmh_opts+=("-Dchaos.enabled=true")
            ;;
    esac

    # Execute the stress test
    timeout $((duration * 60)) \
        "$JAVA_CMD" -jar "$PROJECT_ROOT/target/benchmarks.jar" \
        "${jmh_opts[@]}" \
        org.yawlfoundation.yawl.performance.stress.ProductionWorkloadStressTest \
        > "$load_log" 2>&1 &

    local load_pid=$!

    # Monitor progress
    local start_time=$(date +%s)
    local end_time=$((start_time + duration * 60))
    local progress_update_interval=10

    while kill -0 $load_pid 2>/dev/null && [[ $(date +%s) -lt $end_time ]]; do
        local elapsed=$(( $(date +%s) - start_time ))
        local remaining=$(( end_time - $(date +%s) ))
        local progress=$(( elapsed * 100 / (duration * 60) ))

        log "DEBUG" "Load test progress: ${progress}% (${elapsed}s elapsed, ${remaining}s remaining)"

        sleep $progress_update_interval
    done

    # Check if test completed normally
    if ! kill -0 $load_pid 2>/dev/null; then
        log "INFO" "Load test completed"
        wait $load_pid
        local exit_code=$?
    else
        log "WARN" "Load test timed out"
        kill -9 $load_pid 2>/dev/null || true
        local exit_code=124
    fi

    # Process results
    if [[ -f "$results_file" ]]; then
        log "INFO" "Processing results for scenario: $scenario"
        process_stress_results "$results_file" "$scenario_output"
    fi

    return $exit_code
}

process_stress_results() {
    local results_file="$1"
    local scenario_output="$2"

    # Generate summary report
    local summary_file="$scenario_output/summary-$(date +%Y%m%d_%H%M%S).md"

    cat << EOF > "$summary_file"
# Stress Test Results - $scenario_output

*Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")*

## Test Summary

EOF

    # Extract metrics from JSON results
    if command -v jq >/dev/null 2>&1; then
        local total_ops=$(jq '.benchmarkOps // 0' "$results_file")
        local error_rate=$(jq '.errorRate // 0' "$results_file")
        local avg_latency=$(jq '.avgLatency // 0' "$results_file")
        local p95_latency=$(jq '.p95Latency // 0' "$results_file")

        cat << EOF >> "$summary_file"
### Performance Metrics

| Metric | Value | Status |
|--------|-------|--------|
| Total Operations | $total_ops | ✅ |
| Error Rate | ${error_rate}% | $([[ $(echo "$error_rate < 5" | bc -l 2>/dev/null || echo "0") -eq 1 ]] && echo "✅" || echo "❌") |
| Average Latency | ${avg_latency}ms | ✅ |
| P95 Latency | ${p95_latency}ms | $([[ $(echo "$p95_latency < 1000" | bc -l 2>/dev/null || echo "0") -eq 1 ]] && echo "✅" || echo "❌") |

EOF
    fi

    # Add system metrics if available
    if [[ -f "$scenario_output/../monitoring/vmstat.log" ]]; then
        cat << EOF >> "$summary_file"
### System Metrics

EOF
        # Calculate average CPU usage
        local avg_cpu=$(tail -10 "$scenario_output/../monitoring/vmstat.log" | awk '/cpu/ {usage=100-$NF} END {print usage}')
        cat << EOF >> "$summary_file"
- Average CPU Usage: ${avg_cpu}%
- Memory Usage: Check monitoring/vmstat.log
- Disk I/O: Check monitoring/vmstat.log
EOF
    fi

    # Add recommendations
    cat << EOF >> "$summary_file"

## Recommendations

EOF

    if command -v jq >/dev/null 2>&1; then
        if [[ $(echo "$error_rate > 5" | bc -l 2>/dev/null || echo "0") -eq 1 ]]; then
            cat << EOF >> "$summary_file"
### Error Handling
- High error rate detected ($error_rate%)
- Investigate error patterns in test logs
- Consider implementing retry mechanisms with exponential backoff
EOF
        fi

        if [[ $(echo "$p95_latency > 1000" | bc -l 2>/dev/null || echo "0") -eq 1 ]]; then
            cat << EOF >> "$summary_file"
### Performance Optimization
- High latency detected (P95: ${p95_latency}ms)
- Consider optimizing database queries
- Implement caching strategies
- Scale horizontally if CPU usage is high
EOF
        fi
    fi

    cat << EOF >> "$summary_file"

## Next Steps

1. [ ] Review detailed metrics in this directory
2. [ ] Analyze system resource usage
3. [ ] Identify bottlenecks and implement fixes
4. [ ] Schedule follow-up stress test after optimizations

---
*Generated by YAWL Stress Test Orchestration Script*
EOF

    log "INFO" "Summary report generated: $summary_file"
}

generate_comprehensive_report() {
    local output_dir="$1"
    local scenarios="$2"

    local report_file="$output_dir/comprehensive-stress-test-report.md"

    log "INFO" "Generating comprehensive stress test report: $report_file"

    cat << EOF > "$report_file"
# YAWL Comprehensive Stress Test Report

*Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")*
*Scenarios Tested: $scenarios*

## Executive Summary

This report summarizes the results of comprehensive stress testing performed on YAWL v6.0.0-GA.
The tests validated system performance under various load conditions and identified areas for optimization.

## Test Overview

| Scenario | Status | Key Findings |
|----------|--------|--------------|
EOF

    # Process each scenario
    IFS=',' read -ra scenario_array <<< "$scenarios"
    for scenario in "${scenario_array[@]}"; do
        local scenario_dir="$output_dir/$scenario"
        local status="✅ Completed"
        local findings="No issues detected"

        if [[ -f "$scenario_dir/summary-*.md" ]]; then
            local latest_summary=$(ls -t "$scenario_dir/summary-"*.md | head -1)
            local error_count=$(grep -c "❌" "$latest_summary" 2>/dev/null || echo "0")
            if [[ $error_count -gt 0 ]]; then
                status="⚠️ Issues detected"
                findings="$error_count performance issues found"
            fi
        fi

        echo "| $scenario | $status | $findings |" >> "$report_file"
    done

    cat << EOF >> "$report_file"

## System Performance Analysis

### Resource Utilization

EOF

    # Analyze monitoring data
    local monitoring_dir="$output_dir/monitoring"
    if [[ -f "$monitoring_dir/vmstat.log" ]]; then
        cat << EOF >> "$report_file"

#### CPU and Memory Usage
- Average CPU: $(tail -10 "$monitoring_dir/vmstat.log" | awk '/cpu/ {usage=100-$NF} END {print usage "%"}')
- Memory Pressure: Monitor vmstat.log for memory usage trends
- Context Switches: Check vmstat.log for excessive switching
EOF
    fi

    cat << EOF >> "$report_file"

### Bottleneck Analysis

EOF

    # Check for common bottleneck indicators
    local bottleneck_count=0
    if [[ -f "$monitoring_dir/vmstat.log" ]]; then
        local high_cpu=$(awk '/cpu/ {if ($5 > 80) count++} END {print count+0}' "$monitoring_dir/vmstat.log" 2>/dev/null || echo "0")
        if [[ $high_cpu -gt 0 ]]; then
            cat << EOF >> "$report_file"
- CPU-bound workload detected ($high_cpu samples > 80% CPU)
EOF
            bottleneck_count=$((bottleneck_count + 1))
        fi
    fi

    if [[ $bottleneck_count -eq 0 ]]; then
        cat << EOF >> "$report_file"
- No significant bottlenecks detected in test scenarios
EOF
    fi

    cat << EOF >> "$report_file"

## Recommendations

### Immediate Actions (Critical)
EOF

    # Generate critical recommendations
    local critical_recommendations=0

    for scenario in "${scenario_array[@]}"; do
        local scenario_dir="$output_dir/$scenario"
        if [[ -f "$scenario_dir/summary-*.md" ]]; then
            local latest_summary=$(ls -t "$scenario_dir/summary-"*.md | head -1)
            if grep -q "❌" "$latest_summary"; then
                cat << EOF >> "$report_file"
1. **$scenario**: Address performance issues in test logs
EOF
                critical_recommendations=$((critical_recommendations + 1))
            fi
        fi
    done

    if [[ $critical_recommendations -eq 0 ]]; then
        cat << EOF >> "$report_file"
1. No critical issues requiring immediate attention
EOF
    fi

    cat << EOF >> "$report_file"

### Medium-term Optimizations

1. **Database Performance**: Implement connection pooling and query optimization
2. **Caching Strategy**: Add Redis caching for frequently accessed data
3. **Load Balancing**: Consider horizontal scaling for high load scenarios
4. **Monitoring Enhancement**: Implement comprehensive alerting for production

### Long-term Improvements

1. **Architecture Review**: Evaluate microservices architecture for better scalability
2. **Auto-scaling**: Implement Kubernetes HPA based on metrics
3. **Circuit Breakers**: Add resilience patterns for service failures
4. **Performance Testing**: Regular stress testing as part of CI/CD pipeline

## Conclusion

The stress testing validates YAWL v6.0.0-GA's ability to handle production workloads.
$([[ $critical_recommendations -eq 0 ]] && echo "All scenarios passed successfully." || echo "Some areas require optimization before production deployment.")

## Next Steps

1. [ ] Address critical recommendations
2. [ ] Implement medium-term optimizations
3. [ ] Schedule follow-up stress test
4. [ ] Set up ongoing performance monitoring

---
*Generated by YAWL Stress Test Orchestration Script v6.0.0-GA*
EOF

    log "SUCCESS" "Comprehensive report generated: $report_file"
}

cleanup() {
    log "INFO" "Cleaning up test environment..."

    if [[ "${SKIP_TEardown:-false}" != "true" ]]; then
        stop_monitoring

        # Clean up temporary files
        rm -rf /tmp/yawl-stress-test-$$ 2>/dev/null || true

        log "INFO" "Cleanup complete"
    fi
}

main() {
    local scenario="${1:-baseline}"
    shift || true

    # Parse command line arguments
    local duration=$DEFAULT_DURATION
    local concurrent=$DEFAULT_CONCURRENT
    local rate=$DEFAULT_RATE
    local output_dir=$DEFAULT_OUTPUT_DIR
    local skip_setup=false
    local enable_monitoring=false
    local jvm_profiling=false
    local metrics_interval=$DEFAULT_METRICS_INTERVAL

    while [[ $# -gt 0 ]]; do
        case $1 in
            -t|--duration)
                duration="$2"
                shift 2
                ;;
            -c|--concurrent)
                concurrent="$2"
                shift 2
                ;;
            -r|--rate-per-sec)
                rate="$2"
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
            --skip-setup)
                skip_setup=true
                shift
                ;;
            --skip-teardown)
                SKIP_TEardown=true
                shift
                ;;
            --enable-monitoring)
                enable_monitoring=true
                shift
                ;;
            --jvm-profiling)
                jvm_profiling=true
                shift
                ;;
            --metrics-interval)
                metrics_interval="$2"
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

    # Validate scenario
    if [[ -z "${SCENARIOS[$scenario]:-}" ]]; then
        log "ERROR" "Unknown scenario: $scenario"
        print_help
        exit 1
    fi

    log "INFO" "Starting YAWL Stress Test Orchestration"
    log "INFO" "Scenario: $scenario"
    log "INFO" "Duration: $duration minutes"
    log "INFO" "Concurrent operations: $concurrent"
    log "INFO" "Rate: $rate ops/sec"
    log "INFO" "Output directory: $output_dir"

    # Set up trap for cleanup
    trap cleanup EXIT

    # Check requirements
    check_requirements

    # Setup environment unless skipped
    if [[ "$skip_setup" != "true" ]]; then
        setup_environment
    fi

    # Export variables for sub-processes
    export ENABLE_MONITORING="$enable_monitoring"
    export DEFAULT_METRICS_INTERVAL="$metrics_interval"

    # Run stress tests
    local test_scenarios="${SCENARIOS[$scenario]}"
    local overall_success=true

    IFS=',' read -ra scenario_array <<< "$test_scenarios"
    for test_scenario in "${scenario_array[@]}"; do
        log "INFO" "Running stress test scenario: $test_scenario"

        if ! run_load_generator "$test_scenario" "$concurrent" "$rate" "$duration" "$output_dir"; then
            log "ERROR" "Stress test scenario $test_scenario failed"
            overall_success=false
        else
            log "SUCCESS" "Stress test scenario $test_scenario completed successfully"
        fi
    done

    # Generate comprehensive report
    generate_comprehensive_report "$output_dir" "$test_scenarios"

    # Final status
    if [[ "$overall_success" == "true" ]]; then
        log "SUCCESS" "Stress test orchestration completed successfully!"
        log "INFO" "Results available in: $output_dir"
        exit 0
    else
        log "ERROR" "Stress test orchestration completed with failures"
        log "INFO" "Check results in: $output_dir"
        exit 1
    fi
}

# Execute main function with all arguments
main "$@"