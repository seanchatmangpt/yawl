#!/bin/bash

# YAWL A2A Integration Test Execution Script
#
# This script runs all A2A integration tests and generates coverage reports.
# Uses Chicago TDD methodology with real integrations.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test configuration
TEST_PORT_BASE=19800
TEST_TIMEOUT=300  # 5 minutes per test
PARALLEL_TESTS=4

echo -e "${BLUE}===============================================${NC}"
echo -e "${BLUE}  YAWL A2A Integration Test Suite${NC}"
echo -e "${BLUE}===============================================${NC}"
echo -e "${YELLOW}Running tests with Chicago TDD methodology...${NC}"
echo

# Create test reports directory
mkdir -p test-reports

# Function to run individual test class
run_test() {
    local test_class=$1
    local test_port=$((TEST_PORT_BASE + RANDOM % 1000))
    local timeout_seconds=$2

    echo -e "${BLUE}Running $test_class...${NC}"

    # Start a timeout process
    (
        sleep $timeout_seconds
        echo -e "${RED}Test $test_class timed out after $timeout_seconds seconds${NC}"
        exit 1
    ) &
    local timeout_pid=$!

    # Run the test
    if mvn test -Dtest="$test_class" -DfailIfNoTests=false -q; then
        echo -e "${GREEN}✓ $test_class passed${NC}"
        kill $timeout_pid 2>/dev/null || true
        return 0
    else
        echo -e "${RED}✗ $test_class failed${NC}"
        kill $timeout_pid 2>/dev/null || true
        return 1
    fi
}

# Function to run test suite
run_test_suite() {
    local suite_name=$1
    local suite_classes=$2

    echo -e "${BLUE}===============================================${NC}"
    echo -e "${BLUE}Running $suite_name Suite${NC}"
    echo -e "${BLUE}===============================================${NC}"

    # Parse comma-separated classes
    IFS=',' read -ra classes <<< "$suite_classes"

    local suite_pass_count=0
    local suite_fail_count=0

    for test_class in "${classes[@]}"; do
        # Trim whitespace
        test_class=$(echo "$test_class" | xargs)

        if run_test "$test_class" "$TEST_TIMEOUT"; then
            ((suite_pass_count++))
        else
            ((suite_fail_count++))
            # Continue running other tests even if one fails
        fi
        echo
    done

    echo -e "${BLUE}Suite Results: $suite_pass_count passed, $suite_fail_count failed${NC}"
    return $suite_fail_count
}

# Function to run performance tests
run_performance_tests() {
    echo -e "${BLUE}===============================================${NC}"
    echo -e "${BLUE}Running Performance Tests${NC}"
    echo -e "${BLUE}===============================================${NC}"

    local perf_tests=(
        "org.yawlfoundation.yawl.integration.a2a.VirtualThreadConcurrencyTest"
        "org.yawlfoundation.yawl.integration.a2a.skills.A2ASkillBenchmark"
    )

    local perf_pass_count=0
    local perf_fail_count=0

    for test_class in "${perf_tests[@]}"; do
        if run_test "$test_class" $((TEST_TIMEOUT * 2)); then  # Longer timeout for performance tests
            ((perf_pass_count++))
        else
            ((perf_fail_count++))
        fi
    done

    echo -e "${BLUE}Performance Tests Results: $perf_pass_count passed, $perf_fail_count failed${NC}"
    return $perf_fail_count
}

# Function to run integration tests
run_integration_tests() {
    echo -e "${BLUE}===============================================${NC}"
    echo -e "${BLUE}Running Integration Tests${NC}"
    echo -e "${BLUE}===============================================${NC}"

    local integration_tests=(
        "org.yawlfoundation.yawl.integration.a2a.A2AIntegrationTest"
        "org.yawlfoundation.yawl.integration.mcp_a2a.McpA2AProtocolTest"
        "org.yawlfoundation.yawl.integration.a2a.AutonomousAgentScenarioTest"
    )

    local integration_pass_count=0
    local integration_fail_count=0

    for test_class in "${integration_tests[@]}"; do
        if run_test "$test_class" $((TEST_TIMEOUT * 1.5)); then  # Longer timeout for integration tests
            ((integration_pass_count++))
        else
            ((integration_fail_count++))
        fi
    done

    echo -e "${BLUE}Integration Tests Results: $integration_pass_count passed, $integration_fail_count failed${NC}"
    return $integration_fail_count
}

# Function to run compliance tests
run_compliance_tests() {
    echo -e "${BLUE}===============================================${NC}"
    echo -e "${BLUE}Running Compliance Tests${NC}"
    echo -e "${BLUE}===============================================${NC}"

    local compliance_tests=(
        "org.yawlfoundation.yawl.integration.a2a.A2AComplianceTest"
        "org.yawlfoundation.yawl.integration.a2a.A2AProtocolTest"
        "org.yawlfoundation.yawl.integration.a2a.A2AAuthenticationTest"
    )

    local compliance_pass_count=0
    local compliance_fail_count=0

    for test_class in "${compliance_tests[@]}"; do
        if run_test "$test_class" "$TEST_TIMEOUT"; then
            ((compliance_pass_count++))
        else
            ((compliance_fail_count++))
        fi
    done

    echo -e "${BLUE}Compliance Tests Results: $compliance_pass_count passed, $compliance_fail_count failed${NC}"
    return $compliance_fail_count
}

