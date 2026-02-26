#!/bin/bash

# MCP Protocol Validation Test Execution Script
#
# This script runs the comprehensive MCP protocol validation tests.
#
# Usage:
#   ./run-mcp-validation-tests.sh [options]
#
# Options:
#   -v, --verbose     Enable verbose output
#   -t, --timeout N  Set timeout in seconds (default: 60)
#   -r, --retry N    Set retry attempts (default: 3)
#   -h, --help       Show this help message

set -e

# Default values
VERBOSE=false
TIMEOUT_SECONDS=60
RETRY_ATTEMPTS=3
PYTHON_TIMEOUT=30

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -t|--timeout)
            TIMEOUT_SECONDS="$2"
            shift 2
            ;;
        -r|--retry)
            RETRY_ATTEMPTS="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo "Options:"
            echo "  -v, --verbose     Enable verbose output"
            echo "  -t, --timeout N   Set timeout in seconds (default: 60)"
            echo "  -r, --retry N     Set retry attempts (default: 3)"
            echo "  -h, --help        Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TEST_DIR="$PROJECT_ROOT/test"
BUILD_DIR="$PROJECT_DIR/target"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check if we're in the correct directory
    if [[ ! -f "pom.xml" ]]; then
        log_error "pom.xml not found. Please run this script from the project root."
        exit 1
    fi

    # Check if Java is available
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed or not in PATH"
        exit 1
    fi

    # Check if Python is available (required for MCP tests)
    if ! command -v python3 &> /dev/null && ! command -v python &> /dev/null; then
        log_warning "Python is not installed. MCP tests may fail."
    fi

    # Check if rebar3 is available (required for YAWL)
    if ! command -v rebar3 &> /dev/null; then
        log_warning "rebar3 is not installed. YAWL compilation may fail."
    fi

    log_success "Prerequisites check completed"
}

# Build the project
build_project() {
    log_info "Building project..."

    # Compile Erlang code
    if command -v rebar3 &> /dev/null; then
        log_info "Compiling Erlang code..."
        rebar3 compile
        if [ $? -ne 0 ]; then
            log_error "Erlang compilation failed"
            exit 1
        fi
    fi

    # Build Java code
    log_info "Building Java code..."
    if [ -f "mvnw" ]; then
        ./mvnw clean compile -q
    elif [ -f "pom.xml" ] && command -v mvn &> /dev/null; then
        mvn clean compile -q
    else
        log_warning "No Maven wrapper or Maven found. Java build skipped."
    fi

    log_success "Project build completed"
}

# Start test services
start_test_services() {
    log_info "Starting test services..."

    # Start MCP server
    log_info "Starting MCP server..."
    cd "$TEST_DIR/resources"
    python3 -m http.server 8081 &
    MCP_SERVER_PID=$!

    # Wait for server to start
    sleep 2

    # Start YAWL engine
    log_info "Starting YAWL engine..."
    cd "$PROJECT_ROOT"
    rebar3 run &
    YAWL_PID=$!

    # Wait for YAWL to start
    sleep 5

    # Store PIDs for cleanup
    echo $MCP_SERVER_PID > /tmp/mcp_server.pid
    echo $YAWL_PID > /tmp/yawl_server.pid

    log_success "Test services started"
}

# Stop test services
stop_test_services() {
    log_info "Stopping test services..."

    # Stop MCP server
    if [ -f /tmp/mcp_server.pid ]; then
        MCP_PID=$(cat /tmp/mcp_server.pid)
        if [ -n "$MCP_PID" ]; then
            kill $MCP_PID 2>/dev/null || true
        fi
        rm -f /tmp/mcp_server.pid
    fi

    # Stop YAWL server
    if [ -f /tmp/yawl_server.pid ]; then
        YAWL_PID=$(cat /tmp/yawl_server.pid)
        if [ -n "$YAWL_PID" ]; then
            kill $YAWL_PID 2>/dev/null || true
        fi
        rm -f /tmp/yawl_server.pid
    fi

    log_success "Test services stopped"
}

# Run individual test
run_test() {
    local test_class=$1
    local test_method=$2
    local timeout=$3

    if [ "$VERBOSE" = true ]; then
        log_info "Running $test_class.$test_method..."
    fi

    # Run the test with timeout
    timeout $timeout java -cp "$TEST_DIR:$BUILD_DIR/classes" \
        org.junit.runner.JUnitCore \
        org.yawlfoundation.yawl.integration.java_python.interoperability.$test_class \
        2>/dev/null || echo "TIMEOUT"
}

