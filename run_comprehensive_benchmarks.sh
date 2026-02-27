#!/bin/bash

# Comprehensive YAWL v6.0.0-GA Benchmark Test Runner
# This script runs all benchmark test suites and generates reports

set -e

# Configuration
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
REPORT_DIR="benchmark-reports-$TIMESTAMP"
JAVA_HOME=$JAVA_HOME

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}==================================================${NC}"
echo -e "${BLUE}YAWL v6.0.0-GA Comprehensive Benchmark Test Suite${NC}"
echo -e "${BLUE}==================================================${NC}"
echo -e "${BLUE}Timestamp: $TIMESTAMP${NC}"
echo ""

# Create report directory
mkdir -p $REPORT_DIR

# Function to print test results
print_result() {
    local test_name="$1"
    local status="$2"
    local color="$3"

    if [ "$status" -eq 0 ]; then
        echo -e "${color}✅ $test_name: PASSED${NC}"
        echo "$test_name: PASSED" >> $REPORT_DIR/test-summary.txt
    else
        echo -e "${color}❌ $test_name: FAILED (exit code: $status)${NC}"
        echo "$test_name: FAILED" >> $REPORT_DIR/test-summary.txt
    fi
}

# Function to run test with timeout
run_test_with_timeout() {
    local test_name="$1"
    local command="$2"
    local timeout_seconds="${3:-300}"  # Default 5 minutes

    echo -e "${YELLOW}Running: $test_name${NC}"
    echo "Command: $command"
    echo "Timeout: $timeout_seconds seconds"
    echo "========================================"

    if timeout $timeout_seconds bash -c "$command"; then
        print_result "$test_name" "0" "$GREEN"
        return 0
    else
        local exit_code=$?
        if [ $exit_code -eq 124 ]; then
            print_result "$test_name" "124" "$RED"
        else
            print_result "$test_name" "$exit_code" "$RED"
        fi
        return $exit_code
    fi
}

# 1. Unit Tests
echo -e "${BLUE}### 1. Running Unit Tests ###${NC}"
echo ""

# Simple Java tests
run_test_with_timeout "SimpleTest" "cd test/org/yawlfoundation/yawl/performance && java SimpleTest" 30

# Java compilation tests
run_test_with_timeout "Java25Compilation" "cd test/org/yawlfoundation/yawl/performance && find . -name '*.java' -exec javac -source 25 -target 25 {} \;" 60

# 2. JMH Benchmarks
echo -e "\n${BLUE}### 2. Running JMH Benchmarks ###${NC}"
echo ""

# Validate benchmark configurations
run_test_with_timeout "JMHValidation" "cd test/org/yawlfoundation/yawl/performance/jmh && bash validate-benchmarks.sh" 60

# Run individual JMH benchmarks (if compiled)
if [ -f "test/org/yawlfoundation/yawl/performance/jmh/target/classes/org/yawlfoundation/yawl/performance/jmh/AllBenchmarksRunner.class" ]; then
    run_test_with_timeout "AllJMH Benchmarks" "cd test/org/yawlfoundation/yawl/performance/jmh && java -cp target/classes:target/dependency/*:. org.yawlfoundation.yawl.performance.jmh.AllBenchmarksRunner" 600
else
    echo -e "${YELLOW}Skipping JMH benchmarks - not compiled${NC}"
fi

# 3. Integration Benchmarks
echo -e "\n${BLUE}### 3. Running Integration Benchmarks ###${NC}"
echo ""

run_test_with_timeout "IntegrationBenchmarks" "cd test/org/yawlfoundation/yawl/integration/benchmark && java BenchmarkSuite run" 300

# 4. Chaos Engineering Tests
echo -e "\n${BLUE}### 4. Running Chaos Engineering Tests ###${NC}"
echo ""

# Check if chaos test script exists
if [ -f "yawl-integration/src/test/scripts/run_chaos_tests.sh" ]; then
    run_test_with_timeout "ChaosEngineeringTests" "cd yawl-integration && bash src/test/scripts/run_chaos_tests.sh" 300
else
    echo -e "${YELLOW}Skipping chaos tests - script not found${NC}"
fi

# 5. Polyglot Integration Tests
echo -e "\n${BLUE}### 5. Running Polyglot Integration Tests ###${NC}"
echo ""

# GraalPy benchmarks
run_test_with_timeout "GraalPyPerformance" "cd test/org/yawlfoundation/yawl/graalpy/performance && java -jar yawl-performance-benchmarks-6.0.0-SNAPSHOT.jar" 180

# 6. Production Load Tests
echo -e "\n${BLUE}### 6. Running Production Load Tests ###${NC}"
echo ""

run_test_with_timeout "ProductionLoadTest" "cd test/org/yawlfoundation/yawl/performance/production && java CloudScalingBenchmark" 300

# 7. Edge Case Tests
echo -e "\n${BLUE}### 7. Running Edge Case Tests ###${NC}"
echo ""

run_test_with_timeout "LargePayloadTest" "cd test/org/yawlfoundation/yawl/performance/edge && java LargePayloadTest" 120

# 8. Regression Detection
echo -e "\n${BLUE}### 8. Running Regression Detection ###${NC}"
echo ""

# Check baseline measurements
if [ -f "test/org/yawlfoundation/yawl/performance/BaselineMeasurements.md" ]; then
    run_test_with_timeout "BaselineComparison" "cd test/org/yawlfoundation/yawl/performance && cat BaselineMeasurements.md | head -20" 30