# Function to run test suites in parallel
run_tests_in_parallel() {
    local test_groups=("$@")
    local results=()
    local pids=()

    for group in "${test_groups[@]}"; do
        (
            eval "$group"
        ) &
        pids+=($!)
    done

    # Wait for all background jobs to complete
    local overall_success=0
    for pid in "${pids[@]}"; do
        wait $pid
        if [ $? -ne 0 ]; then
            overall_success=1
        fi
    done

    return $overall_success
}

# Function to generate coverage report
generate_coverage_report() {
    echo -e "${BLUE}===============================================${NC}"
    echo -e "${BLUE}Generating Coverage Report${NC}"
    echo -e "${BLUE}===============================================${NC}"

    # Run the coverage report generator
    if java -cp test org.yawlfoundation.yawl.integration.a2a.A2ATestCoverageReport; then
        echo -e "${GREEN}✓ Coverage report generated successfully${NC}"

        # Display key metrics
        if [ -f "test-reports/a2a-test-coverage.json" ]; then
            echo
            echo -e "${BLUE}Coverage Metrics:${NC}"
            # Extract and display key metrics from JSON
            if command -v jq &> /dev/null; then
                echo "  Method Coverage: $(jq '.methodCoverage' test-reports/a2a-test-coverage.json | cut -d. -f1)%"
                echo "  Scenario Coverage: $(jq '.scenarioCoverage' test-reports/a2a-test-coverage.json | cut -d. -f1)%"
                echo "  Coverage Grade: $(jq '.coverageGrade' test-reports/a2a-test-coverage.json)"
            else
                echo "  Coverage report generated at: test-reports/a2a-test-coverage.json"
            fi
        fi
    else
        echo -e "${YELLOW}Warning: Coverage report generation failed${NC}"
    fi
}

# Function to run specific test patterns
run_specific_tests() {
    local pattern=$1

    echo -e "${BLUE}Running tests matching pattern: $pattern${NC}"

    # Find all test files matching the pattern
    local test_files=$(find test -name "*$pattern*.java" | sed 's|/|.|g' | sed 's|\.java||' | head -10)

    if [ -z "$test_files" ]; then
        echo -e "${YELLOW}No tests found matching pattern: $pattern${NC}"
        return 0
    fi

    echo -e "Found tests: $test_files"

    local pass_count=0
    local fail_count=0

    for test_file in $test_files; do
        if run_test "$test_file" "$TEST_TIMEOUT"; then
            ((pass_count++))
        else
            ((fail_count++))
        fi
    done

    echo -e "${BLUE}Pattern Results: $pass_count passed, $fail_count failed${NC}"
    return $fail_count
}

# Main execution function
main() {
    local mode=${1:-"all"}

    case $mode in
        "core")
            echo -e "${BLUE}Running Core A2A Tests only${NC}"
            run_test_suite "Core A2A" \
                "org.yawlfoundation.yawl.integration.a2a.YawlA2AServerTest,org.yawlfoundation.yawl.integration.a2a.A2AClientTest"
            ;;

        "compliance")
            echo -e "${BLUE}Running Compliance Tests only${NC}"
            run_compliance_tests
            ;;

        "integration")
            echo -e "${BLUE}Running Integration Tests only${NC}"
            run_integration_tests
            ;;

        "performance")
            echo -e "${BLUE}Running Performance Tests only${NC}"
            run_performance_tests
            ;;

        "specific")
            if [ -z "$2" ]; then
                echo -e "${RED}Usage: $0 specific <pattern>${NC}"
                exit 1
            fi
            run_specific_tests "$2"
            ;;

        "parallel")
            echo -e "${BLUE}Running Tests in Parallel${NC}"
            run_tests_in_parallel \
                "run_compliance_tests" \
                "run_integration_tests" \
                "run_performance_tests"
            ;;

        "report")
            echo -e "${BLUE}Generating Coverage Report only${NC}"
            generate_coverage_report
            ;;

        "all"|"")
            echo -e "${BLUE}Running All Tests${NC}"
            run_compliance_tests
            run_integration_tests
            run_performance_tests
            generate_coverage_report
            ;;

        *)
            echo -e "${RED}Usage: $0 [core|compliance|integration|performance|specific|parallel|report|all]${NC}"
            exit 1
            ;;
    esac

    echo
    echo -e "${BLUE}===============================================${NC}"
    echo -e "${BLUE}  Test Execution Complete${NC}"
    echo -e "${BLUE}===============================================${NC}"

    # Check if any tests failed
    if [ $? -ne 0 ]; then
        echo -e "${YELLOW}Some tests failed. Check output above for details.${NC}"
        exit 1
    else
        echo -e "${GREEN}All tests passed!${NC}"
        exit 0
    fi
}

# Handle script arguments
main "$@"