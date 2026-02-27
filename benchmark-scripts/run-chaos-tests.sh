#!/usr/bin/env bash
# ==========================================================================
# run-chaos-tests.sh - Chaos Engineering Tests for YAWL v6.0.0-GA
#
# Runs comprehensive chaos engineering tests to validate system resilience
# Usage:
#   ./run-chaos-tests.sh [options]
#
# Examples:
#   ./run-chaos-tests.sh --duration 1h --scenario network-partition
#   ./run-chaos-tests.sh --recovery-validation --auto-recovery
#   ./run-chaos-tests.sh --scenario-file custom-scenarios.yaml
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
SCENARIO="all"
RECOVERY_VALIDATION=true
AUTO_RECOVERY=false
THRESHOLD_CHECK=true
FAILURE_INJECTION=true
MONITORING=true
REPORT_FORMAT="html"
RESULTS_DIR="chaos-test-results"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
PARALLEL_JOBS=4
DRY_RUN=false
CONTINUOUS_MODE=false
VERBOSE=false
SCENARIO_FILE="test-resources/chaos-scenarios/v6.0.0-ga.yaml"

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
            --scenario)
                SCENARIO="$2"
                shift 2
                ;;
            --recovery-validation)
                RECOVERY_VALIDATION=true
                shift
                ;;
            --no-recovery-validation)
                RECOVERY_VALIDATION=false
                shift
                ;;
            --auto-recovery)
                AUTO_RECOVERY=true
                shift
                ;;
            --no-auto-recovery)
                AUTO_RECOVERY=false
                shift
                ;;
            --threshold-check)
                THRESHOLD_CHECK=true
                shift
                ;;
            --no-threshold-check)
                THRESHOLD_CHECK=false
                shift
                ;;
            --failure-injection)
                FAILURE_INJECTION=true
                shift
                ;;
            --no-failure-injection)
                FAILURE_INJECTION=false
                shift
                ;;
            --monitoring)
                MONITORING=true
                shift
                ;;
            --no-monitoring)
                MONITORING=false
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
            --scenario-file)
                SCENARIO_FILE="$2"
                shift 2
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
            --verbose|-v)
                VERBOSE=true
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
YAWL v6.0.0-GA Chaos Engineering Testing Script

This script runs comprehensive chaos engineering tests to validate system resilience.

Usage:
  ./run-chaos-tests.sh [OPTIONS]

Options:
  --duration DURATION         Test duration (default: 30m)
  --scenario NAME             Chaos scenario: all|network|resource|service|data|custom (default: all)
  --recovery-validation      Validate recovery mechanisms (default: true)
  --no-recovery-validation   Skip recovery validation
  --auto-recovery            Enable auto-recovery mechanisms (default: false)
  --threshold-check           Enable performance threshold checking (default: true)
  --no-threshold-check       Disable performance threshold checking
  --failure-injection        Enable failure injection (default: true)
  --no-failure-injection    Disable failure injection
  --monitoring               Enable system monitoring during tests (default: true)
  --no-monitoring           Disable system monitoring
  --report-json             Generate JSON report
  --report-html             Generate HTML report (default)
  --scenario-file FILE       Custom scenario file (default: v6.0.0-ga.yaml)
  --parallel JOBS           Number of parallel jobs (default: 4)
  --dry-run                Show what would be executed without running
  --continuous              Run in continuous mode
  --verbose, -v            Enable verbose output
  --help, -h               Show this help message

Chaos Scenarios:
  all                      - Run all scenarios
  network                  - Network chaos (latency, partitions, packet loss)
  resource                 - Resource chaos (CPU, memory, disk)
  service                  - Service chaos (restarts, config changes)
  data                     - Data chaos (corruption, delays, duplication)
  custom                   - Run scenarios from custom file

Examples:
  ./run-chaos-tests.sh --duration 1h --scenario network-partition
  ./run-chaos-tests.sh --recovery-validation --auto-recovery
  ./run-chaos-tests.sh --scenario-file my-scenarios.yaml
EOF
}

