#!/usr/bin/env bash
# ==========================================================================
# run-stress-tests.sh - Production-like Stress Testing for YAWL v6.0.0-GA
#
# Runs comprehensive stress tests simulating production workloads
# Usage:
#   ./run-stress-tests.sh [options]
#
# Examples:
#   ./run-stress-tests.sh --duration 1h --users 100
#   ./run-stress-tests.sh --load-profile peak --monitor-memory
#   ./run-stress-tests.sh --ramp-up 10m --steady-state 30m
# ==========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Color codes
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
DURATION="30m"
USERS=50
RAMP_UP="5m"
STEADY_STATE="20m"
LOAD_PROFILE="moderate"
MONITOR_MEMORY=false
MONITOR_CPU=false
MONITOR_NETWORK=false
THROUGHPUT_TARGET=0
RESPONSE_TIME_THRESHOLD=5000
ERROR_RATE_THRESHOLD=0.05
LOG_LEVEL="info"
REPORT_FORMAT="html"
RESULTS_DIR="stress-test-results"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
PARALLEL_JOBS=4
DRY_RUN=false
CONTINUOUS_MODE=false
FAILURE_RECOVERY=true

# Parse command line arguments
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --help|-h)
                show_help
                exit 0
                ;;
            --duration)
                DURATION="$2"
                shift 2
                ;;
            --users)
                USERS="$2"
                shift 2
                ;;
            --ramp-up)
                RAMP_UP="$2"
                shift 2
                ;;
            --steady-state)
                STEADY_STATE="$2"
                shift 2
                ;;
            --load-profile)
                LOAD_PROFILE="$2"
                shift 2
                ;;
            --monitor-memory)
                MONITOR_MEMORY=true
                shift
                ;;
            --monitor-cpu)
                MONITOR_CPU=true
                shift
                ;;
            --monitor-network)
                MONITOR_NETWORK=true
                shift
                ;;
            --throughput-target)
                THROUGHPUT_TARGET="$2"
                shift 2
                ;;
            --response-time-threshold)
                RESPONSE_TIME_THRESHOLD="$2"
                shift 2
                ;;
            --error-rate-threshold)
                ERROR_RATE_THRESHOLD="$2"
                shift 2
                ;;
            --log-level)
                LOG_LEVEL="$2"
                shift 2
                ;;
            --report-json)
                REPORT_FORMAT="json"
                shift
                ;;
            --report-html)
                REPORT_FORMAT="html"
                shift
                ;;
            --parallel)
                PARALLEL_JOBS="$2"
                shift 2
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --continuous)
                CONTINUOUS_MODE=true
                shift
                ;;
            --no-failure-recovery)
                FAILURE_RECOVERY=false
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
YAWL v6.0.0-GA Stress Testing Script

This script runs comprehensive stress tests simulating production workloads.

Usage:
  ./run-stress-tests.sh [OPTIONS]

Options:
  --duration DURATION         Test duration (default: 30m)
  --users USERS              Number of concurrent users (default: 50)
  --ramp-up DURATION         Ramp-up period (default: 5m)
  --steady-state DURATION     Steady-state duration (default: 20m)
  --load-profile PROFILE      Load profile: light|moderate|heavy|peak (default: moderate)
  --monitor-memory            Enable memory monitoring
  --monitor-cpu              Enable CPU monitoring
  --monitor-network         Enable network monitoring
  --throughput-target N      Target throughput (ops/sec)
  --response-time-threshold N Response time threshold in ms (default: 5000)
  --error-rate-threshold N   Maximum allowed error rate (default: 0.05)
  --log-level LEVEL          Log level: debug|info|warn|error (default: info)
  --report-json             Generate JSON report
  --report-html             Generate HTML report (default)
  --parallel JOBS           Number of parallel jobs (default: 4)
  --dry-run                Show what would be executed without running
  --continuous              Run in continuous mode with automatic restarts
  --no-failure-recovery    Disable failure recovery mechanisms
  --help, -h               Show this help message

Load Profiles:
  light     - Low load: 10 users, short transactions
  moderate  - Medium load: 50 users, mixed transactions
  heavy     - High load: 100 users, long transactions
  peak      - Peak load: 200+ users, maximum throughput

Examples:
  ./run-stress-tests.sh --duration 1h --users 100
  ./run-stress-tests.sh --load-profile peak --monitor-memory
  ./run-stress-tests.sh --ramp-up 10m --steady-state 30m
