#!/bin/bash

#
# Parallel Stress Test Orchestration Script
# Runs all 3 tests + analyzer + dashboard in background
#

set -euo pipefail

YAWL_HOME="/home/user/yawl"
METRICS_DIR="${YAWL_HOME}/stress-test-results/metrics"
LOGS_DIR="${YAWL_HOME}/stress-test-results/logs"

# Create directories
mkdir -p "${METRICS_DIR}" "${LOGS_DIR}"

echo "========================================"
echo "YAWL Parallel Stress Test Orchestrator"
echo "========================================"
echo "Metrics: ${METRICS_DIR}"
echo "Logs: ${LOGS_DIR}"
echo

# Build
echo "[1/6] Building YAWL..."
mvn -q -pl yawl-benchmark clean compile

# Ensure metrics directory exists
echo "[2/6] Preparing metrics directory..."
rm -f "${METRICS_DIR}"/*

# Start analyzer in background
echo "[3/6] Starting Real-time Analyzer..."
cd "${YAWL_HOME}"
nohup java -cp "yawl-benchmark/target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout -pl yawl-benchmark 2>/dev/null)" \
  org.yawlfoundation.yawl.benchmark.soak.RealtimeMetricsAnalyzer \
  "${METRICS_DIR}" \
  "${METRICS_DIR}/realtime-analysis.txt" \
  false \
  > "${LOGS_DIR}/analyzer.log" 2>&1 &

ANALYZER_PID=$!
echo "[ANALYZER] PID: ${ANALYZER_PID}"
sleep 2

# Start dashboard in background
echo "[4/6] Starting Metrics Dashboard..."
nohup bash scripts/metrics-dashboard.sh "${METRICS_DIR}" 15 \
  > "${LOGS_DIR}/dashboard.log" 2>&1 &

DASHBOARD_PID=$!
echo "[DASHBOARD] PID: ${DASHBOARD_PID}"
sleep 1

# Start stress tests in background
echo "[5/6] Launching 3 Parallel Stress Tests..."

# Conservative: 3600s @ 10 cases/sec
mvn -q -pl yawl-benchmark test -Dtest=SoakTestRunner \
  -Dsoak.duration.seconds=3600 \
  -Dsoak.rate.cases.per.second=10 \
  > "${LOGS_DIR}/conservative.log" 2>&1 &
CONSERVATIVE_PID=$!
echo "[CONSERVATIVE] PID: ${CONSERVATIVE_PID} (10 cases/sec, 1 hour)"

# Moderate: 3600s @ 50 cases/sec
mvn -q -pl yawl-benchmark test -Dtest=SoakTestRunner \
  -Dsoak.duration.seconds=3600 \
  -Dsoak.rate.cases.per.second=50 \
  > "${LOGS_DIR}/moderate.log" 2>&1 &
MODERATE_PID=$!
echo "[MODERATE] PID: ${MODERATE_PID} (50 cases/sec, 1 hour)"

# Aggressive: 3600s @ 200 cases/sec
mvn -q -pl yawl-benchmark test -Dtest=SoakTestRunner \
  -Dsoak.duration.seconds=3600 \
  -Dsoak.rate.cases.per.second=200 \
  > "${LOGS_DIR}/aggressive.log" 2>&1 &
AGGRESSIVE_PID=$!
echo "[AGGRESSIVE] PID: ${AGGRESSIVE_PID} (200 cases/sec, 1 hour)"

echo
echo "[6/6] Monitoring Test Execution..."
echo
echo "========================================="
echo "Processes Running:"
echo "  Analyzer (PID ${ANALYZER_PID}): tail -f ${LOGS_DIR}/analyzer.log"
echo "  Dashboard (PID ${DASHBOARD_PID}): view in separate terminal or tail -f ${LOGS_DIR}/dashboard.log"
echo "  Conservative (PID ${CONSERVATIVE_PID}): tail -f ${LOGS_DIR}/conservative.log"
echo "  Moderate (PID ${MODERATE_PID}): tail -f ${LOGS_DIR}/moderate.log"
echo "  Aggressive (PID ${AGGRESSIVE_PID}): tail -f ${LOGS_DIR}/aggressive.log"
echo
echo "Dashboard:"
echo "  bash scripts/metrics-dashboard.sh ${METRICS_DIR} 15"
echo
echo "Metrics:"
echo "  tail -f ${METRICS_DIR}/realtime-analysis.txt"
echo
echo "To stop all: bash scripts/stop-stress-tests.sh"
echo "========================================="
echo

# Save PIDs
{
    echo "ANALYZER_PID=${ANALYZER_PID}"
    echo "DASHBOARD_PID=${DASHBOARD_PID}"
    echo "CONSERVATIVE_PID=${CONSERVATIVE_PID}"
    echo "MODERATE_PID=${MODERATE_PID}"
    echo "AGGRESSIVE_PID=${AGGRESSIVE_PID}"
} > "${LOGS_DIR}/.pids"

# Wait for stress tests to complete
wait ${CONSERVATIVE_PID} ${MODERATE_PID} ${AGGRESSIVE_PID}

echo
echo "========================================"
echo "Stress Tests Complete"
echo "========================================"
echo "Stopping analyzer..."
kill ${ANALYZER_PID} 2>/dev/null || true
kill ${DASHBOARD_PID} 2>/dev/null || true

sleep 2

echo "Final Results:"
echo
echo "Metrics files:"
ls -lh "${METRICS_DIR}"/metrics-*.jsonl

echo
echo "Analysis files:"
ls -lh "${METRICS_DIR}"/analysis-*.json

echo
echo "Summary:"
tail -20 "${METRICS_DIR}/realtime-analysis.txt"

echo
echo "All data available in: ${METRICS_DIR}"