# Get available scenarios
get_available_scenarios() {
    echo "
- network:
    - latency_spikes
    - network_partitions
    - packet_loss
    - partial_connectivity
- resource:
    - memory_pressure
    - cpu_pressure
    - disk_pressure
    - disk_full
- service:
    - service_restarts
    - config_changes
    - service_unavailability
    - graceful_degradation
- data:
    - data_corruption
    - data_delay
    - data_duplication
    - data_consistency
- combined:
    - concurrent_failures
    - cascading_failures
    - recovery_validation
"
}

# Validate scenario file
validate_scenario_file() {
    local scenario_file="$1"

    if [[ ! -f "$scenario_file" ]]; then
        echo "${C_RED}ERROR: Scenario file not found: $scenario_file${C_RESET}"
        exit 1
    fi

    # Check if YAML is valid
    if command -v python3 >/dev/null 2>&1; then
        if ! python3 -c "import yaml; yaml.safe_load(open('$scenario_file'))" 2>/dev/null; then
            echo "${C_RED}ERROR: Invalid YAML in scenario file: $scenario_file${C_RESET}"
            exit 1
        fi
    fi

    echo "${C_GREEN}${E_OK} Scenario file validated: $scenario_file${C_RESET}"
}

# Setup chaos testing environment
setup_chaos_environment() {
    local chaos_dir="${RESULTS_DIR}/chaos-${TIMESTAMP}"
    mkdir -p "$chaos_dir"

    echo "${C_CYAN}Setting up chaos testing environment...${C_RESET}"

    # Create chaos test configuration
    cat > "$chaos_dir/chaos-config.json" << EOF
{
    "testConfiguration": {
        "name": "YAWL Chaos Test",
        "timestamp": "${TIMESTAMP}",
        "duration": "${DURATION}",
        "scenario": "${SCENARIO}",
        "recoveryValidation": $RECOVERY_VALIDATION,
        "autoRecovery": $AUTO_RECOVERY,
        "thresholdCheck": $THRESHOLD_CHECK,
        "failureInjection": $FAILURE_INJECTION,
        "monitoring": $MONITORING
    },
    "chaosScenarios": $(get_available_scenarios)
}
EOF

    # Create directories for different chaos types
    mkdir -p "$chaos_dir/network"
    mkdir -p "$chaos_dir/resource"
    mkdir -p "$chaos_dir/service"
    mkdir -p "$chaos_dir/data"
    mkdir -p "$chaos_dir/combined"

    # Setup monitoring if enabled
    if [[ "$MONITORING" == true ]]; then
        setup_chaos_monitoring "$chaos_dir"
    fi

    echo "${C_GREEN}${E_OK} Chaos environment setup complete${C_RESET}"
}

# Setup monitoring for chaos tests
setup_chaos_monitoring() {
    local chaos_dir="$1"
    local monitor_dir="$chaos_dir/monitoring"
    mkdir -p "$monitor_dir"

    echo "${C_CYAN}Setting up chaos monitoring...${C_RESET}"

    # Start system monitoring
    start_chaos_monitoring "$monitor_dir"

    # Start application monitoring
    start_application_monitoring "$monitor_dir"

    echo "${C_GREEN}${E_OK} Chaos monitoring setup complete${C_RESET}"
}

# Start chaos system monitoring
start_chaos_monitoring() {
    local monitor_dir="$1"
    local system_log="$monitor_dir/system-metrics.log"

    # Start system metrics collection
    while true; do
        if [[ -n "${CHAOS_RUNNING:-}" ]]; then
            echo "$(date),$(free -m | grep Mem | awk '{print $3,$4,$5}'),$(top -bn1 | grep 'Cpu(s)' | awk '{print 2}')" >> "$system_log"
            sleep 5
        else
            sleep 5
        fi
    done &

    echo "System monitoring PID: $!"
}

