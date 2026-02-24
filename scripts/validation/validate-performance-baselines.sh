#!/usr/bin/env bash
# ==========================================================================
# validate-performance-baselines.sh - Validates performance against baselines
#
# Usage:
#   bash scripts/validation/validate-performance-baselines.sh
#
# Validates:
#   1. Build time against baseline
#   2. Observatory runtime against baseline
#   3. Detects regressions (>10% threshold)
#
# Exit codes:
#   0 - Performance within acceptable range
#   1 - Performance regression detected
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PERF_DIR="${PROJECT_ROOT}/docs/v6/latest/performance"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ERRORS=0
WARNINGS=0

# Regression threshold (percentage)
REGRESSION_THRESHOLD=10

echo ""
echo "========================================="
echo "  Performance Baseline Validation"
echo "  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "========================================="
echo ""

# Ensure performance directory exists
mkdir -p "${PERF_DIR}"

# -------------------------------------------------------------------------
# Helper function to measure time in milliseconds
# -------------------------------------------------------------------------
get_epoch_ms() {
    date +%s%3N
}

# -------------------------------------------------------------------------
# 1. Build Time Validation
# -------------------------------------------------------------------------
echo "[1/3] Validating build time..."

BUILD_BASELINE="${PERF_DIR}/build-baseline.json"

# Check if baseline exists
if [[ -f "$BUILD_BASELINE" ]]; then
    BASELINE_TIME=$(jq -r '.metrics.clean_compile_ms // 0' "$BUILD_BASELINE" 2>/dev/null)

    if [[ "$BASELINE_TIME" -gt 0 ]]; then
        echo "  Baseline: ${BASELINE_TIME}ms"

        # Measure current build time
        echo "  Measuring current build time..."
        START=$(get_epoch_ms)

        # Run compile (suppress output)
        if mvn -T 1.5C clean compile -q 2>/dev/null; then
            END=$(get_epoch_ms)
            CURRENT_TIME=$((END - START))

            echo "  Current:  ${CURRENT_TIME}ms"

            # Calculate regression percentage
            if [[ "$BASELINE_TIME" -gt 0 ]]; then
                REGRESSION_PCT=$(((CURRENT_TIME - BASELINE_TIME) * 100 / BASELINE_TIME))

                if [[ $REGRESSION_PCT -gt $REGRESSION_THRESHOLD ]]; then
                    echo -e "  ${RED}ERROR: Build time regression (+${REGRESSION_PCT}%)${NC}"
                    echo "  Threshold: +${REGRESSION_THRESHOLD}%"
                    ERRORS=$((ERRORS + 1))
                elif [[ $REGRESSION_PCT -gt 0 ]]; then
                    echo -e "  ${YELLOW}WARNING: Build time slower (+${REGRESSION_PCT}%)${NC}"
                    WARNINGS=$((WARNINGS + 1))
                else
                    echo -e "  ${GREEN}PASSED: Build time within range (${REGRESSION_PCT}%)${NC}"
                fi
            fi
        else
            echo -e "  ${RED}ERROR: Build failed${NC}"
            ERRORS=$((ERRORS + 1))
        fi
    else
        echo -e "  ${YELLOW}SKIPPED: No baseline time in build-baseline.json${NC}"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo -e "  ${YELLOW}SKIPPED: No build baseline found${NC}"
    echo "  Run: bash scripts/performance/measure-baseline.sh"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# 2. Observatory Runtime Validation
# -------------------------------------------------------------------------
echo "[2/3] Validating observatory runtime..."

OBS_BASELINE="${PERF_DIR}/observatory-baseline.json"
OBS_SCRIPT="${PROJECT_ROOT}/scripts/observatory/observatory.sh"

