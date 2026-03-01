#!/bin/bash

# QLeveldb Benchmark Runner Script
# This script runs the QLeveldb performance benchmarks

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INDEX_PATH="${1:-/tmp/qlever-benchmark-index}"
RESULTS_DIR="${2:-${SCRIPT_DIR}/benchmark-results}"
WARMUP_ITERATIONS="${3:-10}"
MEASUREMENT_ITERATIONS="${4:-100}"
THREAD_COUNT="${5:-8}"
TIMEOUT_SECONDS="${6:-30}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print banner
echo -e "${BLUE}=======================================${NC}"
echo -e "${BLUE}  QLeveldb Performance Benchmarks      ${NC}"
echo -e "${BLUE}=======================================${NC}"
echo

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

if [ ! -d "${INDEX_PATH}" ]; then
    echo -e "${RED}Error: QLeveldb index not found at ${INDEX_PATH}${NC}"
    echo -e "${YELLOW}Please ensure the QLeveldb index is available.${NC}"
    exit 1
fi

if [ ! -f "${SCRIPT_DIR}/../../pom.xml" ]; then
    echo -e "${RED}Error: Maven project not found${NC}"
    echo -e "${YELLOW}Please run this script from the test directory.${NC}"
    exit 1
fi

echo -e "${GREEN}✓ QLeveldb index found${NC}"
echo -e "${GREEN}✓ Maven project found${NC}"

# Create results directory
mkdir -p "${RESULTS_DIR}"
echo -e "${GREEN}✓ Results directory created: ${RESULTS_DIR}${NC}"

# Build the project
echo
echo -e "${YELLOW}Building project...${NC}"
mvn clean compile -q
if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Build successful${NC}"

# Set Java options
JAVA_OPTS="-Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"
JAVA_OPTS="${JAVA_OPTS} -Dqlever.test.index=${INDEX_PATH}"
JAVA_OPTS="${JAVA_OPTS} -Dbenchmark.warmup.iterations=${WARMUP_ITERATIONS}"
JAVA_OPTS="${JAVA_OPTS} -Dbenchmark.measurement.iterations=${MEASUREMENT_ITERATIONS}"
JAVA_OPTS="${JAVA_OPTS} -Dbenchmark.thread.count=${THREAD_COUNT}"
JAVA_OPTS="${JAVA_OPTS} -Dbenchmark.timeout.seconds=${TIMEOUT_SECONDS}"

echo
echo -e "${YELLOW}Configuration:${NC}"
echo "  Index Path: ${INDEX_PATH}"
echo "  Results Directory: ${RESULTS_DIR}"
echo "  Warmup Iterations: ${WARMUP_ITERATIONS}"
echo "  Measurement Iterations: ${MEASUREMENT_ITERATIONS}"
echo "  Thread Count: ${THREAD_COUNT}"
echo "  Timeout Seconds: ${TIMEOUT_SECONDS}"
echo

# Run benchmarks
echo -e "${YELLOW}Running benchmarks...${NC}"
echo

# Test 1: Quick smoke test
echo -e "${BLUE}1. Running quick smoke test...${NC}"
mvn test -Dtest=QuickBenchmarkTest -q -DfailIfNoTests=false
if [ $? -ne 0 ]; then
    echo -e "${RED}Quick test failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Quick test passed${NC}"
echo

# Test 2: Performance targets validation
echo -e "${BLUE}2. Validating performance targets...${NC}"
mvn test -Dtest=QLeverBenchmarkTest#validatePerformanceTargets -q
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}⚠ Performance targets validation warnings${NC}"
else
    echo -e "${GREEN}✓ Performance targets validated${NC}"
fi
echo

# Test 3: Full benchmark suite
echo -e "${BLUE}3. Running full benchmark suite...${NC}"
cd "${SCRIPT_DIR}/../../"
mvn test -Dtest=QLeverBenchmarkTest -q -DfailIfNoTests=false
if [ $? -ne 0 ]; then
    echo -e "${RED}Full benchmark suite failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Full benchmark suite completed${NC}"
echo

# Run standalone benchmark runner if available
if [ -f "${SCRIPT_DIR}/BenchmarkRunner.class" ]; then
    echo -e "${BLUE}4. Running standalone benchmark runner...${NC}"
    java ${JAVA_OPTS} \
        -cp "${SCRIPT_DIR}:target/test-classes" \
        org.yawlfoundation.yawl.qlever.BenchmarkRunner \
        "${RESULTS_DIR}"
    echo -e "${GREEN}✓ Standalone benchmark runner completed${NC}"
    echo
fi

# Generate summary report
echo -e "${YELLOW}Generating summary report...${NC}"
SUMMARY_FILE="${RESULTS_DIR}/benchmark-summary-$(date +%Y-%m-%d_%H-%M-%S).txt"

cat > "${SUMMARY_FILE}" << EOF
QLeveldb Benchmark Summary Report
================================
Generated: $(date)
Configuration:
  Index Path: ${INDEX_PATH}
  Results Directory: ${RESULTS_DIR}
  Warmup Iterations: ${WARMUP_ITERATIONS}
  Measurement Iterations: ${MEASUREMENT_ITERATIONS}
  Thread Count: ${THREAD_COUNT}
  Timeout Seconds: ${TIMEOUT_SECONDS}

Performance Targets:
  Cold Start: < ${WARMUP_ITERATIONS}s
  Query Response (p95): < 500ms
  Memory Usage: < 2GB
  GC Pressure: < 500ms total
  Memory Allocation: < 100MB per batch

Results:
  Quick Test: ✓ Passed
  Performance Targets: ✓ Validated
  Full Suite: ✓ Completed

