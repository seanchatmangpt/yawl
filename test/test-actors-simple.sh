#!/bin/bash

# Simple test execution for actor validation
# Works with Maven dependency issues by running tests individually

echo "🚀 Running YAWL Actor Test Suite (Simple Mode)"
echo "=============================================="

# Test results
passed=0
failed=0

# List of test classes to run
test_classes=(
    "org.yawlfoundation.yawl.actor.unit.ActorMemoryLeakDetectorTest"
    "org.yawlfoundation.yawl.actor.unit.ActorDeadlockDetectorTest"
    "org.yawlfoundation.yawl.actor.unit.ActorMessageHandlerTest"
    "org.yawlfoundation.yawl.actor.integration.ActorIntegrationTest"
)

# Create results directory
mkdir -p test/results

# Function to run a single test
run_test() {
    local test_class=$1
    local test_file=$2

    echo "📝 Running $test_file..."

    # Try to run with different classpaths
    if java -cp "test:target/test-classes:target/classes" \
        org.junit.runner.JUnitCore $test_class > test/results/${test_file}.log 2>&1; then
        echo "✅ $test_file - PASSED"
        ((passed++))
    else
        echo "❌ $test_file - FAILED"
        ((failed++))

        # Show relevant errors
        echo "📄 Recent errors:"
        tail -10 test/results/${test_file}.log | grep -E "(ERROR|Exception|Failure|Error)" || echo "  No specific errors found in log"
    fi

    echo ""
}

# Run all tests
for test_class in "${test_classes[@]}"; do
    test_file=$(echo $test_class | sed 's|.*\.||' | tr '[:upper:]' '[:lower:]')
    run_test "$test_class" "$test_file"
done

# Summary
echo "📊 Test Results Summary"
echo "======================"
echo "Total tests: $((passed + failed))"
echo "✅ Passed: $passed"
echo "❌ Failed: $failed"

if [ $((passed + failed)) -gt 0 ]; then
    pass_rate=$((passed * 100 / (passed + failed)))
    echo "Pass rate: $pass_rate%"

    if [ $pass_rate -ge 80 ]; then
        echo "🎉 Test suite PASSED (≥80% required)"
        exit 0
    else
        echo "🚨 Test suite FAILED (<80% required)"
        exit 1
    fi
else
    echo "⚠️ No tests were executed"
    exit 1
fi