# Run all MCP validation tests
run_mcp_validation_tests() {
    log_info "Running MCP Protocol Validation Tests..."

    # Define test classes and methods
    local test_cases=(
        "McpProtocolValidationTest#testMcpToolRegistrationDiscovery 1"
        "McpProtocolValidationTest#testParameterSchemaValidation 1"
        "McpProtocolValidationTest#testToolExecutionResponseHandling 2"
        "McpProtocolValidationTest#testConcurrentToolExecution 2"
        "McpProtocolValidationTest#testErrorHandlingForInvalidCalls 1"
        "McpProtocolValidationTest#testInterfaceABCompliance 1"
        "McpProtocolValidationTest#testPerformanceAndLoad 3"
    )

    local total_tests=${#test_cases[@]}
    local passed_tests=0
    local failed_tests=0

    # Run tests
    for test_case in "${test_cases[@]}"; do
        IFS=' ' read -r test_class test_method timeout <<< "$test_case"

        if [ "$VERBOSE" = true ]; then
            echo ""
            log_info "Executing: $test_class.$test_method (timeout: ${timeout}s)"
        fi

        # Run test with retry logic
        for attempt in $(seq 1 $RETRY_ATTEMPTS); do
            result=$(run_test "$test_class" "$test_method" "$timeout")

            if [[ "$result" == *"OK"* ]]; then
                log_success "$test_class.$test_method passed (attempt $attempt)"
                ((passed_tests++))
                break
            elif [[ "$result" == *"TIMEOUT"* ]]; then
                log_warning "$test_class.$test_method timed out (attempt $attempt)"
                if [ $attempt -eq $RETRY_ATTEMPTS ]; then
                    log_error "$test_class.$test_method failed after $RETRY_ATTEMPTS attempts"
                    ((failed_tests++))
                fi
            else
                log_error "$test_class.$test_method failed (attempt $attempt)"
                if [ $attempt -eq $RETRY_ATTEMPTS ]; then
                    ((failed_tests++))
                else
                    # Wait before retry
                    sleep 2
                fi
            fi
        done

        # Brief pause between tests
        sleep 1
    done

    # Summary
    echo ""
    log_info "Test Summary:"
    log_success "Passed: $passed_tests"
    log_error "Failed: $failed_tests"
    log_info "Total: $total_tests"

    if [ $failed_tests -eq 0 ]; then
        log_success "All tests passed!"
        return 0
    else
        log_error "$failed_tests tests failed."
        return 1
    fi
}

# Generate test report
generate_test_report() {
    log_info "Generating test report..."

    local report_dir="$PROJECT_DIR/test-results"
    mkdir -p "$report_dir"

    local timestamp=$(date +"%Y%m%d_%H%M%S")
    local report_file="$report_dir/mcp-validation-report-$timestamp.txt"

    # Create report
    {
        echo "MCP Protocol Validation Test Report"
        echo "================================="
        echo "Timestamp: $(date)"
        echo "Project: $PROJECT_ROOT"
        echo ""
        echo "Configuration:"
        echo "  Verbose: $VERBOSE"
        echo "  Timeout: $TIMEOUT_SECONDS seconds"
        echo "  Retry attempts: $RETRY_ATTEMPTS"
        echo ""
        echo "Test Results:"
        # This would be populated with actual test results
        echo "  Total tests run: N/A"
        echo "  Passed: N/A"
        echo "  Failed: N/A"
        echo ""
        echo "Test Files:"
        echo "  - $TEST_DIR/org/yawlfoundation/yawl/integration/java_python/interoperability/McpProtocolValidationTest.java"
        echo "  - $TEST_DIR/org/yawlfoundation/yawl/integration/java_python/interoperability/McpTestConstants.java"
        echo "  - $TEST_DIR/org/yawlfoundation/yawl/integration/java_python/interoperability/McpTestData.java"
        echo "  - $TEST_DIR/org/yawlfoundation/yawl/integration/java_python/interoperability/McpTestUtils.java"
        echo ""
        echo "Configuration Files:"
        echo "  - $TEST_DIR/resources/mcp-test-config.properties"
        echo "  - $TEST_DIR/resources/test-workflow-schemas.json"
        echo ""
    } > "$report_file"

    log_success "Report generated: $report_file"
}

# Main execution
main() {
    log_info "Starting MCP Protocol Validation Test Suite"

    # Check prerequisites
    check_prerequisites

    # Build project
    build_project

    # Start test services
    start_test_services

    # Trap for cleanup
    trap 'stop_test_services' EXIT

    # Run tests
    run_mcp_validation_tests
    local test_result=$?

    # Generate report
    generate_test_report

    exit $test_result
}

# Run main function
main "$@"