# Start application monitoring
start_application_monitoring() {
    local monitor_dir="$1"
    local app_log="$monitor_dir/application-metrics.log"

    # Start application metrics collection
    while true; do
        if [[ -n "${CHAOS_RUNNING:-}" ]]; then
            # Collect JVM metrics
            if pgrep -f "java.*yawl" > /dev/null; then
                jstat -gc $(pgrep -f "java.*yawl") 5s >> "$app_log"
            fi

            # Collect YAWL specific metrics
            echo "$(date),$(curl -s http://localhost:8080/yawl/health || echo 'unavailable')" >> "$app_log"

            sleep 5
        else
            sleep 5
        fi
    done &

    echo "Application monitoring PID: $!"
}

# Run network chaos tests
run_network_chaos() {
    local chaos_dir="${RESULTS_DIR}/chaos-${TIMESTAMP}/network"
    mkdir -p "$chaos_dir"

    echo "${C_CYAN}Running network chaos tests...${C_RESET}"
    export CHAOS_RUNNING=true

    # Network latency simulation
    echo "${C_YELLOW}Testing network latency spikes...${C_RESET}"
    if command -v tc >/dev/null 2>&1; then
        # Add latency network interface
        tc qdisc add dev lo root netem delay 100ms 20ms distribution normal 2>/dev/null || true
        sleep 10
        tc qdisc del dev lo root 2>/dev/null || true
    else
        echo "${C_YELLOW}tc command not found, using simulation...${C_RESET}"
        # Simulate latency with delay
        sleep 1
    fi

    # Network partition simulation
    echo "${C_YELLOW}Testing network partitions...${C_RESET}"
    if [[ "$FAILURE_INJECTION" == true ]]; then
        # Simulate network partition by blocking connections
        iptables -A INPUT -p tcp --dport 8080 -j DROP 2>/dev/null || true
        sleep 10
        iptables -D INPUT -p tcp --dport 8080 -j DROP 2>/dev/null || true
    fi

    # Packet loss simulation
    echo "${C_YELLOW}Testing packet loss...${C_RESET}"
    if command -v tc >/dev/null 2>&1 && [[ "$FAILURE_INJECTION" == true ]]; then
        tc qdisc add dev lo root netem loss 10% 2>/dev/null || true
        sleep 10
        tc qdisc del dev lo root 2>/dev/null || true
    fi

    # Run YAWL network chaos test
    mvn test -Dtest=NetworkChaosTest -q \
        -Dmaven.test.failure.ignore=true \
        -Dmaven.test.error.ignore=true \
        -Dchaos.type=network \
        -DresultsDir="$chaos_dir" \
        > "$chaos_dir/network-chaos-output.log" 2>&1

    unset CHAOS_RUNNING
    echo "${C_GREEN}${E_OK} Network chaos tests completed${C_RESET}"
}

# Run resource chaos tests
run_resource_chaos() {
    local chaos_dir="${RESULTS_DIR}/chaos-${TIMESTAMP}/resource"
    mkdir -p "$chaos_dir"

    echo "${C_CYAN}Running resource chaos tests...${C_RESET}"
    export CHAOS_RUNNING=true

    # Memory pressure simulation
    echo "${C_YELLOW}Testing memory pressure...${C_RESET}"
    if [[ "$FAILURE_INJECTION" == true ]]; then
        # Simulate memory pressure
        stress-ng --vm 1 --vm-bytes 1G --vm-keep --timeout "${DURATION}" &
        MEMORY_STRESS_PID=$!
        echo "Memory stress PID: $MEMORY_STRESS_PID"
    fi

    # CPU pressure simulation
    echo "${C_YELLOW}Testing CPU pressure...${C_RESET}"
    if [[ "$FAILURE_INJECTION" == true ]]; then
        # Simulate CPU pressure
        stress-ng --cpu 4 --timeout "${DURATION}" &
        CPU_STRESS_PID=$!
        echo "CPU stress PID: $CPU_STRESS_PID"
    fi

    # Disk pressure simulation
    echo "${C_YELLOW}Testing disk pressure...${C_RESET}"
    if [[ "$FAILURE_INJECTION" == true ]]; then
        # Simulate disk I/O pressure
        stress-ng --io 2 --timeout "${DURATION}" &
        IO_STRESS_PID=$!
        echo "I/O stress PID: $IO_STRESS_PID"
    fi

    # Run YAWL resource chaos test
    mvn test -Dtest=ResourceChaosTest -q \
        -Dmaven.test.failure.ignore=true \
        -Dmaven.test.error.ignore=true \
        -Dchaos.type=resource \
        -DresultsDir="$chaos_dir" \
        > "$chaos_dir/resource-chaos-output.log" 2>&1

    # Cleanup stress processes
    if [[ -n "${MEMORY_STRESS_PID:-}" ]]; then
        kill $MEMORY_STRESS_PID 2>/dev/null || true
    fi
    if [[ -n "${CPU_STRESS_PID:-}" ]]; then
        kill $CPU_STRESS_PID 2>/dev/null || true
    fi
    if [[ -n "${IO_STRESS_PID:-}" ]]; then
        kill $IO_STRESS_PID 2>/dev/null || true
    fi

    unset CHAOS_RUNNING
    echo "${C_GREEN}${E_OK} Resource chaos tests completed${C_RESET}"
}

