#!/usr/bin/env bash
# ==========================================================================
# measure-baseline.sh - Measures current performance and updates baselines
#
# Usage:
#   bash scripts/performance/measure-baseline.sh
#
# Measures:
#   1. Build time (clean compile)
#   2. Observatory runtime
#   3. Test execution time
#
# Output: docs/v6/latest/performance/*.json
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PERF_DIR="${PROJECT_ROOT}/docs/v6/latest/performance"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Ensure performance directory exists
mkdir -p "${PERF_DIR}"

echo ""
echo "========================================="
echo "  Performance Baseline Measurement"
echo "  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "========================================="
echo ""

# -------------------------------------------------------------------------
# Helper function to measure time in milliseconds
# -------------------------------------------------------------------------
get_epoch_ms() {
    if command -v python3 &> /dev/null; then
        python3 -c 'import time; print(int(time.time() * 1000))'
    else
        echo $(($(date +%s) * 1000))
    fi
}

# -------------------------------------------------------------------------
# 1. Build Time Measurement
# -------------------------------------------------------------------------
echo "[1/3] Measuring build time..."

START=$(get_epoch_ms)
mvn -T 1.5C clean compile -q
END=$(get_epoch_ms)
BUILD_TIME=$((END - START))

# Get environment info
if command -v nproc &> /dev/null; then
    CPU_CORES=$(nproc)
elif command -v sysctl &> /dev/null; then
    CPU_CORES=$(sysctl -n hw.ncpu 2>/dev/null || echo "unknown")
else
    CPU_CORES="unknown"
fi

if [[ "$(uname)" == "Darwin" ]]; then
    MEM_GB=$(( $(sysctl -n hw.memsize 2>/dev/null || echo "0") / 1024 / 1024 / 1024 ))
