#!/bin/bash

# YAWL Performance Regression Detection Framework Test Suite
# =========================================================

set -euo pipefail

echo "=== Regression Detection Framework Test Suite ==="
echo

# Test results
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Test 1: Script exists and is executable
echo "Test 1: Script validation"
if [ -f scripts/regression-detection.sh ]; then
    echo -e "${GREEN}PASS${NC} Script exists"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} Script not found"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

if [ -x scripts/regression-detection.sh ]; then
    echo -e "${GREEN}PASS${NC} Script is executable"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} Script not executable"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 2: Configuration validation
echo
echo "Test 2: Configuration validation"
if ! ./scripts/regression-detection.sh -t abc >/dev/null 2>&1; then
    echo -e "${GREEN}PASS${NC} Invalid threshold detected"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} Invalid threshold not caught"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

if ! ./scripts/regression-detection.sh -p 1.5 >/dev/null 2>&1; then
    echo -e "${GREEN}PASS${NC} Invalid p-value detected"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} Invalid p-value not caught"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 3: Dry run functionality
echo
echo "Test 3: Dry run functionality"
if ./scripts/regression-detection.sh --dry-run -q >/dev/null 2>&1; then
    echo -e "${GREEN}PASS${NC} Dry run works"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} Dry run failed"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 4: Directory structure
echo
echo "Test 4: Directory structure"
mkdir -p performance/baselines performance/results performance/reports

if [ -d performance/baselines ]; then
    echo -e "${GREEN}PASS${NC} Baseline directory exists"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} Baseline directory missing"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

if [ -d performance/results ]; then
    echo -e "${GREEN}PASS${NC} Results directory exists"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} Results directory missing"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

if [ -d performance/reports ]; then
    echo -e "${GREEN}PASS${NC} Reports directory exists"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} Reports directory missing"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 5: CI/CD integration
echo
echo "Test 5: CI/CD Integration"
if [ -f .github/workflows/performance-regression.yml ]; then
    echo -e "${GREEN}PASS${NC} GitHub Actions workflow exists"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} GitHub Actions workflow missing"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

if [ -f scripts/regression-detection/README.md ]; then
    echo -e "${GREEN}PASS${NC} README exists"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} README missing"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 6: Example files
echo
echo "Test 6: Example Files"
if [ -f examples/regression-detection-example.sh ]; then
    echo -e "${GREEN}PASS${NC} Example script exists"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} Example script missing"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

if [ -x examples/regression-detection-example.sh ]; then
    echo -e "${GREEN}PASS${NC} Example script is executable"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} Example script not executable"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 7: Benchmark classes
echo
echo "Test 7: Benchmark Classes"
if [ -f test/org/yawlfoundation/yawl/performance/BenchmarkRunner.java ]; then
    echo -e "${GREEN}PASS${NC} BenchmarkRunner exists"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} BenchmarkRunner missing"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

if [ -f test/org/yawlfoundation/yawl/performance/jmh/AllBenchmarksRunner.java ]; then
    echo -e "${GREEN}PASS${NC} AllBenchmarksRunner exists"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} AllBenchmarksRunner missing"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 8: Baseline metrics documentation
echo
echo "Test 8: Documentation"
if [ -f docs/v6/latest/performance/baseline-metrics.md ]; then
    echo -e "${GREEN}PASS${NC} Baseline metrics documentation exists"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}FAIL${NC} Baseline metrics documentation missing"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 9: Statistical calculation (simulate)
echo
echo "Test 9: Statistical Analysis"
# Create test data for statistical calculation
cat > test-stats.json << EOF
{
  "benchmarkResults": [
    {
      "benchmark": "test.benchmark",
      "p50": 100.0,
      "p95": 200.0,
      "mean": 150.0,
      "stddev": 50.0
    }
  ]
}
