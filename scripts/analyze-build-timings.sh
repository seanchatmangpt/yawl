#!/usr/bin/env bash
# ==========================================================================
# analyze-build-timings.sh — Build Timing Analysis & Trend Detection
#
# Analyzes build-timings.json for performance regressions and slowest tests.
# Supports trend analysis over multiple builds for continuous monitoring.
#
# Usage:
#   bash scripts/analyze-build-timings.sh               # analyze all timings
#   bash scripts/analyze-build-timings.sh --recent 5    # last 5 builds
#   bash scripts/analyze-build-timings.sh --trend       # show trend analysis
#   bash scripts/analyze-build-timings.sh --percentile  # 50th/95th/99th %ile
#
# Output:
#   - Build performance summary (count, avg time, max time)
#   - Slowest tests (if available from Surefire reports)
#   - Regression detection (>10% deviation from rolling average)
#   - Trend report with sparklines
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TIMINGS_FILE="${REPO_ROOT}/.yawl/timings/build-timings.json"

# Color codes
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_BLUE='\033[94m'
readonly C_YELLOW='\033[93m'
readonly C_CYAN='\033[96m'

# Default options
RECENT_BUILDS=0
SHOW_TREND=false
SHOW_PERCENTILES=false

# ── Parse arguments ───────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --recent)
            RECENT_BUILDS="${2:-5}"
            shift 2
            ;;
        --trend)
            SHOW_TREND=true
            shift
            ;;
        --percentile)
            SHOW_PERCENTILES=true
            shift
            ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0
            ;;
        *)
            echo "Unknown arg: $1. Use -h for help." >&2
            exit 1
            ;;
    esac
done

# ── Check if timings file exists ───────────────────────────────────────
if [[ ! -f "$TIMINGS_FILE" ]]; then
    echo "No timings data found. Run: DX_TIMINGS=1 bash scripts/dx.sh"
    exit 1
fi

# ── Parse timings and compute statistics ───────────────────────────────
declare -a timings_array
declare -a success_array

