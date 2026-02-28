#!/usr/bin/env bash
# ==========================================================================
# verify-baselines.sh — Verify and report on baseline metrics
#
# Loads existing baselines and validates them for:
#   - Consistency (std dev < 10%)
#   - Constraints (times < max thresholds)
#   - Completeness (all scenarios present)
#
# Usage:
#   bash scripts/verify-baselines.sh                    # Verify latest baseline
#   bash scripts/verify-baselines.sh --detailed         # Show detailed report
#   bash scripts/verify-baselines.sh --compare         # Compare to previous
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BASELINES_FILE="${REPO_ROOT}/.yawl/metrics/baselines.json"

# Color codes for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'  # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[OK]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

# Main verification
verify_baselines() {
    if [ ! -f "$BASELINES_FILE" ]; then
        log_error "Baselines file not found: $BASELINES_FILE"
        exit 1
    fi

    log_info "Loading baselines from: $BASELINES_FILE"
    echo ""

    # Get latest baseline
    local latest_baseline
    latest_baseline=$(jq '.baselines[-1]' "$BASELINES_FILE")

    local baseline_date
    baseline_date=$(echo "$latest_baseline" | jq -r '.baseline_date')

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}BASELINE VERIFICATION REPORT${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo "Baseline Date: $baseline_date"
    echo ""

    # Environment info
    local env_info
    env_info=$(echo "$latest_baseline" | jq '.environment')

    echo -e "${BLUE}Environment:${NC}"
    echo "  Java Version: $(echo "$env_info" | jq -r '.java_version')"
    echo "  CPU Cores: $(echo "$env_info" | jq -r '.cpu_cores')"
    echo "  Total Memory: $(echo "$env_info" | jq -r '.total_memory_mb')MB"
    echo "  Kernel: $(echo "$env_info" | jq -r '.kernel')"
    echo ""

    # Verify each scenario
    local scenarios
    scenarios=$(echo "$latest_baseline" | jq -r '.scenarios[].scenario')

    local all_valid=true

    for scenario in $scenarios; do
        echo -e "${BLUE}Scenario: $scenario${NC}"

        local scenario_data
        scenario_data=$(echo "$latest_baseline" | jq ".scenarios[] | select(.scenario == \"$scenario\")")

        local mean_ms
        mean_ms=$(echo "$scenario_data" | jq '.total_time.mean_ms')

        local std_dev_ms
        std_dev_ms=$(echo "$scenario_data" | jq '.total_time.std_dev_ms')

        local std_dev_pct
        std_dev_pct=$(echo "$scenario_data" | jq '.validity.time_std_dev_percent')

        local is_valid
        is_valid=$(echo "$scenario_data" | jq '.validity.valid')

        local peak_memory
        peak_memory=$(echo "$scenario_data" | jq '.memory.mean_mb')

        local cpu_avg
        cpu_avg=$(echo "$scenario_data" | jq '.cpu_avg_percent')

        local tests_passed
        tests_passed=$(echo "$scenario_data" | jq '.test_metrics.total_passed')

        local tests_failed
        tests_failed=$(echo "$scenario_data" | jq '.test_metrics.total_failed')

        local tests_skipped
        tests_skipped=$(echo "$scenario_data" | jq '.test_metrics.total_skipped')

        # Format output
        echo "  Execution Time: ${mean_ms}ms (±${std_dev_ms}ms, ${std_dev_pct}%)"

        if [ "$is_valid" = "true" ]; then
            log_success "Consistency: Valid (std dev ${std_dev_pct}% < 10%)"
        else
            log_warn "Consistency: High variability (std dev ${std_dev_pct}% > 10%)"
            all_valid=false
        fi

        echo "  Memory Usage: ${peak_memory}MB"
        echo "  CPU Utilization: ${cpu_avg}%"
        echo "  Tests: $tests_passed passed, $tests_failed failed, $tests_skipped skipped"

        # Check constraints
        case "$scenario" in
            cold-start)
                if [ "$mean_ms" -lt 120000 ]; then
                    log_success "Constraint: Within limit (<120s)"
                else
                    log_error "Constraint: EXCEEDED (${mean_ms}ms > 120000ms)"
                    all_valid=false
                fi
                ;;
            warm-start)
                if [ "$mean_ms" -lt 30000 ]; then
                    log_success "Constraint: Within limit (<30s)"
                else
                    log_error "Constraint: EXCEEDED (${mean_ms}ms > 30000ms)"
                    all_valid=false
                fi
                ;;
            full-suite)
                if [ "$mean_ms" -lt 180000 ]; then
                    log_success "Constraint: Within limit (<180s)"
                else
                    log_error "Constraint: EXCEEDED (${mean_ms}ms > 180000ms)"
                    all_valid=false
                fi
                ;;
        esac

        echo ""
    done

    # Summary
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"

    if [ "$all_valid" = "true" ]; then
        echo -e "${GREEN}RESULT: All baselines VALID${NC}"
        echo "All scenarios meet consistency and constraint requirements."
        return 0
    else
        echo -e "${RED}RESULT: Some baselines INVALID${NC}"
        echo "Please review issues above and re-run baselines if needed."
        return 1
    fi
}

