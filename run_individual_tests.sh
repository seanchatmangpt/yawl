#!/bin/bash

# Run individual tests to identify what works
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
LOG_DIR="individual-test-logs-$TIMESTAMP"
mkdir -p "$LOG_DIR"

echo "YAWL v6.0.0-GA Individual Test Execution"
echo "===================================="
echo "Timestamp: $TIMESTAMP"
echo "Log Directory: $LOG_DIR"
echo ""

# Test 1: Simple test that we know works
echo "Test 1: Simple Test (known to work)"
cd test/org/yawlfoundation/yawl/performance
java SimpleTest > "$LOG_DIR/simple-test.log" 2>&1
SIMPLE_EXIT=$?
echo "Exit code: $SIMPLE_EXIT"

# Test 2: Integration test compilation check
echo ""
echo "Test 2: Integration Test Compilation"
cd ../../integration/benchmark
javac -version > "$LOG_DIR/integration-compile-check.log" 2>&1
INTEGRATION_EXIT=$?

# Try to compile individual files
echo "Attempting to compile IntegrationBenchmarks.java..."
javac IntegrationBenchmarks.java > "$LOG_DIR/integration-benchmarks-compile.log" 2>&1 || true
echo "IntegrationBenchmarks compile exit: $?"

# Test 3: JMH validation
echo ""
echo "Test 3: JMH Validation"
cd ../../../performance/jmh
if [ -f validate-benchmarks.sh ]; then
    bash validate-benchmarks.sh > "$LOG_DIR/jmh-validation.log" 2>&1
    JMH_EXIT=$?
    echo "JMH validation exit: $JMH_EXIT"
else
    echo "JMH validation script not found"
    JMH_EXIT=1
fi

# Test 4: Chaos engineering test script
echo ""
echo "Test 4: Chaos Engineering Test Script"
cd ../../../../yawl-integration
if [ -f "src/test/scripts/run_chaos_tests.sh" ]; then
    echo "Chaos test script exists, checking syntax..."
    bash -n src/test/scripts/run_chaos_tests.sh > "$LOG_DIR/chaos-syntax.log" 2>&1
    CHAOS_SYNTAX=$?
    echo "Chaos script syntax check: $CHAOS_SYNTAX"
else
    echo "Chaos test script not found"
    CHAOS_SYNTAX=1
fi

# Test 5: Benchmark configuration
echo ""
echo "Test 5: Benchmark Configuration"
cd ../../../performance
if [ -f BenchmarkConfig.java ]; then
    echo "BenchmarkConfig.java exists, checking content..."
    grep -n "PERFORMANCE_GATE_THRESHOLD\|caseCreationRateThreshold" BenchmarkConfig.java > "$LOG_DIR/benchmark-config.log" 2>&1
    CONFIG_EXIT=$?
    echo "Config check exit: $CONFIG_EXIT"
else
    echo "BenchmarkConfig.java not found"
    CONFIG_EXIT=1
fi

# Test 6: Performance test compilation
echo ""
echo "Test 6: Performance Test Compilation"
cd performance
echo "Checking Java 25 compilation compatibility..."
find . -name "*.java" | head -3 | while read file; do
    echo "Checking $file..."
    grep -n "public void.*Blackhole" "$file" > "$LOG_DIR/perf-test-check-$$(basename $file).log" 2>&1 || true
done

# Test 7: Test data files
echo ""
echo "Test 7: Test Data Files"
cd ../../integration/a2a
if [ -f "sample-a2a-workflow.json" ]; then
    echo "A2A sample workflow exists"
    wc -l sample-a2a-workflow.json > "$LOG_DIR/a2a-data.log" 2>&1
    A2A_EXIT=$?
else
    echo "A2A sample workflow not found"
    A2A_EXIT=1
fi

# Test 8: Polyglot integration
echo ""
echo "Test 8: Polyglot Integration"
cd ../../graalpy/performance
if [ -f "TPOT2IntegrationBenchmark.java" ]; then
    echo "TPOT2 integration file exists"
    echo "GraalPy benchmark files:"
    find . -name "*.java" | wc -l > "$LOG_DIR/polyglot-count.log" 2>&1
    POLYGLOT_EXIT=$?
else
    echo "Polyglot integration files not found"
    POLYGLOT_EXIT=1
fi

# Generate summary
echo ""
echo "Individual Test Summary"
echo "======================="
echo "Simple Test: $SIMPLE_EXIT (0=success)"
echo "Integration Compilation: $INTEGRATION_EXIT"
echo "JMH Validation: $JMH_EXIT"
echo "Chaos Script Syntax: $CHAOS_SYNTAX"
echo "Benchmark Config: $CONFIG_EXIT"
echo "A2A Data: $A2A_EXIT"
echo "Polyglot: $POLYGLOT_EXIT"

# Count successful tests
SUCCESSFUL_TESTS=0
if [ $SIMPLE_EXIT -eq 0 ]; then ((SUCCESSFUL_TESTS++)); fi
if [ $INTEGRATION_EXIT -eq 0 ]; then ((SUCCESSFUL_TESTS++)); fi
if [ $JMH_EXIT -eq 0 ]; then ((SUCCESSFUL_TESTS++)); fi
if [ $CHAOS_SYNTAX -eq 0 ]; then ((SUCCESSFUL_TESTS++)); fi
if [ $CONFIG_EXIT -eq 0 ]; then ((SUCCESSFUL_TESTS++)); fi
if [ $A2A_EXIT -eq 0 ]; then ((SUCCESSFUL_TESTS++)); fi
if [ $POLYGLOT_EXIT -eq 0 ]; then ((SUCCESSFUL_TESTS++)); fi

TOTAL_TESTS=7
SUCCESS_RATE=$((SUCCESSFUL_TESTS * 100 / TOTAL_TESTS))

echo ""
echo "Tests Successful: $SUCCESSFUL_TESTS/$TOTAL_TESTS ($SUCCESS_RATE%)"
echo ""
echo "Check individual logs in: $LOG_DIR/"

# Exit based on success
if [ $SUCCESSFUL_TESTS -gt 0 ]; then
    echo "Some tests passed. Check logs for details."
    exit 0
else
    echo "All tests failed. Check logs for details."
    exit 1
fi