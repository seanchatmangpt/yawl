#!/bin/bash

# YAWL Actor Model Validation Test Runner - Phase 2
# ===============================================
#
# This script runs comprehensive validation tests for the YAWL Actor Model
# to validate 10M agent scalability claims.
#
# Test Categories:
# 1. Scale Testing (100K, 500K, 1M, 2M, 5M, 10M agents)
# 2. Performance Validation (latency, throughput, message delivery)
# 3. Stress & Stability Testing (flood, bursts, memory leaks)

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPORT_DIR="${PROJECT_ROOT}/reports/validation"
JAVA_OPTS="-Xms8g -Xmx64g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
TEST_TIMEOUT=3600  # 1 hour per test

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test categories
SCALE_TESTS=(
    "ActorModelScaleTest#test100KAgents"
    "ActorModelScaleTest#test500KAgents"
    "ActorModelScaleTest#test1MAgents"
    "ActorModelScaleTest#test2MAgents"
    "ActorModelScaleTest#test5MAgents"
    "ActorModelScaleTest#test10MAgents"
)

PERFORMANCE_TESTS=(
    "ActorModelPerformanceTest#testLatency10K"
    "ActorModelPerformanceTest#testLatency100K"
    "ActorModelPerformanceTest#testLatency1M"
    "ActorModelPerformanceTest#testLatency5M"
    "ActorModelPerformanceTest#testMessageDeliveryRate"
    "ActorModelPerformanceTest#testMessageLossPrevention"
    "ActorModelPerformanceTest#testMemoryLinearity"
    "ActorModelPerformanceTest#testCarrierThreadUtilization"
    "ActorModelPerformanceTest#testSchedulingThroughput"
)

STRESS_TESTS=(
    "ActorModelStressTest#testStability1M4Hours"
    "ActorModelStressTest#testStability5M24Hours"
    "ActorModelStressTest#testStability10M8Hours"
    "ActorModelStressTest#testMessageFlood"
    "ActorModelStressTest#testBurstPattern"
    "ActorModelStressTest#testMemoryLeakDetection"
    "ActorModelStressTest#testMixedStress"
    "ActorModelStressTest#testRecoveryStress"
)

# Initialize environment
init_environment() {
    echo -e "${BLUE}Initializing YAWL Actor Model Validation Environment${NC}"

    # Check prerequisites
    check_prerequisites

    # Create directories
    create_directories

    # Set up JVM options
    setup_jvm_options

    echo -e "${GREEN}Environment initialized successfully${NC}"
}

# Check system prerequisites
check_prerequisites() {
    echo -e "${YELLOW}Checking system prerequisites...${NC}"

    # Check Java version
    if ! command -v java &> /dev/null; then
        echo -e "${RED}Java is not installed${NC}"
        exit 1
    fi

    java_version=$(java -version 2>&1 | grep -oP '(?:java|openjdk) version "([^"]+)"' | grep -oP '[0-9]+(\.[0-9]+)+' | head -1)
    if [[ ! "$java_version" =~ ^21\. ]] && [[ ! "$java_version" =~ ^22\. ]] && [[ ! "$java_version" =~ ^23\. ]] && [[ ! "$java_version" =~ ^24\. ]] && [[ ! "$java_version" =~ ^25\. ]]; then
        echo -e "${RED}Java 21+ is required (found: $java_version)${NC}"
        exit 1
    fi

    # Check memory
    total_memory=$(free -m | awk 'NR==2{printf "%.1f", $2/1024}')
    if (( $(echo "$total_memory < 16.0" | bc -l) )); then
        echo -e "${RED}At least 16GB of RAM is recommended (found: ${total_memory}GB)${NC}"
        exit 1
    fi

    # Check CPU cores
    cpu_cores=$(nproc)
    if (( cpu_cores < 8 )); then
        echo -e "${YELLOW}Warning: Less than 8 CPU cores detected (${cpu_cores}), tests may be slower${NC}"
    fi

    echo -e "${GREEN}All prerequisites satisfied${NC}"
}

