#!/bin/bash

# YAWL Regression Detection Script v6.0.0-GA
# ============================================
#
# This script runs regression tests to detect performance and functionality regressions.
# Compares current performance with baseline and identifies deviations.
#
# Usage:
#   ./scripts/run-regression-tests.sh [mode] [options]
#
# Modes:
#   performance     - Performance regression detection
#   functionality  - Functional regression detection
#   comprehensive   - Both performance and functional regressions
#   baseline       - Establish new baseline
#
# Options:
#   -b, --baseline FILE     Baseline results file (default: performance-baseline.json)
#   -t, --threshold PERCENT  Threshold percentage for regression detection (default: 10%)
   -d, --days N            Number of days to analyze (default: 7)
   -o, --output-dir DIR    Output directory for results
   -v, --verbose           Enable verbose logging
   --include-trends        Include trend analysis in regression detection
   --auto-fix             Attempt auto-fix for common regressions
   --enable-notifications  Enable regression notifications
   --slack-webhook URL     Slack webhook URL for notifications
   -h, --help             Show this help message

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEFAULT_OUTPUT_DIR="${PROJECT_ROOT}/regression-results"
DEFAULT_BASELINE_FILE="${PROJECT_ROOT}/performance-baseline.json"
DEFAULT_THRESHOLD=10
DEFAULT_DAYS=7

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Regression modes
declare -A REGRESSION_MODES=(
    ["performance"]="latency throughput memory cpu"
    ["functionality"]="correctness compliance integration"
    ["comprehensive"]="latency throughput memory cpu correctness compliance integration"
    ["baseline"]="latency throughput memory cpu"
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
YAWL Regression Detection Script v6.0.0-GA
=============================================

Run regression tests to detect performance and functionality regressions.

MODES:
  performance     - Performance regression detection
  functionality  - Functional regression detection
  comprehensive   - Both performance and functional regressions
  baseline       - Establish new baseline

USAGE:
  $0 [mode] [options]

EXAMPLES:
  $0 performance --threshold 15 --verbose
  $0 comprehensive --baseline /path/to/baseline.json --include-trends
  $0 baseline --auto-fix --enable-notifications
  $0 functionality --days 30 --slack-webhook https://hooks.slack.com/...

OPTIONS:
  -b, --baseline FILE     Baseline results file (default: $DEFAULT_BASELINE_FILE)
  -t, --threshold PERCENT  Threshold percentage for regression detection (default: $DEFAULT_THRESHOLD%)
  -d, --days N            Number of days to analyze (default: $DEFAULT_DAYS)
  -o, --output-dir DIR    Output directory for results (default: $DEFAULT_OUTPUT_DIR)
  -v, --verbose           Enable verbose logging
  --include-trends        Include trend analysis in regression detection
  --auto-fix             Attempt auto-fix for common regressions
  --enable-notifications  Enable regression notifications
  --slack-webhook URL     Slack webhook URL for notifications
  -h, --help             Show this help message

ENVIRONMENT VARIABLES:
  JAVA_HOME               Java home directory
  YAWL_ENGINE_URL         YAWL engine URL
  DATABASE_URL           Database connection URL
  PROMETHEUS_URL         Prometheus metrics endpoint
  GRAFANA_URL           Grafana dashboard URL

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
        "curl" "jq" "bc" "awk" "sort" "uniq"
    )

    for tool in "${required_tools[@]}"; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            log "ERROR" "Required tool not found: $tool"
            exit 1
        fi
    done

    # Check baseline file if not creating new baseline
    if [[ "${MODE:-}" != "baseline" && ! -f "$DEFAULT_BASELINE_FILE" ]]; then
        log "WARN" "Baseline file not found: $DEFAULT_BASELINE_FILE"
        log "WARN" "Creating new baseline..."
    fi

    log "INFO" "All requirements satisfied"
}

