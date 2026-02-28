#!/bin/bash
set -euo pipefail

#
# YAWL Stress Test Orchestrator
# Runs 3 parallel stress tests (conservative, moderate, aggressive)
# with real-time metrics analysis
#

YAWL_HOME="/home/user/yawl"
METRICS_DIR="${YAWL_HOME}/stress-test-results/metrics"
LOGS_DIR="${YAWL_HOME}/stress-test-results/logs"
REPORTS_DIR="${YAWL_HOME}/stress-test-results/reports"

# Test configurations: (name, duration_secs, rate_cases_per_sec)
CONSERVATIVE_DURATION=3600
CONSERVATIVE_RATE=10

MODERATE_DURATION=3600
MODERATE_RATE=50

AGGRESSIVE_DURATION=3600
AGGRESSIVE_RATE=200

# Create directories
mkdir -p "${METRICS_DIR}" "${LOGS_DIR}" "${REPORTS_DIR}"

echo "========================================"
echo "YAWL Stress Test Orchestrator"
echo "========================================"
echo "Start time: $(date)"
echo "Metrics dir: ${METRICS_DIR}"
echo "Logs dir: ${LOGS_DIR}"
echo "Reports dir: ${REPORTS_DIR}"
echo

# Build the project
echo "[1/5] Building YAWL project..."
cd "${YAWL_HOME}"
bash scripts/dx.sh -pl yawl-benchmark > "${LOGS_DIR}/build.log" 2>&1

# Function to run a stress test
run_stress_test() {
    local test_name="$1"
    local duration="$2"
    local rate="$3"

    echo "[STRESS] Starting $test_name test (${duration}s at ${rate} cases/sec)..."

    mvn -q -pl yawl-benchmark test \
        -Dtest=SoakTestRunner \
        -Dsoak.duration.seconds="${duration}" \
        -Dsoak.rate.cases.per.second="${rate}" \
        > "${LOGS_DIR}/${test_name}.log" 2>&1 &

    local pid=$!
    echo "[STRESS] $test_name test running as PID $pid"
    echo "$pid" > "${LOGS_DIR}/${test_name}.pid"
}

# Launch 3 stress tests in parallel
echo "[2/5] Launching 3 parallel stress tests..."
run_stress_test "conservative" "${CONSERVATIVE_DURATION}" "${CONSERVATIVE_RATE}"
run_stress_test "moderate" "${MODERATE_DURATION}" "${MODERATE_RATE}"
run_stress_test "aggressive" "${AGGRESSIVE_DURATION}" "${AGGRESSIVE_RATE}"

sleep 2

# Start the real-time analyzer
echo "[3/5] Starting real-time metrics analyzer..."
cd "${YAWL_HOME}"

java -cp "yawl-benchmark/target/classes:${HOME}/.m2/repository/org/junit/junit-bom/5.10.0/junit-platform-console-standalone.jar" \
    org.yawlfoundation.yawl.benchmark.soak.RealtimeMetricsAnalyzer \
    "${METRICS_DIR}" \
    "${METRICS_DIR}/realtime-analysis.txt" \
    false \
    > "${LOGS_DIR}/analyzer.log" 2>&1 &

ANALYZER_PID=$!
echo "[MONITOR] Real-time analyzer running as PID ${ANALYZER_PID}"

# Wait for all stress tests to complete
echo "[4/5] Waiting for stress tests to complete..."
wait

# Stop the analyzer
kill ${ANALYZER_PID} 2>/dev/null || true
sleep 1

echo "[5/5] Generating final reports..."

# Collect all metrics into a summary
{
    echo "# Stress Test Results Summary"
    echo "# Generated: $(date)"
    echo
    echo "## Metrics Files:"
    ls -lh "${METRICS_DIR}"/metrics-*.jsonl 2>/dev/null || echo "No metrics files found"
    echo
    echo "## Analysis Files:"
    ls -lh "${METRICS_DIR}"/analysis-*.json 2>/dev/null || echo "No analysis files found"
    echo
    echo "## Real-time Analysis:"
    if [ -f "${METRICS_DIR}/realtime-analysis.txt" ]; then
        tail -50 "${METRICS_DIR}/realtime-analysis.txt"
    fi
} > "${REPORTS_DIR}/summary.txt"

echo
echo "========================================"
echo "Stress Test Complete"
echo "========================================"
echo "End time: $(date)"
echo "Metrics available in: ${METRICS_DIR}"
echo "Full summary in: ${REPORTS_DIR}/summary.txt"
echo "View analyzer output: tail -f ${LOGS_DIR}/analyzer.log"
echo