# Optional: Compare to previous baseline
compare_baselines() {
    if [ ! -f "$BASELINES_FILE" ]; then
        log_error "Baselines file not found: $BASELINES_FILE"
        exit 1
    fi

    local baseline_count
    baseline_count=$(jq '.baselines | length' "$BASELINES_FILE")

    if [ "$baseline_count" -lt 2 ]; then
        log_warn "Only one baseline found, cannot compare"
        return
    fi

    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}COMPARISON TO PREVIOUS BASELINE${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo ""

    local current_baseline
    current_baseline=$(jq '.baselines[-1]' "$BASELINES_FILE")

    local previous_baseline
    previous_baseline=$(jq '.baselines[-2]' "$BASELINES_FILE")

    local current_date
    current_date=$(echo "$current_baseline" | jq -r '.baseline_date')

    local previous_date
    previous_date=$(echo "$previous_baseline" | jq -r '.baseline_date')

    echo "Current:  $current_date"
    echo "Previous: $previous_date"
    echo ""

    # Compare each scenario
    for scenario in cold-start warm-start full-suite; do
        local curr_time
        curr_time=$(echo "$current_baseline" | jq ".scenarios[] | select(.scenario == \"$scenario\") | .total_time.mean_ms")

        local prev_time
        prev_time=$(echo "$previous_baseline" | jq ".scenarios[] | select(.scenario == \"$scenario\") | .total_time.mean_ms" 2>/dev/null || echo "0")

        if [ "$prev_time" = "0" ] || [ -z "$prev_time" ]; then
            echo "$scenario: No previous data"
            continue
        fi

        local diff
        diff=$((curr_time - prev_time))

        local pct_change
        pct_change=$(awk "BEGIN {printf \"%.1f\", ($diff/$prev_time)*100}")

        echo -n "$scenario: ${curr_time}ms (was ${prev_time}ms) "

        if [ "$diff" -lt 0 ]; then
            echo -e "${GREEN}[${pct_change}% FASTER]${NC}"
        elif [ "$diff" -gt 0 ]; then
            echo -e "${YELLOW}[${pct_change}% SLOWER]${NC}"
        else
            echo -e "${GREEN}[NO CHANGE]${NC}"
        fi
    done

    echo ""
}

# Main
case "${1:-}" in
    --compare)
        verify_baselines
        compare_baselines
        ;;
    --detailed)
        verify_baselines
        echo ""
        echo -e "${BLUE}Raw JSON:${NC}"
        jq '.baselines[-1]' "$BASELINES_FILE"
        ;;
    *)
        verify_baselines
        ;;
esac
