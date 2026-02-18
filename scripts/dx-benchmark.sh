#!/usr/bin/env bash
# ==========================================================================
# dx-benchmark.sh — Build Performance Benchmarking with Trend Tracking
#
# Measures compile/test times with millisecond precision, outputs JSON
# to docs/v6/latest/receipts/benchmark-<timestamp>.json for trend analysis.
#
# Usage:
#   bash scripts/dx-benchmark.sh compile     # Benchmark compile phase
#   bash scripts/dx-benchmark.sh test        # Benchmark test phase
#   bash scripts/dx-benchmark.sh all         # Benchmark full build
#   bash scripts/dx-benchmark.sh trend       # Show last 5 runs
#
# Output:
#   docs/v6/latest/receipts/benchmark-<timestamp>.json
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RECEIPTS_DIR="${REPO_ROOT}/docs/v6/latest/receipts"
cd "${REPO_ROOT}"

# ── Parse arguments ───────────────────────────────────────────────────────
PHASE="${1:-all}"
case "$PHASE" in
    compile|test|all|trend) ;;
    -h|--help)
        sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
        exit 0 ;;
    *)  echo "Unknown phase: $PHASE. Use compile|test|all|trend"; exit 1 ;;
esac

# ── Trend command ─────────────────────────────────────────────────────────
if [[ "$PHASE" == "trend" ]]; then
    echo "=== Build Performance Trend (Last 5 Runs) ==="
    echo ""

    LATEST_FILES=$(ls -t "${RECEIPTS_DIR}"/benchmark-*.json 2>/dev/null | head -5)
    if [[ -z "$LATEST_FILES" ]]; then
        echo "No benchmark data found. Run: bash scripts/dx-benchmark.sh all"
        exit 0
    fi

    printf "%-20s %10s %10s %10s %12s\n" "TIMESTAMP" "COMPILE" "TEST" "TOTAL" "STATUS"
    printf "%-20s %10s %10s %10s %12s\n" "----------" "------" "----" "-----" "------"

    for file in $LATEST_FILES; do
        ts=$(basename "$file" | sed 's/benchmark-\([0-9]*\).json/\1/')
        formatted_ts=$(echo "$ts" | sed 's/\(....\)\(..\)\(..\)\(..\)\(..\)/\1-\2-\3 \4:\5/')

        compile_ms=$(python3 -c "import json; d=json.load(open('$file')); print(d.get('compile_ms', 0))" 2>/dev/null || echo "0")
        test_ms=$(python3 -c "import json; d=json.load(open('$file')); print(d.get('test_ms', 0))" 2>/dev/null || echo "0")
        total_ms=$(python3 -c "import json; d=json.load(open('$file')); print(d.get('total_ms', 0))" 2>/dev/null || echo "0")
        status=$(python3 -c "import json; d=json.load(open('$file')); print(d.get('status', 'unknown'))" 2>/dev/null || echo "unknown")

        compile_s=$(python3 -c "print(f'{$compile_ms/1000:.1f}s')")
        test_s=$(python3 -c "print(f'{$test_ms/1000:.1f}s')")
        total_s=$(python3 -c "print(f'{$total_ms/1000:.1f}s')")

        printf "%-20s %10s %10s %10s %12s\n" "$formatted_ts" "$compile_s" "$test_s" "$total_s" "$status"
    done
    echo ""
    echo "Data files: ${RECEIPTS_DIR}/benchmark-*.json"
    exit 0
fi

# ── Ensure output directory ───────────────────────────────────────────────
mkdir -p "${RECEIPTS_DIR}"

# ── Benchmarking helpers ──────────────────────────────────────────────────
get_timestamp() {
    date +%Y%m%d%H%M%S
}

elapsed_seconds() {
    local start_ms="$1"
    local end_ms="$2"
    python3 -c "print(f'{(${end_ms} - ${start_ms}) / 1000:.3f}')"
}

# ── Run benchmark ─────────────────────────────────────────────────────────
TIMESTAMP=$(get_timestamp)
OUTPUT_FILE="${RECEIPTS_DIR}/benchmark-${TIMESTAMP}.json"

echo "=== YAWL Build Benchmark ==="
echo "Phase: $PHASE"
echo "Timestamp: $(date -Iseconds)"
echo ""

GLOBAL_START=$(python3 -c "import time; print(int(time.time() * 1000))")
COMPILE_MS=0
TEST_MS=0
STATUS="success"
ERROR_MSG=""

# Run compile if needed
if [[ "$PHASE" == "compile" || "$PHASE" == "all" ]]; then
    echo "Benchmarking compile phase..."
    COMPILE_START=$(python3 -c "import time; print(int(time.time() * 1000))")

    set +e
    COMPILE_OUTPUT=$(mvn -q -P agent-dx compile 2>&1)
    COMPILE_EXIT=$?
    set -e

    COMPILE_END=$(python3 -c "import time; print(int(time.time() * 1000))")
    COMPILE_MS=$((COMPILE_END - COMPILE_START))

    if [[ $COMPILE_EXIT -ne 0 ]]; then
        STATUS="failed"
        ERROR_MSG="Compile failed"
        echo "Compile: FAILED (${COMPILE_MS}ms)"
    else
        echo "Compile: OK ($(elapsed_seconds $COMPILE_START $COMPILE_END)s)"
    fi
fi

# Run test if needed
if [[ "$PHASE" == "test" || "$PHASE" == "all" ]]; then
    echo "Benchmarking test phase..."
    TEST_START=$(python3 -c "import time; print(int(time.time() * 1000))")

    set +e
    TEST_OUTPUT=$(mvn -q -P agent-dx test 2>&1)
    TEST_EXIT=$?
    set -e

    TEST_END=$(python3 -c "import time; print(int(time.time() * 1000))")
    TEST_MS=$((TEST_END - TEST_START))

    if [[ $TEST_EXIT -ne 0 ]]; then
        STATUS="failed"
        ERROR_MSG="Test failed"
        echo "Test: FAILED (${TEST_MS}ms)"
    else
        echo "Test: OK ($(elapsed_seconds $TEST_START $TEST_END)s)"
    fi
fi

GLOBAL_END=$(python3 -c "import time; print(int(time.time() * 1000))")
TOTAL_MS=$((GLOBAL_END - GLOBAL_START))

# ── Write JSON output ─────────────────────────────────────────────────────
cat > "${OUTPUT_FILE}" <<EOF
{
  "timestamp": "$(date -Iseconds)",
  "timestamp_unix": $(date +%s),
  "phase": "${PHASE}",
  "compile_ms": ${COMPILE_MS},
  "test_ms": ${TEST_MS},
  "total_ms": ${TOTAL_MS},
  "status": "${STATUS}",
  "error": "${ERROR_MSG}",
  "branch": "$(git branch --show-current 2>/dev/null || echo 'unknown')",
  "commit": "$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')",
  "java_version": "$(java -version 2>&1 | head -1 | cut -d'"' -f2 || echo 'unknown')",
  "maven_version": "$(mvn -version 2>/dev/null | head -1 | cut -d' ' -f3 || echo 'unknown')"
}
EOF

# ── Summary ───────────────────────────────────────────────────────────────
echo ""
echo "=== Benchmark Complete ==="
echo "Total time: $(elapsed_seconds $GLOBAL_START $GLOBAL_END)s"
echo "Status: ${STATUS}"
echo "Output: ${OUTPUT_FILE}"
echo ""
echo "View trend: bash scripts/dx-benchmark.sh trend"

[[ "$STATUS" == "success" ]] || exit 1
