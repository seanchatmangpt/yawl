#!/bin/bash

# Stress Test Runner Script for QLever
# This script runs comprehensive stress tests and generates reports

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEST_DIR="$PROJECT_ROOT/test/org/yawlfoundation/yawl/qlever"
REPORT_DIR="$PROJECT_ROOT/stress-test-reports"

# Logging function
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

# Print header
print_header() {
    echo "=============================================================="
    echo "        QLever Stress Test Suite"
    echo "=============================================================="
    echo "Project Root: $PROJECT_ROOT"
    echo "Test Directory: $TEST_DIR"
    echo "Report Directory: $REPORT_DIR"
    echo "=============================================================="
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check if we're in the right directory
    if [[ ! -f "$PROJECT_ROOT/pom.xml" ]]; then
        log_error "Not in a YAWL project root directory. Please run this script from the project root."
        exit 1
    fi

    # Check if test files exist
    if [[ ! -f "$TEST_DIR/QLeverStressTest.java" ]]; then
        log_error "QLeverStressTest.java not found at $TEST_DIR"
        exit 1
    fi

    # Check if Java is available
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed or not in PATH"
        exit 1
    fi

    # Check if Maven is available
    if ! command -v mvn &> /dev/null; then
        log_error "Maven is not installed or not in PATH"
        exit 1
    fi

    log_success "All prerequisites satisfied"
}

# Create report directory
create_report_directory() {
    log_info "Creating report directory..."
    mkdir -p "$REPORT_DIR"
    mkdir -p "$REPORT_DIR/reports"
    mkdir -p "$REPORT_DIR/logs"
    mkdir -p "$REPORT_DIR/data"
}

# Compile the test classes
compile_tests() {
    log_info "Compiling stress test classes..."
    mvn test-compile -DskipTests -q
    log_success "Test classes compiled successfully"
}

# Run individual stress test scenarios
run_concurrent_test() {
    local test_name="$1"
    local thread_count="$2"
    local duration="$3"

    log_info "Running $test_name with $thread_count threads for $duration seconds..."

    local start_time=$(date +%s)

    # Run the test and capture output
    mvn test -Dtest=QLeverStressTest#testConcurrentQueryThroughput \
        -DfailIfNoTests=false \
        -q \
        -Dtest.threadCount=$thread_count \
        -Dtest.duration=$duration \
        -Dtest.reportFile="$REPORT_DIR/reports/$test_name.json" || {
        log_error "Test $test_name failed"
        return 1
    }

    local end_time=$(date +%s)
    local duration_actual=$((end_time - start_time))

    log_success "$test_name completed in $duration_actual seconds"
}

run_memory_test() {
    log_info "Running memory pressure test..."

    start_time=$(date +%s)

    mvn test -Dtest=QLeverStressTest#testMemoryPressure \
        -DfailIfNoTests=false \
        -q \
        -Dtest.reportFile="$REPORT_DIR/reports/memory_test.json" || {
        log_error "Memory pressure test failed"
        return 1
    }

    end_time=$(date +%s)
    duration_actual=$((end_time - start_time))

    log_success "Memory test completed in $duration_actual seconds"
}

run_complexity_test() {
    log_info "Running query complexity test..."

    start_time=$(date +%s)

    mvn test -Dtest=QLeverStressTest#testQueryComplexityImpact \
        -DfailIfNoTests=false \
        -q \
        -Dtest.reportFile="$REPORT_DIR/reports/complexity_test.json" || {
        log_error "Complexity test failed"
        return 1
    }

    end_time=$(date +%s)
    duration_actual=$((end_time - start_time))

    log_success "Complexity test completed in $duration_actual seconds"
}