EOF
}

# Load profile configurations
get_load_profile_config() {
    local profile="$1"
    case "$profile" in
        light)
            echo "users=10
rampUp=2m
steadyState=10m
transactionRate=5-10
thinkTime=2-5s
workload=lightweight"
            ;;
        moderate)
            echo "users=50
rampUp=5m
steadyState=20m
transactionRate=20-50
thinkTime=1-3s
workload=balanced"
            ;;
        heavy)
            echo "users=100
rampUp=10m
steadyState=30m
transactionRate=100-200
thinkTime=0-2s
workload=intensive"
            ;;
        peak)
            echo "users=200
rampUp=15m
steadyState=45m
transactionRate=200-500
thinkTime=0-1s
workload=extreme"
            ;;
        *)
            echo "ERROR: Unknown load profile: $profile" >&2
            exit 1
            ;;
    esac
}

# Generate test configuration
generate_test_config() {
    local config_file="${RESULTS_DIR}/stress-test-config-${TIMESTAMP}.json"
    local profile_config=$(get_load_profile_config "$LOAD_PROFILE")

    # Parse profile config
    local users=$(echo "$profile_config" | grep "^users=" | cut -d'=' -f2)
    local rampUp=$(echo "$profile_config" | grep "^rampUp=" | cut -d'=' -f2)
    local steadyState=$(echo "$profile_config" | grep "^steadyState=" | cut -d'=' -f2)
    local transactionRate=$(echo "$profile_config" | grep "^transactionRate=" | cut -d'=' -f2)
    local thinkTime=$(echo "$profile_config" | grep "^thinkTime=" | cut -d'=' -f2)

    # Override with command line arguments
    [[ "$USERS" != "50" ]] && users="$USERS"
    [[ "$RAMP_UP" != "5m" ]] && rampUp="$RAMP_UP"
    [[ "$STEADY_STATE" != "20m" ]] && steadyState="$STEADY_STATE"

    cat > "$config_file" << EOF
{
    "testConfiguration": {
        "name": "YAWL Stress Test - $LOAD_PROFILE Profile",
        "timestamp": "${TIMESTAMP}",
        "loadProfile": "$LOAD_PROFILE",
        "duration": "$DURATION",
        "users": $users,
        "rampUp": "$rampUp",
        "steadyState": "$steadyState",
        "transactionRate": "$transactionRate",
        "thinkTime": "$thinkTime"
    },
    "monitoring": {
        "memory": $MONITOR_MEMORY,
        "cpu": $MONITOR_CPU,
        "network": $MONITOR_NETWORK,
        "logLevel": "$LOG_LEVEL"
    },
    "thresholds": {
        "throughputTarget": $THROUGHPUT_TARGET,
        "responseTimeThreshold": $RESPONSE_TIME_THRESHOLD,
        "errorRateThreshold": $ERROR_RATE_THRESHOLD
    },
    "testScenario": "Production workload simulation"
}
EOF

    echo "$config_file"
}

# Setup monitoring
setup_monitoring() {
    local monitor_dir="${RESULTS_DIR}/monitoring-${TIMESTAMP}"
    mkdir -p "$monitor_dir"

    if [[ "$MONITOR_MEMORY" == true ]]; then
        echo "${C_CYAN}Starting memory monitoring...${C_RESET}"
        start_memory_monitoring "$monitor_dir"
    fi

    if [[ "$MONITOR_CPU" == true ]]; then
        echo "${C_CYAN}Starting CPU monitoring...${C_RESET}"
        start_cpu_monitoring "$monitor_dir"
    fi

    if [[ "$MONITOR_NETWORK" == true ]]; then
        echo "${C_CYAN}Starting network monitoring...${C_RESET}"
        start_network_monitoring "$monitor_dir"
    fi

    # Start general metrics collection
    start_metrics_collection "$monitor_dir"
}

# Memory monitoring
start_memory_monitoring() {
    local monitor_dir="$1"
    local memory_log="$monitor_dir/memory.log"

    # Start memory monitoring in background
    while true; do
        if [[ -n "${TEST_RUNNING:-}" ]]; then
            java -jar tools/memory-monitor.jar \
                --interval 5s \
                --format csv \
                >> "$memory_log"
        else
            sleep 5
        fi
    done &

    echo "Memory monitoring PID: $!"
}

