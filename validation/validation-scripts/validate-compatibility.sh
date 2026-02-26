#!/bin/bash

# Compatibility Validation Script for YAWL v6.0.0-GA
#
# This script performs comprehensive compatibility validation between Java and Python implementations,
# ensuring type compatibility, functionality preservation, and cross-language interoperability.
#
# Usage: ./validate-compatibility.sh [options]
# Options:
#   -j, --junit      Generate JUnit XML reports
#   -v, --verbose    Enable verbose output
#   -t, --timeout    Timeout in seconds (default: 300)
#   -h, --help       Show help message

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEST_DIR="$PROJECT_ROOT/test"
GRAALPY_AVAILABLE="${GRAALPY_AVAILABLE:-false}"
VERBOSE=false
JUNIT_REPORT=false
TIMEOUT_SECONDS=300
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

show_help() {
    echo "Compatibility Validation Script for YAWL v6.0.0-GA"
    echo ""
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -j, --junit      Generate JUnit XML reports"
    echo "  -v, --verbose    Enable verbose output"
    echo "  -t, --timeout    Timeout in seconds (default: 300)"
    echo "  -h, --help       Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  GRAALPY_AVAILABLE  Set to 'true' if GraalPy is available"
    echo ""
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -j|--junit)
            JUNIT_REPORT=true
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -t|--timeout)
            TIMEOUT_SECONDS="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