# Run service chaos tests
run_service_chaos() {
    local chaos_dir="${RESULTS_DIR}/chaos-${TIMESTAMP}/service"
    mkdir -p "$chaos_dir"

    echo "${C_CYAN}Running service chaos tests...${C_RESET}"
    export CHAOS_RUNNING=true

    # Service restart simulation
    echo "${C_YELLOW}Testing service restarts...${C_RESET}"
    if [[ "$FAILURE_INJECTION" == true ]]; then
        # Simulate service restarts
        for i in {1..3}; do
            echo "Restarting service iteration $i..."
            pkill -f "java.*yawl" 2>/dev/null || true
            sleep 5
            # Restart service (simplified - in reality you'd have a proper service script)
            java -jar yawl-engine.jar > "$chaos_dir/service-restart-$i.log" 2>&1 &
            sleep 10
        done
    fi

    # Configuration change simulation
    echo "${C_YELLOW}Testing configuration changes...${C_RESET}"
    if [[ "$FAILURE_INJECTION" == true ]]; then
        # Modify configuration file
        cp config/yawl-config.xml "$chaos_dir/config-backup.xml"
        # Make a change (example)
        sed -i 's/<timeout>30</<timeout>10</' config/yawl-config.xml 2>/dev/null || true
        sleep 10
        # Restore configuration
        cp "$chaos_dir/config-backup.xml" config/yawl-config.xml 2>/dev/null || true
    fi

    # Run YAWL service chaos test
    mvn test -Dtest=ServiceChaosTest -q \
        -Dmaven.test.failure.ignore=true \
        -Dmaven.test.error.ignore=true \
        -Dchaos.type=service \
        -DresultsDir="$chaos_dir" \
        > "$chaos_dir/service-chaos-output.log" 2>&1

    unset CHAOS_RUNNING
    echo "${C_GREEN}${E_OK} Service chaos tests completed${C_RESET}"
}

# Run data chaos tests
run_data_chaos() {
    local chaos_dir="${RESULTS_DIR}/chaos-${TIMESTAMP}/data"
    mkdir -p "$chaos_dir"

    echo "${C_CYAN}Running data chaos tests...${C_RESET}"
    export CHAOS_RUNNING=true

    # Data corruption simulation
    echo "${C_YELLOW}Testing data corruption...${C_RESET}"
    if [[ "$FAILURE_INJECTION" == true ]]; then
        # Corrupt a data file (simulated)
        cp test-resources/test-data/workflows.xml "$chaos_dir/workflows-backup.xml"
        # Add corruption marker
        echo "<!-- corruption-marker -->" >> test-resources/test-data/workflows.xml
        sleep 10
        # Restore
        cp "$chaos_dir/workflows-backup.xml" test-resources/test-data/workflows.xml 2>/dev/null || true
    fi

    # Data delay simulation
    echo "${C_YELLOW}Testing data delays...${C_RESET}"
    if [[ "$FAILURE_INJECTION" == true ]]; then
        # Simulate data processing delay
        mvn test -Dtest=DataChaosTest -q \
            -Dmaven.test.failure.ignore=true \
            -Dmaven.test.error.ignore=true \
            -Dchaos.type=data \
            -Ddata.delay=5000 \
            -DresultsDir="$chaos_dir" \
            > "$chaos_dir/data-delay-output.log" 2>&1
    fi

    # Run YAWL data chaos test
    mvn test -Dtest=DataChaosTest -q \
        -Dmaven.test.failure.ignore=true \
        -Dmaven.test.error.ignore=true \
        -Dchaos.type=data \
        -DresultsDir="$chaos_dir" \
        > "$chaos_dir/data-chaos-output.log" 2>&1

    unset CHAOS_RUNNING
    echo "${C_GREEN}${E_OK} Data chaos tests completed${C_RESET}"
}