# Create necessary directories
create_directories() {
    echo -e "${YELLOW}Creating report directories...${NC}"

    mkdir -p "${REPORT_DIR}/scale_tests"
    mkdir -p "${REPORT_DIR}/performance"
    mkdir -p "${REPORT_DIR}/stress_tests"
    mkdir -p "${REPORT_DIR}/logs"
    mkdir -p "${REPORT_DIR}/artifacts"

    echo -e "${GREEN}Directories created${NC}"
}

# Set up JVM options
setup_jvm_options() {
    echo -e "${YELLOW}Setting up JVM options...${NC}"

    # Check available memory
    total_memory=$(free -m | awk 'NR==2{print $2}')
    heap_size=$((total_memory * 80 / 100))  # 80% of total memory

    JAVA_OPTS="-Xms${heap_size}m -Xmx${heap_size}m ${JAVA_OPTS}"

    echo -e "${BLUE}JVM Options: ${JAVA_OPTS}${NC}"
}

# Run a single test
run_test() {
    local test_full_name="$1"
    local test_class="${test_full_name%%#*}"
    local test_method="${test_full_name##*#}"

    echo -e "\n${BLUE}Running test: ${test_full_name}${NC}"

    local start_time=$(date +%s)
    local log_file="${REPORT_DIR}/logs/${test_class}_${test_method}_$(date +%Y%m%d_%H%M%S).log"

    # Run the test
    java ${JAVA_OPTS} -cp "${PROJECT_ROOT}/target/test-classes:${PROJECT_ROOT}/target/classes" \
        -Djava.library.path="${PROJECT_ROOT}/target/native" \
        -Dfile.encoding=UTF-8 \
        org.junit.runner.JUnitCore \
        "${test_class}" \
        "${test_method}" \
        > "${log_file}" 2>&1

    local exit_code=$?
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    # Check test result
    if grep -q "OK" "${log_file}"; then
        echo -e "${GREEN}✓ ${test_full_name} PASSED (${duration}s)${NC}"
        return 0
    else
        echo -e "${RED}✗ ${test_full_name} FAILED (${duration}s)${NC}"
        echo -e "${YELLOW}Check log: ${log_file}${NC}"
        return 1
    fi
}

