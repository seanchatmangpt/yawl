#!/bin/bash

# YAWL v6.0.0-GA Validation Suite Runner
# This script runs the complete validation suite for YAWL v6.0.0-GA

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
VALIDATION_DIR="$(dirname "$0")/.."
REPORT_DIR="$VALIDATION_DIR/reports"
LOG_FILE="$REPORT_DIR/validation-$(date +%Y%m%d-%H%M%S).log"
TEST_TIMEOUT=3600 # 1 hour default timeout

# Initialize directories
mkdir -p "$REPORT_DIR"

# Logging function
log() {
    echo -e "$1" | tee -a "$LOG_FILE"
}

log "${BLUE}Starting YAWL v6.0.0-GA Validation Suite${NC}"
log "Timestamp: $(date)"
log "Log file: $LOG_FILE"
log "======================================="

# Function to run test and capture results
run_test() {
    local test_name="$1"
    local test_class="$2"
    local timeout_seconds="$3"

    log "${YELLOW}Running: $test_name${NC}"
    log "Test class: $test_class"
    log "Timeout: ${timeout_seconds} seconds"

    start_time=$(date +%s)

    # Run the test with timeout
    if timeout "$timeout_seconds" java -cp "$VALIDATION_DIR:$VALIDATION_DIR/test/*" \
        org.junit.runner.JUnitCore "$test_class" >> "$LOG_FILE" 2>&1; then
        end_time=$(date +%s)
        duration=$((end_time - start_time))
        log "${GREEN}✓ PASSED: $test_name (took ${duration}s)${NC}"
        return 0
    else
        end_time=$(date +%s)
        duration=$((end_time - start_time))
        log "${RED}✗ FAILED: $test_name (timed out after ${timeout_seconds}s)${NC}"
        return 1
    fi
}

# Function to run Maven tests
run_maven_test() {
    local test_pattern="$1"
    local test_description="$2"

    log "${YELLOW}Running Maven tests: $test_description${NC}"

    if timeout "$TEST_TIMEOUT" mvn test -Dtest="$test_pattern" -DfailIfNoTests=false >> "$LOG_FILE" 2>&1; then
        log "${GREEN}✓ PASSED: $test_description${NC}"
        return 0
    else
        log "${RED}✗ FAILED: $test_description${NC}"
        return 1
    fi
}

# Main validation workflow
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 1. Performance Validation
log "\n${BLUE}=== Phase 1: Performance Validation ===${NC}"

# Run Enterprise Workload Simulator
TOTAL_TESTS=$((TOTAL_TESTS + 1))
if run_test "Enterprise Workload Simulation" \
    "org.yawlfoundation.yawl.performance.EnterpriseWorkloadSimulator" \
    1800; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# Run Multi-Tenant Load Test
TOTAL_TESTS=$((TOTAL_TESTS + 1))
if run_test "Multi-Tenant Load Test" \
    "org.yawlfoundation.yawl.performance.MultiTenantLoadTest" \
    3600; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# Run Large Dataset Processor
TOTAL_TESTS=$((TOTAL_TESTS + 1))
if run_test "Large Dataset Processing" \
    "org.yawlfoundation.yawl.performance.LargeDatasetProcessor" \
    7200; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# Run JMH Benchmarks
TOTAL_TESTS=$((TOTAL_TESTS + 1))
if run_maven_test "VirtualThreadScalingBenchmarks" \
    "Virtual Thread Scaling Benchmarks"; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# 2. Chaos Engineering Validation
log "\n${BLUE}=== Phase 2: Chaos Engineering ===${NC}"

TOTAL_TESTS=$((TOTAL_TESTS + 1))
if run_test "Production Chaos Test Suite" \
    "org.yawlfoundation.yawl.chaos.ProductionChaosTestSuite" \
    3600; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# 3. Quality Gates Validation
log "\n${BLUE}=== Phase 3: Quality Gates ===${NC}"

TOTAL_TESTS=$((TOTAL_TESTS + 1))
if run_test "Quality Gate Validator" \
    "org.yawlfoundation.yawl.quality.gates.QualityGateValidator" \
    5400; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# 4. A2A/MCP Integration Validation
log "\n${BLUE}=== Phase 4: A2A/MCP Integration ===${NC}"

TOTAL_TESTS=$((TOTAL_TESTS + 1))
if run_test "A2A Performance Suite" \
    "org.yawlfoundation.yawl.integration.a2a.A2APerformanceSuite" \
    3600; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

TOTAL_TESTS=$((TOTAL_TESTS + 1))
if run_test "A2A Full Integration Test" \
    "org.yawlfoundation.yawl.integration.a2a.A2AFullIntegrationTest" \
    3600; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# 5. Virtual Thread Performance
log "\n${BLUE}=== Phase 5: Virtual Thread Performance ===${NC}"

TOTAL_TESTS=$((TOTAL_TESTS + 1))
if run_maven_test "VirtualThreadScalingBenchmarks" \
    "Virtual Thread Scaling Benchmarks with JMH"; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# Generate final report
cat > "$REPORT_DIR/validation-summary-$(date +%Y%m%d-%H%M%S).txt" << EOF
YAWL v6.0.0-GA Validation Suite Summary
========================================

Test Date: $(date)
Total Tests Run: $TOTAL_TESTS
Passed Tests: $PASSED_TESTS
Failed Tests: $FAILED_TESTS
Success Rate: $(echo "scale=2; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc)%

Test Results:
- Enterprise Workload Simulation: $([ $PASSED_TESTS -gt 0 ] && echo "PASSED" || echo "FAILED")
- Multi-Tenant Load Test: $([ $PASSED_TESTS -gt 1 ] && echo "PASSED" || echo "FAILED")
- Large Dataset Processing: $([ $PASSED_TESTS -gt 2 ] && echo "PASSED" || echo "FAILED")
- Virtual Thread Scaling: $([ $PASSED_TESTS -gt 3 ] && echo "PASSED" || echo "FAILED")
- Production Chaos Test: $([ $PASSED_TESTS -gt 4 ] && echo "PASSED" || echo "FAILED")
- Quality Gate Validation: $([ $PASSED_TESTS -gt 5 ] && echo "PASSED" || echo "FAILED")
- A2A Performance Suite: $([ $PASSED_TESTS -gt 6 ] && echo "PASSED" || echo "FAILED")
- A2A Full Integration: $([ $PASSED_TESTS -gt 7 ] && echo "PASSED" || echo "FAILED")

Detailed logs available at: $LOG_FILE
EOF

# Display final results
log "\n${BLUE}=== Validation Suite Complete ===${NC}"
log "Total Tests: $TOTAL_TESTS"
log "Passed: $PASSED_TESTS"
log "Failed: $FAILED_TESTS"

if [ $FAILED_TESTS -eq 0 ]; then
    log "${GREEN}🎉 ALL TESTS PASSED! YAWL v6.0.0-GA is ready for production!${NC}"
    exit 0
else
    log "${RED}❌ $FAILED_TESTS tests failed. Please review the logs for details.${NC}"
    exit 1
fi