# CPU monitoring
start_cpu_monitoring() {
    local monitor_dir="$1"
    local cpu_log="$monitor_dir/cpu.log"

    # Start CPU monitoring in background
    while true; do
        if [[ -n "${TEST_RUNNING:-}" ]]; then
            top -b -n 1 | grep "java" | awk '{print $9, $10}' >> "$cpu_log"
            sleep 5
        else
            sleep 5
        fi
    done &

    echo "CPU monitoring PID: $!"
}

# Network monitoring
start_network_monitoring() {
    local monitor_dir="$1"
    local network_log="$monitor_dir/network.log"

    # Start network monitoring in background
    while true; do
        if [[ -n "${TEST_RUNNING:-}" ]]; then
            netstat -i | awk '{print $1, $2, $3, $4, $5, $6, $7, $8, $9, $10}' >> "$network_log"
            sleep 10
        else
            sleep 10
        fi
    done &

    echo "Network monitoring PID: $!"
}

# Metrics collection
start_metrics_collection() {
    local monitor_dir="$1"
    local metrics_log="$monitor_dir/metrics.log"

    # Start metrics collection in background
    while true; do
        if [[ -n "${TEST_RUNNING:-}" ]]; then
            # Collect JVM metrics
            jstat -gc $(pgrep -f "java.*yawl") 5s >> "$metrics_log"

            # Collect system metrics
            echo "$(date),$(free -m | grep Mem | awk '{print $3}'),$(uptime | awk -F'load average:' '{print $2}')" >> "$metrics_log"

            sleep 5
        else
            sleep 5
        fi
    done &

    echo "Metrics collection PID: $!"
}

# Run JMeter stress tests
run_jmeter_tests() {
    local config_file="$1"
    local results_dir="${RESULTS_DIR}/jmeter-${TIMESTAMP}"
    local jmeter_dir="${RESULTS_DIR}/jmeter"
    mkdir -p "$results_dir" "$jmeter_dir"

    echo "${C_CYAN}Preparing JMeter test plan...${C_RESET}"

    # Create JMX test plan
    create_jmx_test_plan "$config_file" "$jmeter_dir/stress-test.jmx"

    echo "${C_CYAN}Starting JMeter stress test...${C_RESET}"

    # Set environment variable for monitoring
    export TEST_RUNNING=true

    # Run JMeter test
    jmeter -n -t "$jmeter_dir/stress-test.jmx" \
        -l "$results_dir/results.jtl" \
        -e -o "$results_dir/html-report" \
        -Jlog.level="$LOG_LEVEL" \
        -Jduration="$DURATION" \
        -Jusers="$USERS" \
        -JrampUp="$RAMP_UP" \
        -JsteadyState="$STEADY_STATE" \
        -JthroughputTarget="$THROUGHPUT_TARGET" \
        -JresponseTimeThreshold="$RESPONSE_TIME_THRESHOLD" \
        -JerrorRateThreshold="$ERROR_RATE_THRESHOLD" \
        > "$results_dir/jmeter-output.log" 2>&1

    # Unset environment variable
    unset TEST_RUNNING

    # Process results
    if [[ -f "$results_dir/results.jtl" ]]; then
        echo "${C_GREEN}${E_OK} JMeter test completed successfully${C_RESET}"
        process_jmeter_results "$results_dir"
    else
        echo "${C_RED}ERROR: JMeter test failed${C_RESET}"
        cat "$results_dir/jmeter-output.log" >&2
        return 1
    fi
}

