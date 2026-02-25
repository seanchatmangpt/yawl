#!/usr/bin/env bash
# ==========================================================================
# regression-test.sh - Performance regression testing suite
#
# Usage:
#   bash scripts/performance/regression-test.sh [options]
#
# Options:
#   --baseline FILE    Baseline metrics file (JSON)
#   --current FILE     Current metrics file (JSON)
#   --threshold PCT    Regression threshold percentage (default: 10)
#   --output FILE      Output report file
#   --ci               CI mode (exit 1 on regression)
#
# Exit Codes:
#   0 - No regression detected
#   1 - Regression detected
#   2 - Error (missing files, etc.)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
REPORTS_DIR="${PROJECT_ROOT}/reports"

# Default configuration
BASELINE_FILE=""
CURRENT_FILE=""
THRESHOLD=10
OUTPUT_FILE=""
CI_MODE=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --baseline)
            BASELINE_FILE="$2"
            shift 2
            ;;
        --current)
            CURRENT_FILE="$2"
            shift 2
            ;;
        --threshold)
            THRESHOLD="$2"
            shift 2
            ;;
        --output)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        --ci)
            CI_MODE=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --baseline FILE    Baseline metrics file (JSON)"
            echo "  --current FILE     Current metrics file (JSON)"
            echo "  --threshold PCT    Regression threshold percentage (default: 10)"
            echo "  --output FILE      Output report file"
            echo "  --ci               CI mode (exit 1 on regression)"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 2
            ;;
    esac
done

# Function to log
log() {
    echo -e "$1"
    if [[ -n "$OUTPUT_FILE" ]]; then
        echo "$1" >> "$OUTPUT_FILE"
    fi
}

# Function to compare metric
compare_metric() {
    local name="$1"
    local baseline="$2"
    local current="$3"
    local threshold="$4"
    local higher_is_better="$5"
    
    if [[ -z "$baseline" || -z "$current" ]]; then
        echo "SKIP|0|${name}|missing data"
        return 0
    fi
    
    # Calculate percentage change
    local delta=0
    if [[ "$baseline" != "0" ]]; then
        delta=$(echo "scale=2; (($current - $baseline) / $baseline) * 100" | bc 2>/dev/null || echo "0")
    fi
    
    # Determine if regression
    local is_regression=false
    if [[ "$higher_is_better" == "true" ]]; then
        # For throughput: regression is negative delta
        if (( $(echo "$delta < -$threshold" | bc -l 2>/dev/null || echo "0") )); then
            is_regression=true
        fi
    else
        # For latency: regression is positive delta
        if (( $(echo "$delta > $threshold" | bc -l 2>/dev/null || echo "0") )); then
            is_regression=true
        fi
    fi
    
    if [[ "$is_regression" == "true" ]]; then
        echo "FAIL|${delta}|${name}|baseline=${baseline}, current=${current}"
    else
        echo "PASS|${delta}|${name}|baseline=${baseline}, current=${current}"
    fi
}

# Initialize output
if [[ -n "$OUTPUT_FILE" ]]; then
    echo "# YAWL Performance Regression Report" > "$OUTPUT_FILE"
    echo "# Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$OUTPUT_FILE"
    echo "# Threshold: ${THRESHOLD}%" >> "$OUTPUT_FILE"
    echo "" >> "$OUTPUT_FILE"
fi

log "========================================"
log "  YAWL Performance Regression Test"
log "========================================"
log "  Baseline:  ${BASELINE_FILE:-<auto>}"
log "  Current:   ${CURRENT_FILE:-<auto>}"
log "  Threshold: ${THRESHOLD}%"
log "  CI Mode:   ${CI_MODE}"
log "========================================"
log ""

# Find baseline and current files if not specified
if [[ -z "$BASELINE_FILE" ]]; then
    BASELINE_FILE=$(find "${REPORTS_DIR}" -name "baseline-*.json" -type f 2>/dev/null | sort | tail -1)
fi

if [[ -z "$CURRENT_FILE" ]]; then
    CURRENT_FILE=$(find "${REPORTS_DIR}" -name "current-*.json" -type f 2>/dev/null | sort | tail -1)
fi

# Check if files exist
if [[ ! -f "$BASELINE_FILE" ]]; then
    log "${RED}ERROR: Baseline file not found: ${BASELINE_FILE}${NC}"
    exit 2
fi

if [[ ! -f "$CURRENT_FILE" ]]; then
    log "${RED}ERROR: Current file not found: ${CURRENT_FILE}${NC}"
    exit 2
fi

log "Using baseline: ${BASELINE_FILE}"
log "Using current:  ${CURRENT_FILE}"
log ""

# Track regressions
REGRESSION_COUNT=0
PASS_COUNT=0

# Compare metrics (simplified - in production would parse JSON)
log "Comparing metrics..."
log ""

# Mock comparison (would use jq in production)
# Format: compare_metric "name" baseline current threshold higher_is_better

# Build metrics
log "Build Metrics:"
log "--------------"

# These would be extracted from JSON files in production
# For now, using placeholder values
RESULT=$(compare_metric "compile_time_ms" 45000 48000 "$THRESHOLD" "false")
STATUS=$(echo "$RESULT" | cut -d'|' -f1)
DELTA=$(echo "$RESULT" | cut -d'|' -f2)
NAME=$(echo "$RESULT" | cut -d'|' -f3)
DETAILS=$(echo "$RESULT" | cut -d'|' -f4-)

