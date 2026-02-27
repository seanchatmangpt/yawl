#!/bin/bash

# Chaos Engineering Test Runner Script
# This script runs all chaos engineering tests with different configurations

set -e

# Configuration
CHAOS_TESTS_DIR="src/test/java/org/yawlfoundation/yawl/integration/java_python/chaos"
JAVA_OPTS="-Xmx512m -Xms256m"
TEST_TIMEOUT=300  # 5 minutes per test
REPORT_DIR="target/chaos-reports"

# Create reports directory
mkdir -p $REPORT_DIR

echo "=================================================="
echo "Starting Chaos Engineering Test Suite"
echo "=================================================="

# Function to run a single test with timeout
run_test() {
    local test_name=$1
    local test_class=$2

    echo ""
    echo "Running test: $test_name"
    echo "=================================================="

    # Run test with timeout
    if timeout $TEST_TIMEOUT mvn test -Dtest=$test_class -q -pl . -am; then
        echo "✅ $test_name: PASSED"
        echo "$test_name: PASSED" >> $REPORT_DIR/test-results.txt
    else
        local exit_code=$?
        if [ $exit_code -eq 124 ]; then
            echo "❌ $test_name: TIMED OUT (after $TEST_TIMEOUT seconds)"
        else
            echo "❌ $test_name: FAILED (exit code: $exit_code)"
        fi
        echo "$test_name: FAILED" >> $REPORT_DIR/test-results.txt
    fi

    # Generate individual test report if junit-xml plugin is available
    if [ -f "target/surefire-reports/TEST-$test_class.xml" ]; then
        cp target/surefire-reports/TEST-$test_class.xml $REPORT_DIR/
    fi
}

# Run all chaos engineering tests
echo "Running failure recovery test..."
run_test "Failure Recovery" "ChaosEngineeringTest#testFailureRecovery"

echo "Running circuit breaker test..."
run_test "Circuit Breaker" "ChaosEngineeringTest#testCircuitBreaker"

echo "Running graceful degradation test..."
run_test "Graceful Degradation" "ChaosEngineeringTest#testGracefulDegradation"

echo "Running timeout handling test..."
run_test "Timeout Handling" "ChaosEngineeringTest#testTimeoutHandling"

echo "Running resource exhaustion test..."
run_test "Resource Exhaustion" "ChaosEngineeringTest#testResourceExhaustion"

echo "Running chaos monkey test..."
run_test "Chaos Monkey" "ChaosEngineeringTest#testChaosMonkey"

echo "Running cascade failure prevention test..."
run_test "Cascade Failure Prevention" "ChaosEngineeringTest#testCascadeFailurePrevention"

# Generate summary report
echo ""
echo "=================================================="
echo "Test Summary"
echo "=================================================="
cat $REPORT_DIR/test-results.txt

# Calculate test statistics
total_tests=$(wc -l < $REPORT_DIR/test-results.txt)
passed_tests=$(grep -c "PASSED" $REPORT_DIR/test-results.txt || true)
failed_tests=$(grep -c "FAILED" $REPORT_DIR/test-results.txt || true)

echo ""
echo "Total tests: $total_tests"
echo "Passed: $passed_tests"
echo "Failed: $failed_tests"

# Calculate success rate
if [ $total_tests -gt 0 ]; then
    success_rate=$((passed_tests * 100 / total_tests))
    echo "Success rate: $success_rate%"
fi

# Clean up
rm -f target/surefire-reports/TEST-ChaosEngineeringTest.xml

echo ""
echo "Chaos engineering test suite completed"
echo "Reports saved to: $REPORT_DIR/"

exit 0