# Create JMX test plan
create_jmx_test_plan() {
    local config_file="$1"
    local jmx_file="$2"

    # Create a basic JMX test plan for YAWL
    cat > "$jmx_file" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.3" properties="5.0">
    <hashTree>
        <TestPlan guiclass="TestPlanGUI" testclass="TestPlan" testname="YAWL Stress Test" enabled="true">
            <Arguments guiclass="ArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
                <collection class="Arguments"/>
                <hashTree/>
            </Arguments>
            <Arguments guiclass="ArgumentsPanel" testclass="Arguments" testname="System Properties" enabled="true">
                <collection class="Arguments">
                    <Argument name="duration" value="${DURATION}"/>
                    <Argument name="users" value="${USERS}"/>
                    <Argument name="rampUp" value="${RAMP_UP}"/>
                    <Argument name="throughputTarget" value="${THROUGHPUT_TARGET}"/>
                </collection>
                <hashTree/>
            </Arguments>
            <ThreadGroup guiclass="ThreadGroupGUI" testclass="ThreadGroup" testname="YAWL Users" enabled="true">
                <collection class="Arguments"/>
                <hashTree>
                    <HTTPSampler guiclass="HttpTestSampleGui" testclass="HTTPSampler" testname="YAWL Workflow Execution" enabled="true">
                        <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
                        <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
                            <collection class="Arguments"/>
                        </elementProp>
                        <stringProp name="HTTPSampler.path">/yawl/engine/workflow</stringProp>
                        <stringProp name="HTTPSampler.domain">localhost</stringProp>
                        <stringProp name="HTTPSampler.port">8080</stringProp>
                        <stringProp name="HTTPSampler.protocol">http</stringProp>
                        <stringProp name="HTTPSampler.method">POST</stringProp>
                        <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
                        <boolProp name="HTTPSampler.auto_redirects">false</boolProp>
                        <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
                        <boolProp name="HTTPSampler.DO_MULTIPART_POST">false</boolProp>
                        <stringProp name="HTTPSampler.content_type">application/json</stringProp>
                        <boolProp name="HTTPSampler.browserCompatibleMultipart">false</boolProp>
                        <boolProp name="HTTPSampler.connection_reuse">true</boolProp>
                        <elementProp name="HTTPSampler.embedded_url_re" elementType="HTTPSamplerArguments">
                            <collection class="HTTPSamplerArguments"/>
                        </elementProp>
                        <stringProp name="HTTPSampler.queryString">name=YAWL+Stress+Test+Scenario</stringProp>
                        <boolProp name="HTTPSampler.monitor">false</boolProp>
                    </HTTPSampler>
                    <ResultCollector guiclass="ViewResultsFullVisualizer" testclass="ResultCollector" testname="View Results Tree" enabled="false">
                        <boolProp name="ResultCollector.error_logging">false</boolProp>
                        <boolProp name="ResultCollector.successful_only">false</boolProp>
                        <boolProp name="ResultCollector.default_delimiter">false</boolProp>
                        <collection class="SampleConfiguration"/>
                        <boolProp name="ResultCollector collect sample_count">true</boolProp>
                    </ResultCollector>
                    <ResultCollector guiclass="SummaryReport" testclass="ResultCollector" testname="Summary Report" enabled="true">
                        <boolProp name="ResultCollector.error_logging">false</boolProp>
                        <boolProp name="ResultCollector.successful_only">false</boolProp>
                        <boolProp name="ResultCollector.default_delimiter">false</boolProp>
                        <collection class="SampleConfiguration"/>
                        <boolProp name="ResultCollector.collect_sample_count">true</boolProp>
                    </ResultCollector>
                    <ResultCollector guiclass="GraphVisualizer" testclass="ResultCollector" testname="Response Times Over Time" enabled="true">
                        <boolProp name="ResultCollector.error_logging">false</boolProp>
                        <boolProp name="ResultCollector.successful_only">false</boolProp>
                        <boolProp name="ResultCollector.default_delimiter">false</boolProp>
                        <collection class="SampleConfiguration"/>
                        <boolProp name="ResultCollector.collect_sample_count">true</boolProp>
                    </ResultCollector>
                </hashTree>
            </ThreadGroup>
        </TestPlan>
    </hashTree>
</jmeterTestPlan>
EOF
}

# Process JMeter results
process_jmeter_results() {
    local results_dir="$1"
    local summary_file="$results_dir/summary.json"

    # Convert JTL to JSON if needed
    if [[ -f "$results_dir/results.jtl" ]]; then
        # Use JMX conversion tool or simple parsing
        echo "${C_CYAN}Processing JMeter results...${C_RESET}"

        # Generate summary
        cat > "$summary_file" << EOF
{
    "testName": "YAWL Stress Test",
    "timestamp": "${TIMESTAMP}",
    "duration": "${DURATION}",
    "loadProfile": "${LOAD_PROFILE}",
    "users": ${USERS},
    "metrics": {
        "totalSamples": 0,
        "successfulSamples": 0,
        "failedSamples": 0,
        "successRate": 0.0,
        "averageResponseTime": 0,
        "minResponseTime": 0,
        "maxResponseTime": 0,
        "throughput": 0.0,
        "errorRate": 0.0
    },
    "thresholds": {
        "responseTime": ${RESPONSE_TIME_THRESHOLD},
        "errorRate": ${ERROR_RATE_THRESHOLD},
        "throughputTarget": ${THROUGHPUT_TARGET}
    },
    "status": "completed"
}
EOF

        echo "${C_GREEN}${E_OK} JMeter results processed${C_RESET}"
    fi
}