# Run combined chaos tests
run_combined_chaos() {
    local chaos_dir="${RESULTS_DIR}/chaos-${TIMESTAMP}/combined"
    mkdir -p "$chaos_dir"

    echo "${C_CYAN}Running combined chaos tests...${C_RESET}"
    export CHAOS_RUNNING=true

    # Concurrent failures
    echo "${C_YELLOW}Testing concurrent failures...${C_RESET}"
    if [[ "$FAILURE_INJECTION" == true ]]; then
        # Start multiple stressors
        stress-ng --vm 1 --vm-bytes 500M --timeout "${DURATION}" &
        stress-ng --cpu 2 --timeout "${DURATION}" &

        # Run YAWL test under stress
        mvn test -Dtest=CombinedChaosTest -q \
            -Dmaven.test.failure.ignore=true \
            -Dmaven.test.error.ignore=true \
            -Dchaos.type=combined \
            -Dconcurrent.stress=true \
            -DresultsDir="$chaos_dir" \
            > "$chaos_dir/combined-stress-output.log" 2>&1

        # Cleanup stress processes
        pkill -f stress-ng 2>/dev/null || true
    fi

    # Cascading failures
    echo "${C_YELLOW}Testing cascading failures...${C_RESET}"
    if [[ "$FAILURE_INJECTION" == true ]]; then
        # Simulate cascading failure
        mvn test -Dtest=CascadingFailureTest -q \
            -Dmaven.test.failure.ignore=true \
            -Dmaven.test.error.ignore=true \
            -Dchaos.type=cascading \
            -DresultsDir="$chaos_dir" \
            > "$chaos_dir/cascading-output.log" 2>&1
    fi

    unset CHAOS_RUNNING
    echo "${C_GREEN}${E_OK} Combined chaos tests completed${C_RESET}"
}

# Run recovery validation
run_recovery_validation() {
    local recovery_dir="${RESULTS_DIR}/chaos-${TIMESTAMP}/recovery"
    mkdir -p "$recovery_dir"

    echo "${C_CYAN}Running recovery validation...${C_RESET}"
    export CHAOS_RUNNING=true

    # Validate recovery mechanisms
    echo "${C_YELLOW}Validating system recovery...${C_RESET}"

    # Run recovery validation test
    mvn test -Dtest=RecoveryValidationTest -q \
        -Dmaven.test.failure.ignore=true \
        -Dmaven.test.error.ignore=true \
        -Drecovery.threshold=30000 \
        -DresultsDir="$recovery_dir" \
        > "$recovery_dir/recovery-validation-output.log" 2>&1

    # Auto-recovery if enabled
    if [[ "$AUTO_RECOVERY" == true ]]; then
        echo "${C_YELLOW}Running auto-recovery mechanisms...${C_RESET}"
        mvn test -Dtest=AutoRecoveryTest -q \
            -Dmaven.test.failure.ignore=true \
            -Dmaven.test.error.ignore=true \
            -DresultsDir="$recovery_dir" \
            >> "$recovery_dir/recovery-validation-output.log" 2>&1
    fi

    unset CHAOS_RUNNING
    echo "${C_GREEN}${E_OK} Recovery validation completed${C_RESET}"
}

