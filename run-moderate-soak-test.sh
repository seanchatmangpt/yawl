#!/bin/bash

# Moderate Load Soak Test Runner - 4 hour test at 1000 cases/sec
# Duration: 4 hours = 14,400 seconds
# Expected cases: ~14.4M created during test

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_START=$(date '+%Y%m%d-%H%M%S')
RESULTS_DIR="$SCRIPT_DIR/benchmark-results"

echo "==============================================="
echo "YAWL Moderate Load Soak Test (4 hours)"
echo "==============================================="
echo "Start time: $TEST_START"
echo "Duration: 4 hours"
echo "Case creation rate: 1000 cases/sec"
echo "Expected total cases: ~14.4M"
echo "Load profile: POISSON"
echo "Heap: 8GB, ZGC, Compact headers"
echo ""

mkdir -p "$RESULTS_DIR"

# Run the test with moderate load parameters
mvn test -pl yawl-benchmark -P soak-tests \
  -Dsoak.duration.hours=4 \
  -Dsoak.rate.cases.per.second=1000 \
  -Dsoak.rate.tasks.per.second=5000 \
  -Dsoak.load.profile=POISSON \
  -Dsoak.metrics.sample.interval.min=2 \
  -Dsoak.gc.pause.warning.ms=100 \
  -Dsoak.heap.warning.threshold.mb=700 \
  -Dsoak.throughput.cliff.percent=30 \
  -Dargument.compile="-Xms8g -Xmx8g -XX:+UseZGC -XX:+UseCompactObjectHeaders -XX:+DisableExplicitGC -XX:+AlwaysPreTouch" \
  2>&1 | tee "$RESULTS_DIR/soak-test-moderate-${TEST_START}.log"

TEST_END=$(date '+%Y%m%d-%H%M%S')
echo ""
echo "==============================================="
echo "Test execution completed"
echo "Start: $TEST_START"
echo "End:   $TEST_END"
echo "Results: $RESULTS_DIR"
echo "==============================================="