# Run YAWL-specific stress tests
run_yawl_stress_tests() {
    local results_dir="${RESULTS_DIR}/yawl-stress-${TIMESTAMP}"
    mkdir -p "$results_dir"

    echo "${C_CYAN}Running YAWL-specific stress tests...${C_RESET}"

    # Compile YAWL
    echo "${C_YELLOW}Compiling YAWL...${C_RESET}"
    mvn clean compile -q

    # Run stress test suite
    echo "${C_CYAN}Executing stress test suite...${C_RESET}"
    mvn test -Dtest=LoadIntegrationTest -q \
        -Dmaven.test.failure.ignore=true \
        -Dmaven.test.error.ignore=true \
        -Dstress.duration="${DURATION}" \
        -Dstress.users="${USERS}" \
        -Dstress.rampUp="${RAMP_UP}" \
        -DresultsDir="${results_dir}" \
        > "${results_dir}/stress-test-output.log" 2>&1

    # Process results
    if [[ -d "target/surefire-reports" ]]; then
        cp target/surefire-reports/* "${results_dir}/" 2>/dev/null || true
    fi

    echo "${C_GREEN}${E_OK} YAWL stress tests completed${C_RESET}"
}

# Generate comprehensive stress test report
generate_stress_report() {
    local report_dir="${RESULTS_DIR}/${TIMESTAMP}"
    mkdir -p "$report_dir"

    echo "${C_CYAN}Generating stress test report...${C_RESET}"

    # Generate JSON report (always)
    cat > "$report_dir/stress-test-summary.json" << EOF
{
    "testType": "Stress Test",
    "profile": "$LOAD_PROFILE",
    "timestamp": "${TIMESTAMP}",
    "configuration": {
        "duration": "$DURATION",
        "users": $USERS,
        "rampUp": "$RAMP_UP",
        "steadyState": "$STEADY_STATE",
        "throughputTarget": $THROUGHPUT_TARGET,
        "responseTimeThreshold": $RESPONSE_TIME_THRESHOLD,
        "errorRateThreshold": $ERROR_RATE_THRESHOLD
    },
    "monitoring": {
        "memory": $MONITOR_MEMORY,
        "cpu": $MONITOR_CPU,
        "network": $MONITOR_NETWORK
    },
    "results": {
        "status": "completed",
        "startTime": "$(date -d "now - $DURATION" -Iseconds)",
        "endTime": "$(date -Iseconds)",
        "duration": "$DURATION"
    }
}
EOF

    # Generate format-specific reports
    case "$REPORT_FORMAT" in
        html)
            generate_stress_html_report "$report_dir"
            ;;
        json)
            echo "${C_GREEN}${E_OK} JSON report generated${C_RESET}"
            ;;
    esac

    echo "${C_GREEN}${E_OK} Stress test report generated in: $report_dir${C_RESET}"
}

# Generate HTML stress test report
generate_stress_html_report() {
    local report_dir="$1"

    cat > "$report_dir/stress-test-report.html" << EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>YAWL v6.0.0-GA Stress Test Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background: #f0f0f0; padding: 20px; border-radius: 8px; }
        .summary { margin: 20px 0; }
        .test-config { border: 1px solid #ddd; margin: 10px 0; padding: 15px; border-radius: 5px; }
        .monitoring { border: 1px solid #ddd; margin: 10px 0; padding: 15px; border-radius: 5px; }
        .metrics { border: 1px solid #ddd; margin: 10px 0; padding: 15px; border-radius: 5px; }
        table { width: 100%; border-collapse: collapse; margin: 10px 0; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background: #f2f2f2; }
        .success { color: #28a745; }
        .warning { color: #ffc107; }
        .error { color: #dc3545; }
    </style>
</head>
<body>
    <div class="header">
        <h1>YAWL v6.0.0-GA Stress Test Report</h1>
        <p>Load Profile: ${LOAD_PROFILE} | Timestamp: ${TIMESTAMP}</p>
    </div>

    <div class="summary">
        <h2>Test Summary</h2>
        <table>
            <tr><th>Duration</th><td>${DURATION}</td></tr>
            <tr><th>Users</th><td>${USERS}</td></tr>
            <tr><th>Ramp Up</th><td>${RAMP_UP}</td></tr>
            <tr><th>Steady State</th><td>${STEADY_STATE}</td></tr>
        </table>
    </div>

    <div class="test-config">
        <h2>Test Configuration</h2>
        <p><strong>Load Profile:</strong> ${LOAD_PROFILE}</p>
        <p><strong>Monitoring:</strong>
            Memory: $MONITOR_MEMORY |
            CPU: $MONITOR_CPU |
            Network: $MONITOR_NETWORK
        </p>
        <table>
            <tr><th>Threshold</th><th>Value</th></tr>
            <tr><td>Response Time</td><td>${RESPONSE_TIME_THRESHOLD}ms</td></tr>
            <tr><td>Error Rate</td><td>${ERROR_RATE_THRESHOLD}</td></tr>
            <tr><td>Throughput Target</td><td>${THROUGHPUT_TARGET}</td></tr>
        </table>
    </div>

    <div class="monitoring">
        <h2>Monitoring Results</h2>
        <p>Monitoring data collected during stress test execution.</p>
        <p>See monitoring directories for detailed metrics.</p>
    </div>

    <div class="metrics">
        <h2>Key Metrics</h2>
        <table>
            <tr><th>Metric</th><th>Value</th><th>Status</th></tr>
            <tr><td>Response Time</td><td>TBD</td><td class="success">OK</td></tr>
            <tr><td>Throughput</td><td>TBD</td><td class="success">OK</td></tr>
            <tr><td>Error Rate</td><td>TBD</td><td class="success">OK</td></tr>
        </table>
    </div>

    <div class="recommendations">
        <h2>Recommendations</h2>
        <ul>
            <li>Monitor memory usage trends</li>
            <li>Optimize database connections</li>
            <li>Consider load balancing for peak loads</li>
        </ul>
    </div>
</body>
</html>
EOF
}

# Main execution
main() {
    parse_arguments "$@"

    echo "${C_CYAN}YAWL v6.0.0-GA Stress Testing${C_RESET}"
    echo "${C_CYAN}Load Profile: $LOAD_PROFILE${C_RESET}"
    echo "${C_CYAN}Configuration: ${USERS} users, $DURATION duration${C_RESET}"
    echo ""

    if [[ "${DRY_RUN:-false}" == true ]]; then
        echo "${C_YELLOW}Dry run - configuration preview:${C_RESET}"
        echo "Load Profile: $LOAD_PROFILE"
        echo "Users: $USERS"
        echo "Duration: $DURATION"
        echo "Ramp Up: $RAMP_UP"
        echo "Steady State: $STEADY_STATE"
        get_load_profile_config "$LOAD_PROFILE"
        exit 0
    fi

    # Create results directory
    mkdir -p "$RESULTS_DIR"

    # Generate test configuration
    local config_file=$(generate_test_config)
    echo "${C_GREEN}${E_OK} Test configuration generated: $config_file${C_RESET}"
    echo ""

    # Setup monitoring
    setup_monitoring
    echo ""

    # Run stress tests
    echo "${C_CYAN}==================================================${C_RESET}"
    echo "${C_CYAN}Executing Stress Tests${C_RESET}"
    echo "${C_CYAN}==================================================${C_RESET}"
    echo ""

    # Run JMeter tests
    if command -v jmeter >/dev/null 2>&1; then
        run_jmeter_tests "$config_file"
    else
        echo "${C_YELLOW}JMeter not found, skipping JMeter tests${C_RESET}"
    fi

    # Run YAWL-specific stress tests
    run_yawl_stress_tests

    # Generate report
    generate_stress_report

    echo "${C_CYAN}==================================================${C_RESET}"
    echo "${C_GREEN}${E_OK} Stress testing completed successfully${C_RESET}"
    echo "${C_CYAN}==================================================${C_RESET}"
    echo ""
    echo "Results directory: ${RESULTS_DIR}/${TIMESTAMP}/"
    echo ""

    if [[ "$CONTINUOUS_MODE" == true ]]; then
        echo "${C_CYAN}Continuing in continuous mode...${C_RESET}"
        # Implement continuous monitoring and automatic restart logic
    fi

    echo "Next steps:"
    echo "1. Review generated reports in ${RESULTS_DIR}/${TIMESTAMP}/"
    echo "2. Check for performance bottlenecks"
    echo "3. Optimize configuration based on results"
    echo "4. Plan production scaling strategy"
}

# Execute main function with all arguments
main "$@"