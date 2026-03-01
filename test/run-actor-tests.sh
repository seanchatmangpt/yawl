#!/bin/bash

# Comprehensive Test Runner for YAWL Actor Pattern Validation
# This script runs the complete actor test suite with proper error handling

set -e

echo "🚀 Starting YAWL Actor Pattern Validation Test Suite"
echo "=================================================="

# Create reports directory
mkdir -p test/reports/actor

# Test categories
declare -a test_categories=(
    "unit"
    "integration"
    "performance"
    "load"
)

# Test results summary
declare -A test_results=(
    ["unit"]=0
    ["integration"]=0
    ["performance"]=0
    ["load"]=0
)

total_tests=0
passed_tests=0

# Function to run individual test files
run_test_file() {
    local test_file=$1
    local category=$2

    echo "📝 Running $test_file..."

    # Create report file
    local report_file="test/reports/actor/${test_file%.java}.txt"

    # Run test with timeout
    if timeout 300s java -cp "test:test-resources/*" org.junit.runner.JUnitCore $test_file > $report_file 2>&1; then
        echo "✅ $test_file - PASSED"
        test_results[$category]=$((test_results[$category] + 1))
        passed_tests=$((passed_tests + 1))
    else
        local exit_code=$?
        echo "❌ $test_file - FAILED (exit code: $exit_code)"

        # Show error details
        echo "📄 Error details:"
        tail -20 $report_file | grep -E "(ERROR|Exception|Failure)" || echo "No error details found"
    fi

    total_tests=$((total_tests + 1))
    echo ""
}

# Run all tests for each category
for category in "${test_categories[@]}"; do
    echo "🧪 Running $category tests..."
    echo "----------------------------------------"

    # Find all test files in the category
    while IFS= read -r -d '' test_file; do
        if [[ $test_file == *"Actor"*"Test.java" ]]; then
            run_test_file "$test_file" "$category"
        fi
    done < <(find "test/org/yawlfoundation/yawl/actor/$category" -name "*Test.java" -print0)
done

# Generate final report
echo "📊 Test Suite Results Summary"
echo "============================="
echo "Total tests run: $total_tests"
echo "✅ Passed: $passed_tests"

# Calculate percentage
if [ $total_tests -gt 0 ]; then
    pass_rate=$((passed_tests * 100 / total_tests))
    echo "Pass rate: $pass_rate%"

    if [ $pass_rate -ge 80 ]; then
        echo "🎉 Test suite PASSED (≥80% required)"
        exit 0
    else
        echo "🚨 Test suite FAILED (<80% required)"
        exit 1
    fi
else
    echo "⚠️  No tests found"
    exit 1
fi

echo ""
echo "📄 Detailed reports available in test/reports/actor/"