run_benchmarks() {
    local mode="$1"
    local output_dir="$2"
    local timestamp=$(date +%Y%m%d_%H%M%S)

    log "INFO" "Running benchmarks for regression detection"

    local benchmark_dir="$output_dir/benchmarks-$timestamp"
    mkdir -p "$benchmark_dir"

    # Run benchmarks based on mode
    local benchmarks=(${REGRESSION_MODES[$mode]})

    # JMH benchmarks for performance metrics
    if [[ " ${benchmarks[*]} " =~ "latency" ]] || [[ " ${benchmarks[*]} " =~ "throughput" ]]; then
        log "INFO" "Running JMH performance benchmarks"

        "$JAVA_CMD" -jar "$PROJECT_ROOT/target/benchmarks.jar" \
            -rf json \
            -rff "$benchmark_dir/performance.json" \
            -wi 3 \
            -i 5 \
            -f 1 \
            -tu ms \
            -jvmArgs "-Xms2g" \
            -jvmArgs "-Xmx4g" \
            -jvmArgs "-XX:+UseZGC" \
            -jvmArgs "--enable-preview" \
            org.yawlfoundation.yawl.performance.jmh.AllBenchmarksRunner > "$benchmark_dir/performance.log" 2>&1

        if [[ $? -eq 0 ]]; then
            log "SUCCESS" "Performance benchmarks completed"
        else
            log "ERROR" "Performance benchmarks failed"
            return 1
        fi
    fi

    # Functional tests
    if [[ " ${benchmarks[*]} " =~ "correctness" ]] || [[ " ${benchmarks[*]} " =~ "compliance" ]]; then
        log "INFO" "Running functional tests"

        mvn test -Dtest="*Test" -q \
            -Dspring.profiles.active=regression \
            -Dspring.test.execution.parallel.enabled=false \
            > "$benchmark_dir/functional.log" 2>&1

        if [[ $? -eq 0 ]]; then
            log "SUCCESS" "Functional tests completed"
        else
            log "ERROR" "Functional tests failed"
            return 1
        fi
    fi

    # Integration tests
    if [[ " ${benchmarks[*]} " =~ "integration" ]]; then
        log "INFO" "Running integration tests"

        mvn test -Dtest="*IntegrationTest" -q \
            -Dspring.profiles.active=regression \
            -Dspring.test.execution.parallel.enabled=false \
            > "$benchmark_dir/integration.log" 2>&1

        if [[ $? -eq 0 ]]; then
            log "SUCCESS" "Integration tests completed"
        else
            log "ERROR" "Integration tests failed"
            return 1
        fi
    fi

    # Collect system metrics
    collect_system_metrics "$benchmark_dir"

    log "SUCCESS" "All benchmarks completed"
    return 0
}