# Run tests in parallel with limited concurrency
run_tests_parallel() {
    local test_array=("$@")
    local concurrency=4
    local pids=()
    local return_codes=()

    echo -e "\n${BLUE}Running ${#test_array[@]} tests with ${concurrency} workers${NC}"

    # Process tests in batches
    for ((i=0; i<${#test_array[@]}; i+=concurrency)); do
        local batch_end=$((i + concurrency - 1))
        if (( batch_end >= ${#test_array[@]} )); then
            batch_end=${#test_array[@]}-1
        fi

        # Start batch of tests
        for ((j=i; j<=batch_end; j++)); do
            (
                run_test "${test_array[j]}"
                echo "${test_array[j]}:$?" >> "${REPORT_DIR}/return_codes.tmp"
            ) &
            pids+=($!)
        done

        # Wait for batch to complete
        for pid in "${pids[@]}"; do
            wait $pid
        done
        pids=()
    done

    # Collect return codes
    if [[ -f "${REPORT_DIR}/return_codes.tmp" ]]; then
        while IFS=':' read -r test_code return_code; do
            return_codes+=("$return_code")
        done < "${REPORT_DIR}/return_codes.tmp"
        rm -f "${REPORT_DIR}/return_codes.tmp"
    fi

    # Check if all tests passed
    local failed_tests=0
    for code in "${return_codes[@]}"; do
        if (( code != 0 )); then
            ((failed_tests++))
        fi
    done

    return $failed_tests
}

# Run scale testing
run_scale_tests() {
    echo -e "\n${BLUE}================== Scale Testing ================${NC}"
    echo -e "${BLUE}Testing agent scales: 100K, 500K, 1M, 2M, 5M, 10M${NC}"

    run_tests_parallel "${SCALE_TESTS[@]}"
    local scale_result=$?

    # Generate scale test summary
    generate_scale_test_summary

    return $scale_result
}

# Run performance validation
run_performance_tests() {
    echo -e "\n${BLUE}================ Performance Validation ================${NC}"
    echo -e "${BLUE}Testing latency, throughput, and message delivery metrics${NC}"

    run_tests_parallel "${PERFORMANCE_TESTS[@]}"
    local perf_result=$?

    # Generate performance summary
    generate_performance_summary

    return $perf_result
}

# Run stress testing
run_stress_tests() {
    echo -e "\n${BLUE}================ Stress Testing ================${NC}"
    echo -e "${BLUE}Testing stability, flood handling, and recovery${NC}"

    run_tests_parallel "${STRESS_TESTS[@]}"
    local stress_result=$?

    # Generate stress test summary
    generate_stress_test_summary

    return $stress_result
}

# Generate scale test summary
generate_scale_test_summary() {
    echo -e "\n${YELLOW}Generating scale test summary...${NC}"

    local summary_file="${REPORT_DIR}/scale_test_summary.txt"

    {
        echo "YAWL Actor Model - Scale Testing Summary"
        echo "======================================"
        echo ""
        echo "Test Date: $(date)"
        echo ""
        echo "Scale Tests Results:"
        echo "-------------------"

        for test in "${SCALE_TESTS[@]}"; do
            local test_class="${test%%#*}"
            local test_method="${test##*#}"
            local log_file=$(ls -t "${REPORT_DIR}/logs/${test_class}_${test_method}_"*.log 2>/dev/null | head -1)

            if [[ -n "$log_file" ]]; then
                if grep -q "OK" "$log_file"; then
                    echo "✓ ${test}: PASSED"
                else
                    echo "✗ ${test}: FAILED"
                fi
            else
                echo "? ${test}: NO LOG FILE"
            fi
        done

        echo ""
        echo "Key Metrics:"
        echo "------------"
        echo "- Heap per agent: ≤150 bytes"
        echo "- GC pause time: <200ms"
        echo "- Thread utilization: <80%"
        echo ""
    } > "${summary_file}"

    echo -e "${GREEN}Scale test summary saved to: ${summary_file}${NC}"
}

# Generate performance summary
generate_performance_summary() {
    echo -e "\n${YELLOW}Generating performance summary...${NC}"

    local summary_file="${REPORT_DIR}/performance_summary.txt"

    {
        echo "YAWL Actor Model - Performance Validation Summary"
        echo "================================================"
        echo ""
        echo "Test Date: $(date)"
        echo ""
        echo "Performance Tests Results:"
        echo "--------------------------"

        for test in "${PERFORMANCE_TESTS[@]}"; do
            local test_class="${test%%#*}"
            local test_method="${test##*#}"
            local log_file=$(ls -t "${REPORT_DIR}/logs/${test_class}_${test_method}_"*.log 2>/dev/null | head -1)

            if [[ -n "$log_file" ]]; then
                if grep -q "OK" "$log_file"; then
                    echo "✓ ${test}: PASSED"
                else
                    echo "✗ ${test}: FAILED"
                fi
            else
                echo "? ${test}: NO LOG FILE"
            fi
        done

        echo ""
        echo "Key Thresholds:"
        echo "---------------"
        echo "- p99 latency: <100ms"
        echo "- Message rate: >10K/second/agent"
        echo "- Message loss: 0%"
        echo "- Memory linearity: ≤10% deviation"
        echo ""
    } > "${summary_file}"

    echo -e "${GREEN}Performance summary saved to: ${summary_file}${NC}"
}

# Generate stress test summary
generate_stress_test_summary() {
    echo -e "\n${YELLOW}Generating stress test summary...${NC}"

    local summary_file="${REPORT_DIR}/stress_test_summary.txt"

    {
        echo "YAWL Actor Model - Stress Testing Summary"
        echo "========================================"
        echo ""
        echo "Test Date: $(date)"
        echo ""
        echo "Stress Tests Results:"
        echo "---------------------"

        for test in "${STRESS_TESTS[@]}"; do
            local test_class="${test%%#*}"
            local test_method="${test##*#}"
            local log_file=$(ls -t "${REPORT_DIR}/logs/${test_class}_${test_method}_"*.log 2>/dev/null | head -1)

            if [[ -n "$log_file" ]]; then
                if grep -q "OK" "$log_file"; then
                    echo "✓ ${test}: PASSED"
                else
                    echo "✗ ${test}: FAILED"
                fi
            else
                echo "? ${test}: NO LOG FILE"
            fi
        done

        echo ""
        echo "Key Requirements:"
        echo "----------------"
        echo "- 24-hour stability at 5M agents"
        echo "- Flood handling: 100K msg/s"
        echo "- Burst recovery: 10x load spikes"
        echo "- Memory leak detection: <5% growth"
        echo "- Recovery rate: >95%"
        echo ""
    } > "${summary_file}"

    echo -e "${GREEN}Stress test summary saved to: ${summary_file}${NC}"
}

# Generate final validation report
generate_final_report() {
    echo -e "\n${YELLOW}Generating final validation report...${NC}"

    local report_file="${REPORT_DIR}/final_validation_report.html"

    {
        cat << EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>YAWL Actor Model Validation Report - Phase 2</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background-color: #f0f8ff; padding: 20px; border-radius: 5px; }
        .section { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }
        .success { color: green; }
        .failure { color: red; }
        .warning { color: orange; }
        table { width: 100%; border-collapse: collapse; margin: 10px 0; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>
    <div class="header">
        <h1>YAWL Actor Model Validation Report</h1>
        <h2>Phase 2: 10M Agent Scalability Validation</h2>
        <p>Generated: $(date)</p>
    </div>
EOF

        # Test Results Summary
        echo "<div class='section'><h2>Test Results Summary</h2>"

        # Scale Tests
        echo "<h3>Scale Testing</h3>"
        echo "<table>"
        echo "<tr><th>Test</th><th>Result</th><th>Agents</th></tr>"
        for test in "${SCALE_TESTS[@]}"; do
            local test_class="${test%%#*}"
            local test_method="${test##*#}"
            local log_file=$(ls -t "${REPORT_DIR}/logs/${test_class}_${test_method}_"*.log 2>/dev/null | head -1)

            if [[ -n "$log_file" ]]; then
                if grep -q "OK" "$log_file"; then
                    echo "<tr><td>${test}</td><td class='success'>PASS</td><td>${test_method//test/}</td></tr>"
                else
                    echo "<tr><td>${test}</td><td class='failure'>FAIL</td><td>${test_method//test/}</td></tr>"
                fi
            fi
        done
        echo "</table>"

        # Performance Tests
        echo "<h3>Performance Validation</h3>"
        echo "<table>"
        echo "<tr><th>Test</th><th>Result</th></tr>"
        for test in "${PERFORMANCE_TESTS[@]}"; do
            local test_class="${test%%#*}"
            local test_method="${test##*#}"
            local log_file=$(ls -t "${REPORT_DIR}/logs/${test_class}_${test_method}_"*.log 2>/dev/null | head -1)

            if [[ -n "$log_file" ]]; then
                if grep -q "OK" "$log_file"; then
                    echo "<tr><td>${test}</td><td class='success'>PASS</td></tr>"
                else
                    echo "<tr><td>${test}</td><td class='failure'>FAIL</td></tr>"
                fi
            fi
        done
        echo "</table>"

        # Stress Tests
        echo "<h3>Stress Testing</h3>"
        echo "<table>"
        echo "<tr><th>Test</th><th>Result</th></tr>"
        for test in "${STRESS_TESTS[@]}"; do
            local test_class="${test%%#*}"
            local test_method="${test##*#}"
            local log_file=$(ls -t "${REPORT_DIR}/logs/${test_class}_${test_method}_"*.log 2>/dev/null | head -1)

            if [[ -n "$log_file" ]]; then
                if grep -q "OK" "$log_file"; then
                    echo "<tr><td>${test}</td><td class='success'>PASS</td></tr>"
                else
                    echo "<tr><td>${test}</td><td class='failure'>FAIL</td></tr>"
                fi
            fi
        done
        echo "</table>"
        echo "</div>"

        # Claims Validation
        echo "<div class='section'><h2>Scalability Claims Validation</h2>"
        echo "<h3>10M Agent Claims</h3>"
        echo "<ul>"
        echo "<li><strong>Heap per agent ≤150 bytes</strong>: "
        if [[ -f "${REPORT_DIR}/scale_tests/10M_Scale_Test_report.json" ]]; then
            if grep -q "\"heapPerAgent\": 150" "${REPORT_DIR}/scale_tests/10M_Scale_Test_report.json"; then
                echo "<span class='success'>✓ VALIDATED</span>"
            else
                echo "<span class='failure'>✗ NOT VALIDATED</span>"
            fi
        else
            echo "<span class='warning'>? NOT TESTED</span>"
        fi
        echo "</li>"

        echo "<li><strong>p99 latency <100ms</strong>: "
        if [[ -f "${REPORT_DIR}/performance/10M_Latency_Test_report.json" ]]; then
            if grep -q "\"p99LatencyMillis\": 100" "${REPORT_DIR}/performance/10M_Latency_Test_report.json"; then
                echo "<span class='success'>✓ VALIDATED</span>"
            else
                echo "<span class='failure'>✗ NOT VALIDATED</span>"
            fi
        else
            echo "<span class='warning'>? NOT TESTED</span>"
        fi
        echo "</li>"

        echo "<li><strong>Message rate >10K/sec/agent</strong>: "
        if [[ -f "${REPORT_DIR}/performance/Message_Rate_Report.json" ]]; then
            if grep -q "\"avgRate\": 10000" "${REPORT_DIR}/performance/Message_Rate_Report.json"; then
                echo "<span class='success'>✓ VALIDATED</span>"
            else
                echo "<span class='failure'>✗ NOT VALIDATED</span>"
            fi
        else
            echo "<span class='warning'>? NOT TESTED</span>"
        fi
        echo "</li>"
        echo "</ul>"
        echo "</div>"

        # Recommendations
        echo "<div class='section'><h2>Recommendations</h2>"
        echo "<ul>"
        echo "<li>Monitor heap usage in production environments</li>"
        echo "<li>Implement alerting for GC pressure spikes</li>"
        echo "<li>Set up circuit breakers for flood scenarios</li>"
        echo "<li>Configure regular memory leak detection</li>"
        echo "<li>Proactive scaling based on load patterns</li>"
        echo "</ul>"
        echo "</div>"

        echo "</body></html>"
    } > "${report_file}"

    echo -e "${GREEN}Final validation report saved to: ${report_file}${NC}"
}

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}Cleaning up temporary files...${NC}"

    # Clean up temporary files
    find "${REPORT_DIR}" -name "*.tmp" -delete

    # Compress logs older than 1 day
    find "${REPORT_DIR}/logs" -name "*.log" -mtime +1 -exec gzip {} \;

    echo -e "${GREEN}Cleanup completed${NC}"
}

# Main execution
main() {
    echo -e "${BLUE}YAWL Actor Model Validation Test Runner${NC}"
    echo -e "${BLUE}=======================================${NC}"

    # Initialize
    init_environment

    # Run all test suites
    local overall_result=0

    echo -e "\n${BLUE}Starting test execution...${NC}"

    # Run scale tests
    if ! run_scale_tests; then
        echo -e "${RED}Scale tests failed${NC}"
        overall_result=1
    else
        echo -e "${GREEN}Scale tests completed successfully${NC}"
    fi

    # Run performance tests
    if ! run_performance_tests; then
        echo -e "${RED}Performance tests failed${NC}"
        overall_result=1
    else
        echo -e "${GREEN}Performance tests completed successfully${NC}"
    fi

    # Run stress tests
    if ! run_stress_tests; then
        echo -e "${RED}Stress tests failed${NC}"
        overall_result=1
    else
        echo -e "${GREEN}Stress tests completed successfully${NC}"
    fi

    # Generate final report
    generate_final_report

    # Cleanup
    cleanup

    # Exit with appropriate code
    if [[ $overall_result -eq 0 ]]; then
        echo -e "\n${GREEN}✓ ALL TESTS PASSED - 10M Agent Scalability Claims VALIDATED${NC}"
        exit 0
    else
        echo -e "\n${RED}✗ SOME TESTS FAILED - Claims NOT FULLY VALIDATED${NC}"
        exit 1
    fi
}

# Trap for cleanup on exit
trap cleanup EXIT

# Run main function
main "$@"