else
    MEM_GB=$(( $(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}' || echo "0") / 1024 / 1024 ))
fi

cat > "${PERF_DIR}/build-baseline.json" << EOF
{
  "generated_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "java_version": "25",
  "maven_version": "$(mvn -version 2>/dev/null | head -1 | awk '{print $3}' || echo "unknown")",
  "metrics": {
    "clean_compile_ms": ${BUILD_TIME},
    "parallel_factor": "1.5C"
  },
  "environment": {
    "cpu_cores": ${CPU_CORES},
    "memory_gb": ${MEM_GB:-0},
    "os": "$(uname -s)"
  },
  "trends": {
    "last_7_days": [],
    "regression_threshold_pct": 10
  }
}
EOF

echo -e "  ${GREEN}Build time: ${BUILD_TIME}ms${NC}"
echo "  Saved to: ${PERF_DIR}/build-baseline.json"
echo ""

# -------------------------------------------------------------------------
# 2. Observatory Runtime Measurement
# -------------------------------------------------------------------------
echo "[2/3] Measuring observatory runtime..."

OBS_SCRIPT="${PROJECT_ROOT}/scripts/observatory/observatory.sh"

if [[ -f "$OBS_SCRIPT" ]]; then
    START=$(get_epoch_ms)
    bash "$OBS_SCRIPT" > /dev/null 2>&1
    END=$(get_epoch_ms)
    OBS_TIME=$((END - START))

    # Extract metrics from receipt
    RECEIPT="${PROJECT_ROOT}/docs/v6/latest/receipts/observatory.json"
    if [[ -f "$RECEIPT" ]]; then
        RECEIPT_TIME=$(jq -r '.timing_ms.total // 0' "$RECEIPT" 2>/dev/null || echo "0")
        FACTS_TIME=$(jq -r '.timing_ms.facts // 0' "$RECEIPT" 2>/dev/null || echo "0")
        DIAGRAMS_TIME=$(jq -r '.timing_ms.diagrams // 0' "$RECEIPT" 2>/dev/null || echo "0")
        FACTS_COUNT=$(jq -r '.facts_emitted | length' "$RECEIPT" 2>/dev/null || echo "0")
        DIAGRAMS_COUNT=$(jq -r '.diagrams_emitted | length' "$RECEIPT" 2>/dev/null || echo "0")
    else
        RECEIPT_TIME=0
        FACTS_TIME=0
        DIAGRAMS_TIME=0
        FACTS_COUNT=0
        DIAGRAMS_COUNT=0
    fi

    cat > "${PERF_DIR}/observatory-baseline.json" << EOF
{
  "generated_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "metrics": {
    "total_runtime_ms": ${OBS_TIME},
    "receipt_reported_ms": ${RECEIPT_TIME},
    "facts_phase_ms": ${FACTS_TIME},
    "diagrams_phase_ms": ${DIAGRAMS_TIME}
  },
  "outputs": {
    "facts_count": ${FACTS_COUNT},
    "diagrams_count": ${DIAGRAMS_COUNT}
  }
}
EOF

    echo -e "  ${GREEN}Observatory time: ${OBS_TIME}ms${NC}"
    echo "  Saved to: ${PERF_DIR}/observatory-baseline.json"
else
    echo -e "  ${YELLOW}SKIPPED: Observatory script not found${NC}"
fi

echo ""

# -------------------------------------------------------------------------
# 3. Test Coverage Baseline (if JaCoCo available)
# -------------------------------------------------------------------------
echo "[3/3] Measuring test coverage baseline..."

# Check if JaCoCo is configured
if grep -q "jacoco-maven-plugin" "${PROJECT_ROOT}/pom.xml" 2>/dev/null; then
    echo "  Running tests with coverage..."

    if mvn -T 1.5C test -q 2>/dev/null; then
        # Look for JaCoCo report
        JACOCO_XML="${PROJECT_ROOT}/target/site/jacoco/jacoco.xml"

        if [[ -f "$JACOCO_XML" ]]; then
            LINE_COV=$(xmllint --xpath "string(//counter[@type='LINE']/@covered)" "$JACOCO_XML" 2>/dev/null || echo "0")
            LINE_TOTAL=$(xmllint --xpath "string(//counter[@type='LINE']/@missed)" "$JACOCO_XML" 2>/dev/null || echo "0")
            LINE_TOTAL=$((LINE_TOTAL + LINE_COV))

            BRANCH_COV=$(xmllint --xpath "string(//counter[@type='BRANCH']/@covered)" "$JACOCO_XML" 2>/dev/null || echo "0")
            BRANCH_TOTAL=$(xmllint --xpath "string(//counter[@type='BRANCH']/@missed)" "$JACOCO_XML" 2>/dev/null || echo "0")
            BRANCH_TOTAL=$((BRANCH_TOTAL + BRANCH_COV))

            if [[ $LINE_TOTAL -gt 0 ]]; then
                LINE_PCT=$((LINE_COV * 100 / LINE_TOTAL))
            else
                LINE_PCT=0
            fi

            if [[ $BRANCH_TOTAL -gt 0 ]]; then
                BRANCH_PCT=$((BRANCH_COV * 100 / BRANCH_TOTAL))
            else
                BRANCH_PCT=0
            fi

            cat > "${PERF_DIR}/test-coverage-baseline.json" << EOF
{
  "generated_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "metrics": {
    "line_coverage_pct": ${LINE_PCT},
    "branch_coverage_pct": ${BRANCH_PCT},
    "line_covered": ${LINE_COV},
    "line_total": ${LINE_TOTAL},
    "branch_covered": ${BRANCH_COV},
    "branch_total": ${BRANCH_TOTAL}
  }
}
EOF

            echo -e "  ${GREEN}Line coverage: ${LINE_PCT}%${NC}"
            echo -e "  ${GREEN}Branch coverage: ${BRANCH_PCT}%${NC}"
            echo "  Saved to: ${PERF_DIR}/test-coverage-baseline.json"
        else
            echo -e "  ${YELLOW}JaCoCo report not found at ${JACOCO_XML}${NC}"
        fi
    else
        echo -e "  ${YELLOW}Tests failed, skipping coverage measurement${NC}"
    fi
else
    echo -e "  ${YELLOW}SKIPPED: JaCoCo not configured in pom.xml${NC}"
fi

echo ""

# -------------------------------------------------------------------------
# Summary
# -------------------------------------------------------------------------
echo "========================================="
echo "  Baseline Measurement Complete"
echo "========================================="
echo ""
echo "Generated baselines:"
ls -la "${PERF_DIR}"/*.json 2>/dev/null || echo "  No baselines generated"
echo ""
echo "To validate against these baselines:"
echo "  bash scripts/validation/validate-performance-baselines.sh"
echo ""