collect_system_metrics() {
    local output_dir="$1"

    log "INFO" "Collecting system metrics"

    local metrics_file="$output_dir/system-metrics.json"

    cat << EOF > "$metrics_file"
{
    "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
    "system_info": {
        "hostname": "$(hostname)",
        "java_version": "$("$JAVA_CMD" -version 2>&1 | head -n 1)",
        "os_info": "$(uname -a)"
    },
    "metrics": {
EOF

    # Collect CPU metrics
    if command -v top >/dev/null 2>&1; then
        local cpu_usage=$(top -bn1 | grep "Cpu(s)" | sed "s/.*, *\([0-9.]*\)%* id.*/\1/" | awk '{print 100 - $1"%"}')
        cat << EOF >> "$metrics_file"
        "cpu_usage": "$cpu_usage",
EOF
    fi

    # Collect memory metrics
    if command -v free >/dev/null 2>&1; then
        local memory_info=$(free -m | awk 'NR==2{printf "{ \"total\": %d, \"used\": %d, \"free\": %d, \"percentage\": \"%.2f%%\" }", $2, $3, $4, ($3/$2)*100.0}')
        cat << EOF >> "$metrics_file"
        "memory_info": $memory_info,
EOF
    fi

    # Collect disk metrics
    if command -v df >/dev/null 2>&1; then
        local disk_info=$(df -h | awk '$NF=="/"{printf "{ \"total\": \"%s\", \"used\": \"%s\", \"available\": \"%s\", \"percentage\": \"%s\" }", $2, $3, $4, $5}')
        cat << EOF >> "$metrics_file"
        "disk_info": $disk_info,
EOF
    fi

    # Close JSON
    sed -i 's/,$//' "$metrics_file"
    echo "    }" >> "$metrics_file"
    echo "}" >> "$metrics_file"

    log "INFO" "System metrics collected"
}

establish_baseline() {
    local mode="$1"
    local baseline_file="$2"
    local output_dir="$3"

    log "INFO" "Establishing baseline for mode: $mode"

    # Run benchmarks to establish baseline
    if ! run_benchmarks "$mode" "$output_dir"; then
        log "ERROR" "Failed to establish baseline"
        return 1
    fi

    # Aggregate results into baseline
    local latest_benchmark=$(ls -td "$output_dir"/benchmarks-* | head -1)
    local aggregated_data=$(aggregate_benchmark_results "$latest_benchmark")

    # Save baseline
    echo "$aggregated_data" | jq '.' > "$baseline_file"

    log "SUCCESS" "Baseline established: $baseline_file"
    return 0
}

detect_performance_regressions() {
    local baseline_file="$1"
    current_file="$2"
    threshold="$3"
    output_dir="$4"

    log "INFO" "Detecting performance regressions"

    local regression_file="$output_dir/performance-regressions.json"
    local report_file="$output_dir/performance-regression-report.md"

    # Compare each metric
    cat << EOF > "$regression_file"
{
    "analysis_timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
    "threshold_percent": $threshold,
    "regressions": []
}
EOF

    local baseline_data=$(cat "$baseline_file")
    local current_data=$(cat "$current_file")

    # Compare latency metrics
    compare_metrics "$baseline_data" "$current_data" "latency" "$threshold" "$regression_file"

    # Compare throughput metrics
    compare_metrics "$baseline_data" "$current_data" "throughput" "$threshold" "$regression_file"

    # Compare memory metrics
    compare_metrics "$baseline_data" "$current_data" "memory" "$threshold" "$regression_file"

    # Compare CPU metrics
    compare_metrics "$baseline_data" "$current_data" "cpu" "$threshold" "$regression_file"

    # Generate regression report
    generate_regression_report "performance" "$regression_file" "$report_file"

    log "SUCCESS" "Performance regression analysis complete"
    return 0
}

compare_metrics() {
    local baseline_data="$1"
    local current_data="$2"
    local metric_type="$3"
    local threshold="$4"
    local output_file="$5"

    # Extract relevant metrics
    local baseline_values=$(echo "$baseline_data" | jq ".${metric_type}_metrics[]")
    local current_values=$(echo "$current_data" | jq ".${metric_type}_metrics[]")

    if [[ -z "$baseline_values" || -z "$current_values" ]]; then
        return
    fi

    # Compare each metric
    echo "$baseline_values" | jq -r '.name' | while read -r metric_name; do
        local baseline_value=$(echo "$baseline_data" | jq ".${metric_type}_metrics[] | select(.name == \"$metric_name\") | .value")
        local current_value=$(echo "$current_data" | jq ".${metric_type}_metrics[] | select(.name == \"$metric_name\") | .value")

        if [[ -n "$baseline_value" && -n "$current_value" ]]; then
            local baseline_num=$(echo "$baseline_value" | bc -l 2>/dev/null || echo "0")
            local current_num=$(echo "$current_value" | bc -l 2>/dev/null || echo "0")
            local change_percent=$(echo "scale=2; ($current_num - $baseline_num) / $baseline_num * 100" | bc -l 2>/dev/null || echo "0")

            # Check if it's a regression (positive change for latency, negative for others)
            local is_regression=false
            case "$metric_type" in
                "latency")
                    if (( $(echo "$change_percent > $threshold" | bc -l 2>/dev/null || echo "0") == 1 )); then
                        is_regression=true
                    fi
                    ;;
                *)
                    if (( $(echo "$change_percent < -$threshold" | bc -l 2>/dev/null || echo "0") == 1 )); then
                        is_regression=true
                    fi
                    ;;
            esac

            if [[ "$is_regression" == "true" ]]; then
                local regression='{
                    "metric_name": "'"$metric_name"'",
                    "metric_type": "'"$metric_type"'",
                    "baseline_value": '"$baseline_value"',
                    "current_value": '"$current_value"',
                    "change_percent": '"$change_percent"',
                    "regression_detected": true
                }'

                jq ".regressions += [$regression]" "$output_file" > "$output_file.tmp"
                mv "$output_file.tmp" "$output_file"
            fi
        fi
    done
}