run_timeout_test() {
    log_info "Running timeout test..."

    start_time=$(date +%s)

    mvn test -Dtest=QLeverStressTest#testLongRunningQueries \
        -DfailIfNoTests=false \
        -q \
        -Dtest.reportFile="$REPORT_DIR/reports/timeout_test.json" || {
        log_error "Timeout test failed"
        return 1
    }

    end_time=$(date +%s)
    duration_actual=$((end_time - start_time))

    log_success "Timeout test completed in $duration_actual seconds"
}

run_resource_test() {
    log_info "Running resource exhaustion test..."

    start_time=$(date +%s)

    mvn test -Dtest=QLeverStressTest#testResourceExhaustion \
        -DfailIfNoTests=false \
        -q \
        -Dtest.reportFile="$REPORT_DIR/reports/resource_test.json" || {
        log_error "Resource exhaustion test failed"
        return 1
    }

    end_time=$(date +%s)
    duration_actual=$((end_time - start_time))

    log_success "Resource test completed in $duration_actual seconds"
}

run_rapid_cycle_test() {
    log_info "Running rapid open/close cycle test..."

    start_time=$(date +%s)

    mvn test -Dtest=QLeverStressTest#testRapidOpenCloseCycles \
        -DfailIfNoTests=false \
        -q \
        -Dtest.reportFile="$REPORT_DIR/reports/rapid_cycle_test.json" || {
        log_error "Rapid cycle test failed"
        return 1
    }

    end_time=$(date +%s)
    duration_actual=$((end_time - start_time))

    log_success "Rapid cycle test completed in $duration_actual seconds"
}

run_mixed_workload_test() {
    log_info "Running mixed workload test..."

    start_time=$(date +%s)

    mvn test -Dtest=QLeverStressTest#testMixedWorkload \
        -DfailIfNoTests=false \
        -q \
        -Dtest.reportFile="$REPORT_DIR/reports/mixed_workload_test.json" || {
        log_error "Mixed workload test failed"
        return 1
    }

    end_time=$(date +%s)
    duration_actual=$((end_time - start_time))

    log_success "Mixed workload test completed in $duration_actual seconds"
}

run_breaking_point_test() {
    log_info "Running breaking point identification test..."

    start_time=$(date +%s)

    mvn test -Dtest=QLeverStressTest#testBreakingPointIdentification \
        -DfailIfNoTests=false \
        -q \
        -Dtest.reportFile="$REPORT_DIR/reports/breaking_point_test.json" || {
        log_error "Breaking point test failed"
        return 1
    }

    end_time=$(date +%s)
    duration_actual=$((end_time - start_time))

    log_success "Breaking point test completed in $duration_actual seconds"
}

# Run comprehensive test using StressTestRunner
run_comprehensive_test() {
    log_info "Running comprehensive stress test suite..."

    start_time=$(date +%s)

    # Compile the StressTestRunner
    mvn compile -q -DskipTests

    # Run comprehensive test
    mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.qlever.StressTestRunner" \
        -Dexec.args="-DreportFile=\"$REPORT_DIR/comprehensive_report.json\"" \
        -q || {
        log_error "Comprehensive test failed"
        return 1
    }

    end_time=$(date +%s)
    duration_actual=$((end_time - start_time))

    log_success "Comprehensive test completed in $duration_actual seconds"
}