if [[ -f "$OBS_BASELINE" && -f "$OBS_SCRIPT" ]]; then
    BASELINE_TIME=$(jq -r '.metrics.total_runtime_ms // 0' "$OBS_BASELINE" 2>/dev/null)

    if [[ "$BASELINE_TIME" -gt 0 ]]; then
        echo "  Baseline: ${BASELINE_TIME}ms"

        # Measure current observatory time
        echo "  Measuring current observatory time..."
        START=$(get_epoch_ms)

        # Run observatory (suppress output)
        if bash "$OBS_SCRIPT" > /dev/null 2>&1; then
            END=$(get_epoch_ms)
            CURRENT_TIME=$((END - START))

            echo "  Current:  ${CURRENT_TIME}ms"

            # Calculate regression percentage
            if [[ "$BASELINE_TIME" -gt 0 ]]; then
                REGRESSION_PCT=$(((CURRENT_TIME - BASELINE_TIME) * 100 / BASELINE_TIME))

                if [[ $REGRESSION_PCT -gt $REGRESSION_THRESHOLD ]]; then
                    echo -e "  ${RED}ERROR: Observatory time regression (+${REGRESSION_PCT}%)${NC}"
                    ERRORS=$((ERRORS + 1))
                elif [[ $REGRESSION_PCT -gt 0 ]]; then
                    echo -e "  ${YELLOW}WARNING: Observatory slower (+${REGRESSION_PCT}%)${NC}"
                    WARNINGS=$((WARNINGS + 1))
                else
                    echo -e "  ${GREEN}PASSED: Observatory time within range (${REGRESSION_PCT}%)${NC}"
                fi
            fi
        else
            echo -e "  ${RED}ERROR: Observatory failed${NC}"
            ERRORS=$((ERRORS + 1))
        fi
    else
        echo -e "  ${YELLOW}SKIPPED: No baseline time in observatory-baseline.json${NC}"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo -e "  ${YELLOW}SKIPPED: No observatory baseline found${NC}"
    echo "  Run: bash scripts/performance/measure-baseline.sh"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# 3. Memory Usage Check
# -------------------------------------------------------------------------
echo "[3/3] Checking memory usage..."

# Get current memory info
if [[ "$(uname)" == "Darwin" ]]; then
    # macOS
    TOTAL_MEM=$(sysctl -n hw.memsize 2>/dev/null || echo "0")
    FREE_MEM=$(vm_stat | grep "free" | awk '{print $3}' | sed 's/\.//' 2>/dev/null || echo "0")
    FREE_MEM=$((FREE_MEM * 4096))  # Convert pages to bytes
else
    # Linux
    TOTAL_MEM=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2*1024}' || echo "0")
    FREE_MEM=$(grep MemAvailable /proc/meminfo 2>/dev/null | awk '{print $2*1024}' || echo "0")
fi

if [[ "$TOTAL_MEM" -gt 0 ]]; then
    USED_PCT=$((100 - (FREE_MEM * 100 / TOTAL_MEM)))
    TOTAL_GB=$((TOTAL_MEM / 1024 / 1024 / 1024))
    FREE_GB=$((FREE_MEM / 1024 / 1024 / 1024))

    echo "  Total memory: ${TOTAL_GB}GB"
    echo "  Free memory:  ${FREE_GB}GB"
    echo "  Used:         ${USED_PCT}%"

    if [[ $USED_PCT -gt 90 ]]; then
        echo -e "  ${RED}ERROR: Memory usage critical (>90%)${NC}"
        ERRORS=$((ERRORS + 1))
    elif [[ $USED_PCT -gt 80 ]]; then
        echo -e "  ${YELLOW}WARNING: Memory usage high (>80%)${NC}"
        WARNINGS=$((WARNINGS + 1))
    else
        echo -e "  ${GREEN}PASSED: Memory usage normal${NC}"
    fi
else
    echo -e "  ${YELLOW}SKIPPED: Could not determine memory info${NC}"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# Summary
# -------------------------------------------------------------------------
echo "========================================="
echo "  Performance Validation Summary"
echo "========================================="
echo "  Errors:   ${ERRORS}"
echo "  Warnings: ${WARNINGS}"
echo "  Regression Threshold: ${REGRESSION_THRESHOLD}%"
echo "========================================="
echo ""

if [[ $ERRORS -gt 0 ]]; then
    echo -e "${RED}PERFORMANCE REGRESSION DETECTED${NC}"
    echo ""
    echo "To update baselines after intentional changes:"
    echo "  bash scripts/performance/measure-baseline.sh"
    exit 1
else
    echo -e "${GREEN}PERFORMANCE WITHIN RANGE${NC}"
    exit 0
fi