detect_functionality_regressions() {
    local baseline_file="$1"
    current_file="$2"
    output_dir="$3"

    log "INFO" "Detecting functionality regressions"

    local regression_file="$output_dir/functionality-regressions.json"
    local report_file="$output_dir/functionality-regression-report.md"

    # Compare functional test results
    cat << EOF > "$regression_file"
{
    "analysis_timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
    "regressions": []
}
EOF

    # Test result comparison
    local baseline_tests=$(jq '.functional_results.total_passed // 0' "$baseline_file")
    local current_tests=$(jq '.functional_results.total_passed // 0' "$current_file")

    if [[ $current_tests -lt $baseline_tests ]]; then
        local test_regression='{
            "type": "functional_test_count",
            "baseline_tests": '"$baseline_tests"',
            "current_tests": '"$current_tests"',
            "difference": '$((baseline_tests - current_tests))',
            "regression_detected": true
        }'

        jq ".regressions += [$test_regression]" "$regression_file" > "$regression_file.tmp"
        mv "$regression_file.tmp" "$regression_file"
    fi

    # Generate regression report
    generate_regression_report "functionality" "$regression_file" "$report_file"

    log "SUCCESS" "Functionality regression analysis complete"
    return 0
}

generate_regression_report() {
    local mode="$1"
    local regression_file="$2"
    local report_file="$3"

    log "INFO" "Generating regression report for mode: $mode"

    local regression_count=$(jq '.regressions | length' "$regression_file")

    cat << EOF > "$report_file"
# YAWL Regression Detection Report - $mode

*Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")*

## Executive Summary

EOF

    if [[ $regression_count -gt 0 ]]; then
        cat << EOF
❌ **REGRESSIONS DETECTED**: $regression_count regressions found

### Critical Issues
EOF
    else
        cat << EOF
✅ **NO REGRESSIONS**: All metrics within acceptable thresholds

### System Health
EOF
    fi

    cat << EOF

## Regression Details

EOF

    # Display each regression
    jq -r '.regressions[] | "\(.metric_name | . // .type): \(.change_percent // .difference)% change (\(if .regression_detected then "REGRESSION" else "NO REGRESSION" end))"' \
        "$regression_file" >> "$report_file"

    cat << EOF >> "$report_file"

## Recommendations

EOF

    if [[ $regression_count -gt 0 ]]; then
        cat << EOF
### Immediate Actions (Critical)

1. **Performance Degradation**:
   - Investigate root cause of performance degradation
   - Check for recent code changes affecting performance
   - Review system resource utilization

2. **Functional Issues**:
   - Analyze failing tests and identify common patterns
   - Check for API contract changes
   - Verify data integrity

### Investigation Steps

1. **Code Review**:
   - Review recent commits for performance-related changes
   - Check for database query optimizations
   - Look for caching opportunities

2. **System Analysis**:
   - Monitor resource usage during peak loads
   - Check for memory leaks
   - Analyze thread contention

### Long-term Improvements

1. **Performance Monitoring**: Implement comprehensive monitoring
2. **Testing Strategy**: Enhance test coverage for performance-critical paths
3. **Automation**: Integrate regression detection into CI/CD pipeline

EOF
    else
        cat << EOF
1. **Continue Monitoring**: Maintain current monitoring strategy
2. **Performance Baseline**: Update baseline with current performance
3. **Testing Enhancement**: Add more comprehensive test cases

EOF
    fi

    cat << EOF >> "$report_file"

## Next Steps

1. [ ] Review detailed regression analysis
2. [ ] Implement recommended fixes
3. [ ] Schedule follow-up regression test
4. [ ] Update performance baselines

---
*Generated by YAWL Regression Detection Script v6.0.0-GA*
EOF

    log "SUCCESS" "Regression report generated: $report_file"
}

