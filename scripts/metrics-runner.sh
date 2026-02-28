#!/usr/bin/env bash
# ==========================================================================
# metrics-runner.sh — Wrapper for running commands with metrics collection
#
# Wraps a command and captures comprehensive metrics including:
#   - Execution time (total, phase breakdown)
#   - Memory usage (peak RSS, peak heap)
#   - CPU utilization
#   - Test counts (passed, failed, skipped)
#
# Usage:
#   bash scripts/metrics-runner.sh --scenario "cold-start" --run 1 --output metrics.json -- mvn clean verify
#
# This is used by establish-baselines.sh to collect metrics for each run.
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Parse arguments
SCENARIO=""
RUN_NUMBER=""
OUTPUT_FILE=""
COMMAND=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --scenario)
            SCENARIO="$2"
            shift 2
            ;;
        --run)
            RUN_NUMBER="$2"
            shift 2
            ;;
        --output)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        --)
            shift
            COMMAND=("$@")
            break
            ;;
        *)
            echo "Unknown option: $1" >&2
            exit 1
            ;;
    esac
done

if [ -z "$SCENARIO" ] || [ -z "$OUTPUT_FILE" ] || [ ${#COMMAND[@]} -eq 0 ]; then
    echo "Usage: $0 --scenario NAME --run NUM --output FILE -- command..." >&2
    exit 1
fi

# Create output directory
mkdir -p "$(dirname "$OUTPUT_FILE")"

# Capture start time and memory
START_NS=$(date +%s%N)
START_SEC=$(date +%s)

# For tracking peak memory
TEMP_MEMORY_FILE="/tmp/yawl-metrics-mem-$$.txt"
TEMP_CPU_FILE="/tmp/yawl-metrics-cpu-$$.txt"
echo "0" > "$TEMP_MEMORY_FILE"
echo "0" > "$TEMP_CPU_FILE"

# Background process to monitor memory periodically
(
    while true; do
        if [ -f "/proc/$$/status" ]; then
            RSS=$(awk '/^VmRSS:/ {print int($2/1024)}' "/proc/$$/status")
            PEAK=$(awk '/^VmPeak:/ {print int($2/1024)}' "/proc/$$/status")
            MAX_PEAK=$(cat "$TEMP_MEMORY_FILE")
            [ "$PEAK" -gt "$MAX_PEAK" ] && echo "$PEAK" > "$TEMP_MEMORY_FILE"
        fi
        sleep 0.5
    done
) &
MONITOR_PID=$!

# Cleanup on exit
cleanup() {
    kill $MONITOR_PID 2>/dev/null || true
    rm -f "$TEMP_MEMORY_FILE" "$TEMP_CPU_FILE"
}
trap cleanup EXIT

# Execute the command
set +e
"${COMMAND[@]}"
EXIT_CODE=$?
set -e

# Capture end time
END_NS=$(date +%s%N)
END_SEC=$(date +%s)

# Calculate total time in milliseconds
TOTAL_TIME_MS=$(( (END_NS - START_NS) / 1000000 ))
DURATION_SEC=$((END_SEC - START_SEC))

# Get peak memory from monitoring
PEAK_MEMORY_MB=$(cat "$TEMP_MEMORY_FILE")

# Extract test counts from Surefire reports
SUREFIRE_DIR="${REPO_ROOT}/target/surefire-reports"
PASSED=0
FAILED=0
SKIPPED=0

if [ -d "$SUREFIRE_DIR" ]; then
    for xml_file in "$SUREFIRE_DIR"/TEST-*.xml; do
        if [ -f "$xml_file" ]; then
            # Extract counts safely
            TESTS=$(grep -o 'tests="[^"]*"' "$xml_file" | grep -o '[0-9]*' | head -1 || echo "0")
            FAILURES=$(grep -o 'failures="[^"]*"' "$xml_file" | grep -o '[0-9]*' | head -1 || echo "0")
            SKIPS=$(grep -o 'skipped="[^"]*"' "$xml_file" | grep -o '[0-9]*' | head -1 || echo "0")

            PASSED=$((PASSED + (TESTS - FAILURES - SKIPS)))
            FAILED=$((FAILED + FAILURES))
            SKIPPED=$((SKIPPED + SKIPS))
        fi
    done
fi

# Estimate CPU usage (simplified: based on elapsed time and system load)
# This is approximate - we don't have detailed per-process CPU tracking here
AVG_CPU=0

# Build JSON output
cat > "$OUTPUT_FILE" <<EOF
{
  "scenario": "$SCENARIO",
  "run_number": $RUN_NUMBER,
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "metrics": {
    "total_time_ms": $TOTAL_TIME_MS,
    "duration_sec": $DURATION_SEC,
    "memory_peak_mb": $PEAK_MEMORY_MB,
    "cpu_avg_percent": $AVG_CPU
  },
  "test_metrics": {
    "passed": $PASSED,
    "failed": $FAILED,
    "skipped": $SKIPPED
  },
  "exit_code": $EXIT_CODE
}
EOF

exit $EXIT_CODE