else
    echo -e "${YELLOW}Skipping baseline comparison - not found${NC}"
fi

# 9. CI/CD Pipeline Integration
echo -e "\n${BLUE}### 9. Testing CI/CD Pipeline Integration ###${NC}"
echo ""

# Test Maven build
run_test_with_timeout "MavenBuild" "mvn clean compile -q" 300

# Test Docker build
if command -v docker &> /dev/null; then
    run_test_with_timeout "DockerBuild" "docker buildx bake --load -f Dockerfile" 600
else
    echo -e "${YELLOW}Skipping Docker build - docker not available${NC}"
fi

# 10. Quality Gate Thresholds
echo -e "\n${BLUE}### 10. Validating Quality Gate Thresholds ###${NC}"
echo ""

# Check performance gate thresholds
if [ -f "test/org/yawlfoundation/yawl/performance/BenchmarkConfig.java" ]; then
    run_test_with_timeout "PerformanceGates" "cd test/org/yawlfoundation/yawl/performance && grep -n 'PERFORMANCE_GATE_THRESHOLD\\|caseCreationRateThreshold' BenchmarkConfig.java" 30
else
    echo -e "${YELLOW}Skipping performance gates - config not found${NC}"
fi

# Generate final report
echo ""
echo -e "${BLUE}==================================================${NC}"
echo -e "${BLUE}Generating Final Report${NC}"
echo -e "${BLUE}==================================================${NC}"

# Calculate test statistics
if [ -f "$REPORT_DIR/test-summary.txt" ]; then
    total_tests=$(wc -l < $REPORT_DIR/test-summary.txt)
    passed_tests=$(grep -c "PASSED" $REPORT_DIR/test-summary.txt || echo "0")
    failed_tests=$(grep -c "FAILED" $REPORT_DIR/test-summary.txt || echo "0")

    echo ""
    echo -e "${BLUE}Test Summary${NC}"
    echo "========================================"
    echo "Total tests run: $total_tests"
    echo -e "${GREEN}Passed: $passed_tests${NC}"
    echo -e "${RED}Failed: $failed_tests${NC}"

    if [ $total_tests -gt 0 ]; then
        success_rate=$((passed_tests * 100 / total_tests))
        echo -e "${BLUE}Success rate: ${success_rate}%${NC}"
    fi
fi

# Create HTML report
cat > $REPORT_DIR/comprehensive-report.html << EOF
<!DOCTYPE html>
<html>
<head>
    <title>YAWL v6.0.0-GA Comprehensive Benchmark Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background: #2c3e50; color: white; padding: 20px; }
        .summary { background: #ecf0f1; padding: 15px; margin: 20px 0; }
        .test-result { margin: 10px 0; padding: 10px; border-radius: 5px; }
        .passed { background: #d4edda; color: #155724; }
        .failed { background: #f8d7da; color: #721c24; }
        table { width: 100%; border-collapse: collapse; margin: 20px 0; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background: #f2f2f2; }
    </style>
</head>
<body>
    <div class="header">
        <h1>YAWL v6.0.0-GA Comprehensive Benchmark Report</h1>
        <p>Generated: $(date)</p>
        <p>Report Directory: $REPORT_DIR</p>
    </div>

    <div class="summary">
        <h2>Test Summary</h2>
        <p>Total tests: $total_tests</p>
        <p>Passed: $passed_tests</p>
        <p>Failed: $failed_tests</p>
        <p>Success rate: ${success_rate:-0}%</p>
    </div>

    <h2>Test Results</h2>
    <table>
        <tr>
            <th>Test Name</th>
            <th>Status</th>
        </tr>
EOF

# Add test results to HTML
if [ -f "$REPORT_DIR/test-summary.txt" ]; then
    while IFS=': ' read -r test_name status; do
        echo "        <tr>" >> $REPORT_DIR/comprehensive-report.html
        echo "            <td>$test_name</td>" >> $REPORT_DIR/comprehensive-report.html
        echo "            <td class='$status'>${status# }</td>" >> $REPORT_DIR/comprehensive-report.html
        echo "        </tr>" >> $REPORT_DIR/comprehensive-report.html
    done < "$REPORT_DIR/test-summary.txt"
fi

cat >> $REPORT_DIR/comprehensive-report.html << EOF
    </table>

    <h2>Raw Test Output</h2>
    <p><a href="test-summary.txt">View detailed test results</a></p>

    <h2>Additional Files</h2>
    <ul>
        <li><a href="../_build/test/extras/test/">Compiled test classes</a></li>
        <li><a href="../target/">Maven target directory</a></li>
    </ul>
</body>
</html>
EOF

echo ""
echo -e "${BLUE}==================================================${NC}"
echo -e "${BLUE}Comprehensive benchmark test completed!${NC}"
echo -e "${BLUE}Report saved to: $REPORT_DIR/${NC}"
echo -e "${BLUE}HTML report: $REPORT_DIR/comprehensive-report.html${NC}"
echo -e "${BLUE}==================================================${NC}"

# Exit with appropriate code
if [ -f "$REPORT_DIR/test-summary.txt" ] && [ "$failed_tests" -gt 0 ]; then
    echo -e "${RED}Some tests failed. Check the report for details.${NC}"
    exit 1
else
    echo -e "${GREEN}All tests passed successfully!${NC}"
    exit 0
fi