aggregate_benchmark_results() {
    local benchmark_dir="$1"

    # Aggregate performance metrics
    local performance_data=$(cat "$benchmark_dir/performance.json" 2>/dev/null || echo '{}')
    local functional_data=$(cat "$benchmark_dir/system-metrics.json" 2>/dev/null || echo '{}')

    # Create aggregated result
    cat << EOF | jq -s '.[0] * .[1] * .[2]' <(echo "$performance_data") <(echo "$functional_data") <(cat)
{
    "aggregation_timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
    "performance_metrics": $performance_data,
    "system_metrics": $functional_data,
    "latency_metrics": [
        {
            "name": "average_latency",
            "value": $(echo "$performance_data" | jq '.benchmarks[] | select(.benchmark | contains("Latency")) | .primaryMetric.score' | awk '{sum+=$1; count++} END {print sum/count/count > 0 ? sum/count : 0}' 2>/dev/null || echo "0"),
            "unit": "ms"
        }
    ],
    "throughput_metrics": [
        {
            "name": "throughput",
            "value": $(echo "$performance_data" | jq '.benchmarks[] | select(.benchmark | contains("Throughput")) | .primaryMetric.score' | awk '{sum+=$1; count++} END {print sum/count/count > 0 ? sum/count : 0}' 2>/dev/null || echo "0"),
            "unit": "ops/sec"
        }
    ],
    "memory_metrics": [
        {
            "name": "heap_usage",
            "value": $(echo "$functional_data" | jq '.metrics.memory_info.percentage' | sed 's/%//' 2>/dev/null || echo "0"),
            "unit": "percent"
        }
    ],
    "cpu_metrics": [
        {
            "name": "cpu_usage",
            "value": $(echo "$functional_data" | jq '.metrics.cpu_usage' | sed 's/%//' 2>/dev/null || echo "0"),
            "unit": "percent"
        }
    ],
    "functional_results": {
        "total_passed": $(grep -c "SUCCESS\|OK" "$benchmark_dir/functional.log" "$benchmark_dir/integration.log" 2>/dev/null || echo "0"),
        "total_failed": $(grep -c "FAILURE\|ERROR" "$benchmark_dir/functional.log" "$benchmark_dir/integration.log" 2>/dev/null || echo "0")
    }
}
EOF
}

send_regression_notification() {
    local mode="$1"
    local regression_file="$2"
    local report_file="$3"

    # Skip if no notification configured
    if [[ -z "${SLACK_WEBHOOK_URL:-}" ]]; then
        return
    fi

    local regression_count=$(jq '.regressions | length' "$regression_file")
    local subject="YAWL Regression Alert - $mode"
    local message="Regression detected in YAWL $mode testing"

    if [[ $regression_count -gt 0 ]]; then
        message+="\nRegressions found: $regression_count"
        message+="\nCritical metrics affected:"
        jq -r '.regressions[] | "- \(.metric_name | . // .type): \(.change_percent // .difference)%"' "$regression_file" | while read -r metric; do
            message+="\n$metric"
        done
    else
        message+="\nNo regressions detected"
    fi

    message+="\n\nReport: $report_file"

    # Send Slack notification
    curl -s -X POST -H 'Content-type: application/json' \
        --data "{\"text\":\"$message\"}" \
        "$SLACK_WEBHOOK_URL" > /dev/null 2>&1 || true
}