# Generate comprehensive report
generate_report() {
    log_info "Generating comprehensive stress test report..."

    local report_file="$REPORT_DIR/stress-test-report-$(date +%Y%m%d_%H%M%S).md"

    cat > "$report_file" << 'EOF'
# QLever Stress Test Report

## Test Summary
This report summarizes the comprehensive stress test suite performed on the QLever engine.

* **Test Date:** $(date)
* **Generated By:** run-stress-tests.sh
* **Project Root:** $PROJECT_ROOT

---

## Test Scenarios

### 1. Concurrent Query Throughput
Testing system performance under concurrent query loads.

| Thread Count | Duration | Status |
|--------------|----------|--------|
EOF

    # Add concurrent test results
    for threads in 10 50 100 200 500; do
        local result_file="$REPORT_DIR/reports/concurrent_test_$threads.json"
        if [[ -f "$result_file" ]]; then
            status="✅ Completed"
        else
            status="❌ Failed"
        fi
        echo "| $threads | 30s | $status |" >> "$report_file"
    done

    cat >> "$report_file" << 'EOF'

### 2. Memory Pressure Test
Testing system behavior under memory pressure from large result sets.

### 3. Query Complexity Impact
Analyzing how query complexity affects performance.

| Query Type | Status |
|------------|--------|
EOF

    # Add complexity test results
    local query_types=("Simple" "Medium" "Complex" "Metadata")
    for type in "${query_types[@]}"; do
        local result_file="$REPORT_DIR/reports/complexity_$type.json"
        if [[ -f "$result_file" ]]; then
            status="✅ Completed"
        else
            status="❌ Failed"
        fi
        echo "| $type | $status |" >> "$report_file"
    done

    cat >> "$report_file" << 'EOF'

### 4. Long Running Queries with Timeout
Testing timeout mechanism for long-running queries.

### 5. Resource Exhaustion Test
Testing behavior with many open resource handles.

### 6. Rapid Open/Close Cycles
Testing rapid creation and destruction of engine instances.

### 7. Mixed Workload Test
Testing system performance with mixed read and metadata queries.

### 8. Breaking Point Identification
Identifying system limits and breaking points.

---

## Performance Metrics

### Summary Statistics
- **Total Test Duration:** TBD
- **Total Queries Executed:** TBD
- **Peak Throughput:** TBD queries/second
- **Average Latency:** TBD ms
- **Error Rate:** TBD%

### Key Findings
 TBD

---

## Recommendations
 TBD

---

## Appendix

### Test Environment
- Java Version: $(java -version 2>&1 | head -n1)
- Maven Version: $(mvn -version 2>&1 | head -n1)
- CPU: $(nproc) cores
- Memory: $(free -h | grep Mem | awk '{print $2}')

### Command Line Arguments
EOF

    # Add command line arguments used
    echo "" >> "$report_file"
    echo "mvn test \\\" >> "$report_file"
    echo "  -Dtest=QLeverStressTest \\\" >> "$report_file"
    echo "  -DfailIfNoTests=false \\\" >> "$report_file"
    echo "  -q \\\" >> "$report_file"
    echo "  -Dtest.reportFile=\\\"\\$REPORT_DIR/reports/\\\"\\\" >> "$report_file"

    log_success "Comprehensive report generated: $report_file"
}

# Clean up function
cleanup() {
    log_info "Cleaning up temporary files..."
    # Clean up any temporary files created during testing
    find "$TEST_DIR" -name "*.log" -delete 2>/dev/null || true
    log_success "Cleanup completed"
}

# Main execution
main() {
    print_header

    # Check prerequisites
    check_prerequisites

    # Create report directory
    create_report_directory

    # Compile tests
    compile_tests

    # Run all test scenarios
    log_info "Starting stress test execution..."

    # Run concurrent tests
    run_concurrent_test "concurrent_test_10" 10 30
    run_concurrent_test "concurrent_test_50" 50 30
    run_concurrent_test "concurrent_test_100" 100 30
    run_concurrent_test "concurrent_test_200" 200 30
    run_concurrent_test "concurrent_test_500" 500 30

    # Run other tests
    run_memory_test
    run_complexity_test
    run_timeout_test
    run_resource_test
    run_rapid_cycle_test
    run_mixed_workload_test
    run_breaking_point_test
    run_comprehensive_test

    # Generate report
    generate_report

    # Clean up
    cleanup

    log_success "All stress tests completed successfully!"

    # Generate additional reports using Python script
    if command -v python3 &> /dev/null; then
        log_info "Generating additional reports..."
        python3 scripts/generate-stress-report.py --report-dir "$REPORT_DIR"
    else
        log_warning "Python3 not found, skipping additional report generation"
    fi
}

# Run main function
main "$@"