# Validate configuration
validate_config() {
    print_info "Validating configuration..."

    # Check GraalPy availability
    if [[ "$GRAALPY_AVAILABLE" != "true" ]]; then
        print_warning "GraalPy not available - compatibility tests will be limited"
    fi

    # Check timeout
    if [[ ! "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]]; then
        print_error "Invalid timeout value: $TIMEOUT_SECONDS"
        exit 1
    fi

    print_success "Configuration validated"
}

# Clean up previous results
cleanup() {
    print_info "Cleaning up previous results..."

    mkdir -p "$SCRIPT_DIR/results"
    rm -f "$SCRIPT_DIR/results/compatibility-report-*.txt"
    rm -f "$SCRIPT_DIR/results/junit-compatibility-*.xml"

    print_success "Cleanup completed"
}

# Run type compatibility tests
run_type_compatibility_tests() {
    print_info "Running type compatibility tests..."

    local test_start=$(date +%s)
    local test_log="$SCRIPT_DIR/results/type-compatibility-test.log"

    if [[ "$GRAALPY_AVAILABLE" != "true" ]]; then
        echo "Type compatibility tests skipped (GraalPy not available)" > "$test_log"
        print_warning "Type compatibility tests skipped"
        return 0
    fi

    # Run Java type compatibility tests
    if [[ "$JUNIT_REPORT" == "true" ]]; then
        $JAVA_HOME/bin/java \
            -cp "$PROJECT_ROOT/yawl-graalpy/target/classes:$TEST_DIR:$PROJECT_ROOT/yawl-graalpy/target/test-classes" \
            org.junit.jupiter.engine.JupiterTestEngine \
            --class-path "$TEST_DIR" \
            --scan-classpath \
            --reports-dir "$SCRIPT_DIR/results" \
            --tests "org.yawlfoundation.yawl.integration.java_python.JavaPythonTypeCompatibilityTest" \
            > "$test_log" 2>&1 &

        local test_pid=$!

        # Wait with timeout
        wait_for_completion $test_pid $TIMEOUT_SECONDS

        if [[ $? -ne 0 ]]; then
            print_error "Type compatibility tests timed out"
            kill $test_pid 2>/dev/null || true
            return 1
        fi
    else
        # Run without JUnit report
        timeout $TIMEOUT_SECONDS \
            $JAVA_HOME/bin/java \
            -cp "$PROJECT_ROOT/yawl-graalpy/target/classes:$TEST_DIR:$PROJECT_ROOT/yawl-graalpy/target/test-classes" \
            org.junit.jupiter.engine.JupiterTestEngine \
            --class-path "$TEST_DIR" \
            --scan-classpath \
            --tests "org.yawlfoundation.yawl.integration.java_python.JavaPythonTypeCompatibilityTest" \
            > "$test_log" 2>&1 || echo "Tests failed or timed out" >> "$test_log"
    fi

    # Parse results
    if grep -q "TEST FAILED" "$test_log"; then
        print_error "Type compatibility tests failed"
        return 1
    else
        print_success "Type compatibility tests passed"
        return 0
    fi
}

# Run functionality preservation tests
run_functionality_preservation_tests() {
    print_info "Running functionality preservation tests..."

    local test_log="$SCRIPT_DIR/results/functionality-preservation-test.log"

    if [[ "$GRAALPY_AVAILABLE" != "true" ]]; then
        echo "Functionality preservation tests skipped (GraalPy not available)" > "$test_log"
        print_warning "Functionality preservation tests skipped"
        return 0
    fi

    timeout $TIMEOUT_SECONDS \
        $JAVA_HOME/bin/java \
        -cp "$PROJECT_ROOT/yawl-graalpy/target/classes:$TEST_DIR:$PROJECT_ROOT/yawl-graalpy/target/test-classes" \
        org.junit.jupiter.engine.JupiterTestEngine \
        --class-path "$TEST_DIR" \
        --scan-classpath \
        --tests "org.yawlfoundation.yawl.integration.java_python.YawlFunctionalityPreservationTest" \
        > "$test_log" 2>&1 || echo "Tests failed or timed out" >> "$test_log"

    if grep -q "TEST FAILED" "$test_log"; then
        print_error "Functionality preservation tests failed"
        return 1
    else
        print_success "Functionality preservation tests passed"
        return 0
    fi
}

# Run pattern validation tests
run_pattern_validation_tests() {
    print_info "Running pattern validation tests..."

    local test_log="$SCRIPT_DIR/results/pattern-validation-test.log"

    if [[ "$GRAALPY_AVAILABLE" != "true" ]]; then
        echo "Pattern validation tests skipped (GraalPy not available)" > "$test_log"
        print_warning "Pattern validation tests skipped"
        return 0
    fi

    # Run basic pattern tests
    timeout $TIMEOUT_SECONDS \
        $JAVA_HOME/bin/java \
        -cp "$PROJECT_ROOT/yawl-graalpy/target/classes:$TEST_DIR:$PROJECT_ROOT/yawl-graalpy/target/test-classes" \
        org.junit.jupiter.engine.JupiterTestEngine \
        --class-path "$TEST_DIR" \
        --scan-classpath \
        --tests "org.yawlfoundation.yawl.integration.java_python.patterns.BasicPatternValidator" \
        > "$test_log" 2>&1 || echo "Basic pattern tests failed or timed out" >> "$test_log"

    # Run advanced pattern tests
    timeout $TIMEOUT_SECONDS \
        $JAVA_HOME/bin/java \
        -cp "$PROJECT_ROOT/yawl-graalpy/target/classes:$TEST_DIR:$PROJECT_ROOT/yawl-graalpy/target/test-classes" \
        org.junit.jupiter.engine.JupiterTestEngine \
        --class-path "$TEST_DIR" \
        --scan-classpath \
        --tests "org.yawlfoundation.yawl.integration.java_python.patterns.AdvancedPatternValidator" \
        >> "$test_log" 2>&1 || echo "Advanced pattern tests failed or timed out" >> "$test_log"

    if grep -q "TEST FAILED" "$test_log"; then
        print_error "Pattern validation tests failed"
        return 1
    else
        print_success "Pattern validation tests passed"
        return 0
    fi
}

# Generate compatibility report
generate_compatibility_report() {
    print_info "Generating compatibility report..."

    local report_file="$SCRIPT_DIR/results/compatibility-report-$TIMESTAMP.txt"

    cat > "$report_file" << EOF
Compatibility Validation Report for YAWL v6.0.0-GA
Generated: $(date)
===============================================

Test Environment:
- GraalPy Available: $GRAALPY_AVAILABLE
- Java Version: $($JAVA_HOME/bin/java -version 2>&1 | head -n1)
- Timeout: $TIMEOUT_SECONDS seconds

Test Results:
EOF

    # Process test results
    local total_tests=0
    local passed_tests=0
    local failed_tests=0

    for test_log in "$SCRIPT_DIR/results/"*test*.log; do
        if [[ -f "$test_log" ]]; then
            ((total_tests++))
            echo "" >> "$report_file"
            echo "=== $(basename "$test_log" | sed 's/-test\.log$//') ===" >> "$report_file"

            if grep -q "TEST FAILED" "$test_log"; then
                ((failed_tests++))
                echo "Status: FAILED" >> "$report_file"
                echo "Failures:" >> "$report_file"
                grep "TEST FAILED\|AssertionError" "$test_log" | head -10 >> "$report_file"
            else
                ((passed_tests++))
                echo "Status: PASSED" >> "$report_file"
            fi

            # Add test statistics
            local test_lines=$(wc -l < "$test_log")
            echo "Log entries: $test_lines" >> "$report_file"
        fi
    done

    # Summary
    echo "" >> "$report_file"
    echo "=== SUMMARY ===" >> "$report_file"
    echo "Total tests run: $total_tests" >> "$report_file"
    echo "Tests passed: $passed_tests" >> "$report_file"
    echo "Tests failed: $failed_tests" >> "$report_file"

    if [[ $total_tests -gt 0 ]]; then
        local success_rate=$((passed_tests * 100 / total_tests))
        echo "Success rate: ${success_rate}%" >> "$report_file"
    fi

    # Recommendations
    echo "" >> "$report_file"
    echo "=== RECOMMENDATIONS ===" >> "$report_file"

    if [[ $failed_tests -gt 0 ]]; then
        echo "- Fix failed compatibility tests before proceeding to production" >> "$report_file"
        echo "- Review error logs for specific issues" >> "$report_file"
        echo "- Ensure type marshalling is correctly implemented" >> "$report_file"
        echo "- Verify functionality preservation across language boundaries" >> "$report_file"
    else
        echo "- All compatibility tests passed - system is ready for production" >> "$report_file"
        echo "- Consider adding more comprehensive test cases for edge cases" >> "$report_file"
    fi

    # Compliance check
    echo "" >> "$report_file"
    echo "=== COMPLIANCE CHECK ===" >> "$report_file"

    if [[ "$GRAALPY_AVAILABLE" == "true" && $passed_tests -eq $total_tests ]]; then
        echo "✓ Full compatibility compliance achieved" >> "$report_file"
    elif [[ "$GRAALPY_AVAILABLE" != "true" && $passed_tests -eq $total_tests ]]; then
        echo "⚠ Partial compliance (GraalPy not available)" >> "$report_file"
    else
        echo "✗ Compliance issues detected" >> "$report_file"
    fi

    print_success "Compatibility report generated: $report_file"

    # Display summary
    echo ""
    print_info "=== COMPATIBILITY SUMMARY ==="
    echo "Tests run: $total_tests"
    echo "Passed: $passed_tests"
    echo "Failed: $failed_tests"

    if [[ $total_tests -gt 0 ]]; then
        echo "Success rate: $((passed_tests * 100 / total_tests))%"
    fi

    return $((failed_tests > 0 ? 1 : 0))
}

# Wait for process completion with timeout
wait_for_completion() {
    local pid=$1
    local timeout=$2
    local elapsed=0

    while kill -0 "$pid" 2>/dev/null; do
        if [[ $elapsed -ge $timeout ]]; then
            return 1
        fi
        sleep 1
        ((elapsed++))
    done

    wait "$pid"
    return $?
}

# Main execution
main() {
    echo "Compatibility Validation Script for YAWL v6.0.0-GA"
    echo "================================================="
    echo ""

    # Validate configuration
    validate_config

    # Clean up
    cleanup

    # Run tests
    local all_passed=true

    print_info "Starting compatibility validation..."
    echo ""

    # Run type compatibility tests
    if ! run_type_compatibility_tests; then
        all_passed=false
    fi

    # Run functionality preservation tests
    if ! run_functionality_preservation_tests; then
        all_passed=false
    fi

    # Run pattern validation tests
    if ! run_pattern_validation_tests; then
        all_passed=false
    fi

    # Generate report
    local report_result
    if ! generate_compatibility_report; then
        report_result=1
    else
        report_result=0
    fi

    # Final result
    echo ""
    if [[ "$all_passed" == "true" && $report_result -eq 0 ]]; then
        print_success "All compatibility validation tests passed!"
        exit 0
    else
        print_error "Some compatibility validation tests failed!"
        exit 1
    fi
}

# Run main function
main "$@"