# Load timings into arrays
line_count=0
while IFS= read -r line; do
    [[ -z "$line" ]] && continue

    # Parse JSON: { "elapsed_sec": 12.5, "success": true, ... }
    if [[ $line =~ \"elapsed_sec\":([0-9.]+) ]]; then
        elapsed="${BASH_REMATCH[1]}"
        timings_array+=("$elapsed")

        if [[ $line =~ \"success\":true ]]; then
            success_array+=(1)
        else
            success_array+=(0)
        fi

        ((++line_count))
    fi
done < "$TIMINGS_FILE"

# ── Limit to recent builds if requested ───────────────────────────────
if [[ $RECENT_BUILDS -gt 0 && ${#timings_array[@]} -gt $RECENT_BUILDS ]]; then
    timings_array=("${timings_array[@]:(-$RECENT_BUILDS)}")
    success_array=("${success_array[@]:(-$RECENT_BUILDS)}")
fi

# ── Compute statistics ───────────────────────────────────────────────────
compute_stats() {
    local -n arr=$1
    [[ ${#arr[@]} -eq 0 ]] && return

    local sum=0
    local min=${arr[0]}
    local max=${arr[0]}

    for val in "${arr[@]}"; do
        sum=$(echo "$sum + $val" | bc -l)
        if (( $(echo "$val < $min" | bc -l) )); then min=$val; fi
        if (( $(echo "$val > $max" | bc -l) )); then max=$val; fi
    done

    local avg=$(echo "scale=2; $sum / ${#arr[@]}" | bc -l)

    printf "%s|%s|%s|%s|%d\n" "$min" "$max" "$avg" "$sum" "${#arr[@]}"
}

STATS=$(compute_stats timings_array)
IFS='|' read -r MIN_TIME MAX_TIME AVG_TIME TOTAL_TIME BUILD_COUNT <<< "$STATS"

# Count successes
SUCCESS_COUNT=0
for status in "${success_array[@]}"; do
    ((SUCCESS_COUNT += status))
done
FAIL_COUNT=$((BUILD_COUNT - SUCCESS_COUNT))

# ── Display summary ───────────────────────────────────────────────────
printf "\n${C_CYAN}Build Timing Summary${C_RESET}\n"
printf "${C_CYAN}=====================${C_RESET}\n\n"

printf "Build Count:    %d (${C_GREEN}%d passed${C_RESET}, ${C_RED}%d failed${C_RESET})\n" \
    "$BUILD_COUNT" "$SUCCESS_COUNT" "$FAIL_COUNT"
printf "Total Time:     %.1f seconds\n" "$TOTAL_TIME"
printf "Min Time:       ${C_GREEN}%.2f${C_RESET} sec\n" "$MIN_TIME"
printf "Max Time:       ${C_YELLOW}%.2f${C_RESET} sec\n" "$MAX_TIME"
printf "Avg Time:       %.2f sec\n" "$AVG_TIME"

# ── Regression detection ───────────────────────────────────────────────
latest_time=${timings_array[-1]}
deviation=$(echo "scale=2; (($latest_time - $AVG_TIME) / $AVG_TIME) * 100" | bc -l)

if (( $(echo "$deviation > 10" | bc -l) )); then
    printf "\n${C_RED}⚠ Performance Regression detected!${C_RESET}\n"
    printf "  Latest: %.2f sec (+%.1f%% vs average)\n" "$latest_time" "$deviation"
elif (( $(echo "$deviation < -10" | bc -l) )); then
    printf "\n${C_GREEN}✓ Performance Improvement detected!${C_RESET}\n"
    printf "  Latest: %.2f sec (%.1f%% vs average)\n" "$latest_time" "${deviation#-}"
fi

# ── Percentile analysis (if requested) ───────────────────────────────
if [[ "$SHOW_PERCENTILES" == "true" ]]; then
    printf "\n${C_CYAN}Percentile Analysis${C_RESET}\n"
    printf "${C_CYAN}====================${C_RESET}\n\n"

    # Sort timings for percentile calculation
    IFS=$'\n' sorted=($(sort -n <(printf '%s\n' "${timings_array[@]}")))

    # 50th percentile (median)
    p50_idx=$(( (BUILD_COUNT - 1) / 2 ))
    p50=${sorted[$p50_idx]}

    # 95th percentile
    p95_idx=$(( (BUILD_COUNT - 1) * 95 / 100 ))
    p95=${sorted[$p95_idx]}

    # 99th percentile
    p99_idx=$(( (BUILD_COUNT - 1) * 99 / 100 ))
    p99=${sorted[$((p99_idx < BUILD_COUNT ? p99_idx : BUILD_COUNT - 1))])}

    printf "P50 (median):   %.2f sec\n" "$p50"
    printf "P95:            %.2f sec\n" "$p95"
    printf "P99:            %.2f sec\n" "$p99"
fi

# ── Trend analysis (if requested) ───────────────────────────────────
if [[ "$SHOW_TREND" == "true" && ${#timings_array[@]} -gt 1 ]]; then
    printf "\n${C_CYAN}Trend Analysis (Last 10 Builds)${C_RESET}\n"
    printf "${C_CYAN}================================${C_RESET}\n\n"

    # Show last up to 10 builds
    local limit=$((${#timings_array[@]} > 10 ? 10 : ${#timings_array[@]}))
    local start=$((${#timings_array[@]} - limit))

    for (( i = start; i < ${#timings_array[@]}; i++ )); do
        local t=${timings_array[$i]}
        local s=${success_array[$i]}
        local status=$([[ $s -eq 1 ]] && echo "✓" || echo "✗")

        # Create simple bar graph (scale to 20 chars)
        local bar_len=$(echo "scale=0; $t * 2" | bc -l)
        ((bar_len = bar_len > 20 ? 20 : bar_len))
        printf "  %s %6.2f sec | %s\n" "$status" "$t" "$(printf '%-*s' "$bar_len" | tr ' ' '═')"
    done
fi

# ── Slowest tests (if Surefire reports available) ───────────────────
extract_slowest_tests() {
    local max_tests=5
    local -a slow_tests=()

    # Find all Surefire test report files
    while IFS= read -r report; do
        [[ -z "$report" ]] && continue

        # Parse test execution times
        while IFS= read -r line; do
            if [[ $line =~ Time\ elapsed:\ ([0-9.]+)\ sec ]]; then
                local duration="${BASH_REMATCH[1]}"
                local test_name=$(echo "$line" | sed 's/ Time elapsed:.*//')
                slow_tests+=("${duration}|${test_name}")
            fi
        done < "$report"
    done < <(find "${REPO_ROOT}" -name "TEST-*.txt" -type f -newer "$TIMINGS_FILE" 2>/dev/null | head -10)

    # Sort by duration (descending) and show top N
    if [[ ${#slow_tests[@]} -gt 0 ]]; then
        printf "\n${C_CYAN}Slowest Tests (Last Build)${C_RESET}\n"
        printf "${C_CYAN}==========================${C_RESET}\n\n"

        while IFS='|' read -r duration test; do
            printf "  %.3f sec - %s\n" "$duration" "$test"
        done < <(printf '%s\n' "${slow_tests[@]}" | sort -t'|' -k1 -rn | head -$max_tests)
    fi
}

extract_slowest_tests

printf "\n"