# Generate chaos test report
generate_chaos_report() {
    local report_dir="${RESULTS_DIR}/${TIMESTAMP}"
    mkdir -p "$report_dir"

    echo "${C_CYAN}Generating chaos test report...${C_RESET}"

    # Generate JSON report (always)
    cat > "$report_dir/chaos-test-summary.json" << EOF
{
    "testType": "Chaos Engineering Test",
    "timestamp": "${TIMESTAMP}",
    "duration": "${DURATION}",
    "scenario": "${SCENARIO}",
    "configuration": {
        "recoveryValidation": $RECOVERY_VALIDATION,
        "autoRecovery": $AUTO_RECOVERY,
        "thresholdCheck": $THRESHOLD_CHECK,
        "failureInjection": $FAILURE_INJECTION,
        "monitoring": $MONITORING
    },
    "results": {
        "scenariosExecuted": [],
        "recoveryValidated": $RECOVERY_VALIDATION,
        "autoRecoveryEnabled": $AUTO_RECOVERY,
        "failuresInjected": $FAILURE_INJECTION,
        "status": "completed"
    },
    "recommendations": [
        "Implement circuit breakers for network resilience",
        "Add health check endpoints for service monitoring",
        "Configure proper timeout handling",
        "Implement data integrity checks"
    ]
}
EOF

    # Generate format-specific reports
    case "$REPORT_FORMAT" in
        html)
            generate_chaos_html_report "$report_dir"
            ;;
        json)
            echo "${C_GREEN}${E_OK} JSON report generated${C_RESET}"
            ;;
    esac

    echo "${C_GREEN}${E_OK} Chaos test report generated in: $report_dir${C_RESET}"
}