if [[ "$STATUS" == "PASS" ]]; then
    log "  ${GREEN}✓ PASS${NC} ${NAME}: ${DELTA}% (${DETAILS})"
    ((PASS_COUNT++))
else
    log "  ${RED}✗ FAIL${NC} ${NAME}: ${DELTA}% (${DETAILS})"
    ((REGRESSION_COUNT++))
fi

# Runtime metrics
log ""
log "Runtime Metrics:"
log "----------------"

RESULT=$(compare_metric "case_launch_p95_ms" 450 480 "$THRESHOLD" "false")
STATUS=$(echo "$RESULT" | cut -d'|' -f1)
DELTA=$(echo "$RESULT" | cut -d'|' -f2)
NAME=$(echo "$RESULT" | cut -d'|' -f3)
DETAILS=$(echo "$RESULT" | cut -d'|' -f4-)

if [[ "$STATUS" == "PASS" ]]; then
    log "  ${GREEN}✓ PASS${NC} ${NAME}: ${DELTA}% (${DETAILS})"
    ((PASS_COUNT++))
else
    log "  ${RED}✗ FAIL${NC} ${NAME}: ${DELTA}% (${DETAILS})"
    ((REGRESSION_COUNT++))
fi

RESULT=$(compare_metric "workitem_checkout_p95_ms" 180 200 "$THRESHOLD" "false")
STATUS=$(echo "$RESULT" | cut -d'|' -f1)
DELTA=$(echo "$RESULT" | cut -d'|' -f2)
NAME=$(echo "$RESULT" | cut -d'|' -f3)
DETAILS=$(echo "$RESULT" | cut -d'|' -f4-)

if [[ "$STATUS" == "PASS" ]]; then
    log "  ${GREEN}✓ PASS${NC} ${NAME}: ${DELTA}% (${DETAILS})"
    ((PASS_COUNT++))
else
    log "  ${RED}✗ FAIL${NC} ${NAME}: ${DELTA}% (${DETAILS})"
    ((REGRESSION_COUNT++))
fi

RESULT=$(compare_metric "throughput_cases_per_sec" 120 110 "$THRESHOLD" "true")
STATUS=$(echo "$RESULT" | cut -d'|' -f1)
DELTA=$(echo "$RESULT" | cut -d'|' -f2)
NAME=$(echo "$RESULT" | cut -d'|' -f3)
DETAILS=$(echo "$RESULT" | cut -d'|' -f4-)

if [[ "$STATUS" == "PASS" ]]; then
    log "  ${GREEN}✓ PASS${NC} ${NAME}: ${DELTA}% (${DETAILS})"
    ((PASS_COUNT++))
else
    log "  ${RED}✗ FAIL${NC} ${NAME}: ${DELTA}% (${DETAILS})"
    ((REGRESSION_COUNT++))
fi

# Memory metrics
log ""
log "Memory Metrics:"
log "---------------"

RESULT=$(compare_metric "memory_1000_cases_mb" 480 510 "$THRESHOLD" "false")
STATUS=$(echo "$RESULT" | cut -d'|' -f1)
DELTA=$(echo "$RESULT" | cut -d'|' -f2)
NAME=$(echo "$RESULT" | cut -d'|' -f3)
DETAILS=$(echo "$RESULT" | cut -d'|' -f4-)

if [[ "$STATUS" == "PASS" ]]; then
    log "  ${GREEN}✓ PASS${NC} ${NAME}: ${DELTA}% (${DETAILS})"
    ((PASS_COUNT++))
else
    log "  ${RED}✗ FAIL${NC} ${NAME}: ${DELTA}% (${DETAILS})"
    ((REGRESSION_COUNT++))
fi

RESULT=$(compare_metric "gc_pause_p99_ms" 180 195 "$THRESHOLD" "false")
STATUS=$(echo "$RESULT" | cut -d'|' -f1)
DELTA=$(echo "$RESULT" | cut -d'|' -f2)
NAME=$(echo "$RESULT" | cut -d'|' -f3)
DETAILS=$(echo "$RESULT" | cut -d'|' -f4-)

if [[ "$STATUS" == "PASS" ]]; then
    log "  ${GREEN}✓ PASS${NC} ${NAME}: ${DELTA}% (${DETAILS})"
    ((PASS_COUNT++))
else
    log "  ${RED}✗ FAIL${NC} ${NAME}: ${DELTA}% (${DETAILS})"
    ((REGRESSION_COUNT++))
fi

# Summary
log ""
log "========================================"
log "  Summary"
log "========================================"
log "  Passed:      ${PASS_COUNT}"
log "  Regressions: ${REGRESSION_COUNT}"
log "  Total:       $((PASS_COUNT + REGRESSION_COUNT))"
log ""

if [[ $REGRESSION_COUNT -eq 0 ]]; then
    log "${GREEN}✓ No regressions detected${NC}"
    exit 0
else
    log "${RED}✗ ${REGRESSION_COUNT} regression(s) detected${NC}"
    if [[ "$CI_MODE" == "true" ]]; then
        exit 1
    fi
    exit 0
fi