main() {
    local mode="${1:-performance}"
    shift || true

    # Parse command line arguments
    local baseline_file="$DEFAULT_BASELINE_FILE"
    local threshold=$DEFAULT_THRESHOLD
    local days=$DEFAULT_DAYS
    local output_dir="$DEFAULT_OUTPUT_DIR"
    local include_trends=false
    local auto_fix=false
    local enable_notifications=false

    while [[ $# -gt 0 ]]; do
        case $1 in
            -b|--baseline)
                baseline_file="$2"
                shift 2
                ;;
            -t|--threshold)
                threshold="$2"
                shift 2
                ;;
            -d|--days)
                days="$2"
                shift 2
                ;;
            -o|--output-dir)
                output_dir="$2"
                shift 2
                ;;
            --include-trends)
                include_trends=true
                shift
                ;;
            --auto-fix)
                auto_fix=true
                shift
                ;;
            --enable-notifications)
                enable_notifications=true
                shift
                ;;
            --slack-webhook)
                SLACK_WEBHOOK_URL="$2"
                shift 2
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
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

    # Validate mode
    if [[ -z "${REGRESSION_MODES[$mode]:-}" ]]; then
        log "ERROR" "Unknown mode: $mode"
        print_help
        exit 1
    fi

    log "INFO" "Starting YAWL Regression Detection"
    log "INFO" "Mode: $mode"
    log "INFO" "Threshold: $threshold%"
    log "INFO" "Output directory: $output_dir"

    # Create output directory
    mkdir -p "$output_dir"

    # Check requirements
    check_requirements

    # Handle baseline mode
    if [[ "$mode" == "baseline" ]]; then
        if establish_baseline "$mode" "$baseline_file" "$output_dir"; then
            log "SUCCESS" "Baseline established successfully"
            exit 0
        else
            log "ERROR" "Failed to establish baseline"
            exit 1
        fi
    fi

    # Run current benchmarks
    local benchmark_dir="$output_dir/$(date +%Y%m%d_%H%M%S)"
    if ! run_benchmarks "$mode" "$output_dir"; then
        log "ERROR" "Failed to run benchmarks"
        exit 1
    fi

    # Aggregate current results
    local current_file="$output_dir/current-baseline.json"
    aggregate_benchmark_results "$benchmark_dir" > "$current_file"

    # Detect regressions
    local regression_detected=false

    if [[ " ${REGRESSION_MODES[$mode]} " =~ "latency" ]] || [[ " ${REGRESSION_MODES[$mode]} " =~ "throughput" ]] || [[ " ${REGRESSION_MODES[$mode]} " =~ "memory" ]] || [[ " ${REGRESSION_MODES[$mode]} " =~ "cpu" ]]; then
        if detect_performance_regressions "$baseline_file" "$current_file" "$threshold" "$output_dir"; then
            regression_detected=true
        fi
    fi

    if [[ " ${REGRESSION_MODES[$mode]} " =~ "correctness" ]] || [[ " ${REGRESSION_MODES[$mode]} " =~ "compliance" ]] || [[ " ${REGRESSION_MODES[$mode]} " =~ "integration" ]]; then
        if detect_functionality_regressions "$baseline_file" "$current_file" "$output_dir"; then
            regression_detected=true
        fi
    fi

    # Send notifications if enabled
    if [[ "$enable_notifications" == "true" ]]; then
        local regression_file="$output_dir/performance-regressions.json"
        local report_file="$output_dir/performance-regression-report.md"
        if [[ -f "$regression_file" ]]; then
            send_regression_notification "$mode" "$regression_file" "$report_file"
        fi
    fi

    # Final status
    if [[ "$regression_detected" == "true" ]]; then
        log "ERROR" "Regressions detected! Check reports in: $output_dir"
        exit 1
    else
        log "SUCCESS" "No regressions detected"
        log "INFO" "Results available in: $output_dir"
        exit 0
    fi
}

# Execute main function with all arguments
main "$@"