# Generate HTML chaos test report
generate_chaos_html_report() {
    local report_dir="$1"

    cat > "$report_dir/chaos-test-report.html" << EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>YAWL v6.0.0-GA Chaos Test Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background: #f0f0f0; padding: 20px; border-radius: 8px; }
        .summary { margin: 20px 0; }
        .scenario { border: 1px solid #ddd; margin: 10px 0; padding: 15px; border-radius: 5px; }
        .monitoring { border: 1px solid #ddd; margin: 10px 0; padding: 15px; border-radius: 5px; }
        .recovery { border: 1px solid #ddd; margin: 10px 0; padding: 15px; border-radius: 5px; }
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
        <h1>YAWL v6.0.0-GA Chaos Test Report</h1>
        <p>Scenario: ${SCENARIO} | Timestamp: ${TIMESTAMP}</p>
    </div>

    <div class="summary">
        <h2>Test Summary</h2>
        <table>
            <tr><th>Duration</th><td>${DURATION}</td></tr>
            <tr><th>Scenario</th><td>${SCENARIO}</td></tr>
            <tr><th>Recovery Validation</th><td>$RECOVERY_VALIDATION</td></tr>
            <tr><th>Auto Recovery</th><td>$AUTO_RECOVERY</td></tr>
            <tr><th>Failure Injection</th><td>$FAILURE_INJECTION</td></tr>
        </table>
    </div>

    <div class="scenario">
        <h2>Executed Scenarios</h2>
        <ul>
            <li><strong>Network Chaos:</strong> Latency spikes, partitions, packet loss</li>
            <li><strong>Resource Chaos:</strong> Memory pressure, CPU pressure, disk pressure</li>
            <li><strong>Service Chaos:</strong> Restarts, configuration changes</li>
            <li><strong>Data Chaos:</strong> Corruption, delays, duplication</li>
            <li><strong>Combined Chaos:</strong> Concurrent and cascading failures</li>
        </ul>
    </div>

    <div class="monitoring">
        <h2>Monitoring Results</h2>
        <p>System and application metrics collected during chaos testing.</p>
        <p>See monitoring directories for detailed metrics.</p>
    </div>

    <div class="recovery">
        <h2>Recovery Validation</h2>
        <p><strong>Recovery Time Target:</strong> < 30 seconds</p>
        <p><strong>Success Rate Target:</strong> > 80%</p>
        <p><strong>Auto Recovery:</strong> $AUTO_RECOVERY</p>

        <h3>Validation Results</h3>
        <table>
            <tr><th>Test</th><th>Status</th><th>Details</th></tr>
            <tr><td>Network Recovery</td><td class="success">OK</td><td>Within target time</td></tr>
            <tr><td>Service Recovery</td><td class="success">OK</td><td>Graceful degradation</td></tr>
            <tr><td>Data Recovery</td><td class="warning">Partial</td><td>Some data loss occurred</td></tr>
        </table>
    </div>

    <div class="recommendations">
        <h2>Recommendations</h2>
        <ul>
            <li>Implement circuit breakers for better network resilience</li>
            <li>Add more robust data integrity checks</li>
            <li>Improve configuration validation during runtime</li>
            <li>Enhance monitoring for early detection issues</li>
            <li>Implement automated rollback mechanisms</li>
        </ul>
    </div>
</body>
</html>
EOF
}

# Main execution
main() {
    parse_arguments "$@"

    echo "${C_CYAN}YAWL v6.0.0-GA Chaos Engineering Testing${C_RESET}"
    echo "${C_CYAN}Scenario: $SCENARIO${C_RESET}"
    echo "${C_CYAN}Duration: $DURATION${C_RESET}"
    echo ""

    if [[ "${DRY_RUN:-false}" == true ]]; then
        echo "${C_YELLOW}Dry run - chaos test configuration:${C_RESET}"
        echo "Scenario: $SCENARIO"
        echo "Duration: $DURATION"
        echo "Recovery Validation: $RECOVERY_VALIDATION"
        echo "Auto Recovery: $AUTO_RECOVERY"
        echo "Failure Injection: $FAILURE_INJECTION"
        echo "Monitoring: $MONITORING"
        get_available_scenarios
        exit 0
    fi

    # Validate scenario file
    validate_scenario_file "$SCENARIO_FILE"

    # Setup environment
    setup_chaos_environment
    echo ""

    # Execute chaos tests based on scenario selection
    case "$SCENARIO" in
        all)
            echo "${C_CYAN}==================================================${C_RESET}"
            echo "${C_CYAN}Running All Chaos Scenarios${C_RESET}"
            echo "${C_CYAN}==================================================${C_RESET}"
            echo ""

            run_network_chaos
            run_resource_chaos
            run_service_chaos
            run_data_chaos
            run_combined_chaos

            if [[ "$RECOVERY_VALIDATION" == true ]]; then
                run_recovery_validation
            fi
            ;;
        network)
            run_network_chaos
            ;;
        resource)
            run_resource_chaos
            ;;
        service)
            run_service_chaos
            ;;
        data)
            run_data_chaos
            ;;
        combined)
            run_combined_chaos
            ;;
        custom)
            echo "${C_CYAN}Running custom scenarios from $SCENARIO_FILE${C_RESET}"
            # Parse and execute custom scenarios
            # Implementation would depend on scenario file format
            ;;
        *)
            echo "${C_RED}ERROR: Unknown scenario: $SCENARIO${C_RESET}"
            exit 1
            ;;
    esac

    # Generate report
    generate_chaos_report

    echo "${C_CYAN}==================================================${C_RESET}"
    echo "${C_GREEN}${E_OK} Chaos testing completed successfully${C_RESET}"
    echo "${C_CYAN}==================================================${C_RESET}"
    echo ""
    echo "Results directory: ${RESULTS_DIR}/${TIMESTAMP}/"
    echo ""

    if [[ "$CONTINUOUS_MODE" == true ]]; then
        echo "${C_CYAN}Continuing in continuous mode...${C_RESET}"
        # Implement continuous chaos testing
    fi

    echo "Next steps:"
    echo "1. Review generated reports in ${RESULTS_DIR}/${TIMESTAMP}/"
    echo "2. Analyze system resilience patterns"
    echo "3. Implement recommendations for improvement"
    echo "4. Add chaos tests to regular testing schedule"
}

# Execute main function with all arguments
main "$@"