#!/bin/bash

##############################################################################
# YAWL Phase 4: Build Performance Monitoring Script
# Purpose: Monitor and compare current build time to baseline, detect regressions
#
# Usage: bash scripts/monitor-build-performance.sh [options]
# Options:
#   --profile PROF    Maven profile to use (default: integration-parallel)
#   --baseline FILE   Baseline metrics JSON (default: auto-detect)
#   --threshold PCT   Regression threshold % (default: 5)
#   --output FILE     Output metrics file (default: .claude/metrics/build-metrics-{date}.json)
#   --verbose         Print detailed information
#   --dry-run         Show what would be executed
#
# Example:
#   bash scripts/monitor-build-performance.sh
#   bash scripts/monitor-build-performance.sh --profile integration-parallel --threshold 10 --verbose
##############################################################################

set -euo pipefail

# ============================================================================
# CONFIGURATION
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
METRICS_DIR="${PROJECT_ROOT}/.claude/metrics"
BASELINE_FILE=""
MAVEN_PROFILE="integration-parallel"
REGRESSION_THRESHOLD=5
OUTPUT_FILE=""
VERBOSE=false
DRY_RUN=false

mkdir -p "${METRICS_DIR}"

# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

print_info() {
    echo "[INFO] $*"
}

print_success() {
    echo "$(tput setaf 2)[SUCCESS]$(tput sgr0) $*"
}

print_warn() {
    echo "$(tput setaf 3)[WARN]$(tput sgr0) $*"
}

print_error() {
    echo "$(tput setaf 1)[ERROR]$(tput sgr0) $*" >&2
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --profile)
                MAVEN_PROFILE="$2"
                shift 2
                ;;
            --baseline)
                BASELINE_FILE="$2"
                shift 2
                ;;
            --threshold)
                REGRESSION_THRESHOLD="$2"
                shift 2
                ;;
            --output)
                OUTPUT_FILE="$2"
                shift 2
                ;;
            --verbose)
                VERBOSE=true
                shift
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            *)
                print_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done
}

# Find baseline metrics file
find_baseline_file() {
    # Priority order:
    # 1. .claude/PHASE4-BUILD-METRICS.json (official baseline)
    # 2. Most recent .claude/metrics/build-metrics-*.json
    # 3. .claude/metrics/baseline.json

    if [[ -f "${PROJECT_ROOT}/.claude/PHASE4-BUILD-METRICS.json" ]]; then
        echo "${PROJECT_ROOT}/.claude/PHASE4-BUILD-METRICS.json"
    elif [[ -d "${METRICS_DIR}" ]]; then
        local latest=$(find "${METRICS_DIR}" -name "build-metrics-*.json" -type f -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -1 | awk '{print $2}')
        if [[ -n "$latest" ]]; then
            echo "$latest"
        fi
    fi

    # Fallback to default baseline
    echo "${METRICS_DIR}/baseline.json"
}

# Extract metric from JSON
extract_metric() {
    local json_file="$1"
    local metric_path="$2"

    if [[ ! -f "$json_file" ]]; then
        echo "null"
        return 1
    fi

    # Use Python or jq if available, otherwise fallback to grep
    if command -v jq &> /dev/null; then
        jq -r "$metric_path" "$json_file" 2>/dev/null || echo "null"
    elif command -v python3 &> /dev/null; then
        python3 -c "import json; f=json.load(open('$json_file')); print(eval('f$metric_path'))" 2>/dev/null || echo "null"
    else
        grep -o "$metric_path\":[^,}]*" "$json_file" 2>/dev/null | cut -d: -f2 | tr -d ' "' || echo "null"
    fi
}

# Execute single build and measure time
run_single_build() {
    local log_file="${METRICS_DIR}/.build-monitoring.log"

    if [[ "$DRY_RUN" == "true" ]]; then
        print_info "[DRY_RUN] Would execute: mvn clean verify -P ${MAVEN_PROFILE}"
        echo "85.00"
        return 0
    fi

    cd "${PROJECT_ROOT}"

    print_info "Running build with profile: $MAVEN_PROFILE"

    local start_time=$(date +%s%N)

    timeout 600 mvn -q clean verify -P "${MAVEN_PROFILE}" \
        -DskipITs=false \
        -Dorg.slf4j.simpleLogger.defaultLogLevel=warn \
        > "${log_file}" 2>&1 || print_warn "Build may have failed, continuing with analysis..."

    local end_time=$(date +%s%N)
    local elapsed_ns=$((end_time - start_time))
    local elapsed_s=$(echo "scale=2; $elapsed_ns / 1000000000" | bc)

    # Check build status
    if grep -q "BUILD SUCCESS" "${log_file}"; then
        print_success "Build completed successfully"
    else
        print_warn "Build may not have succeeded (see logs)"
    fi

    echo "$elapsed_s"
}

# Compare against baseline
compare_to_baseline() {
    local current_time="$1"
    local baseline_file="$2"

    if [[ ! -f "$baseline_file" ]]; then
        print_warn "Baseline file not found: $baseline_file"
        echo '{"status": "no_baseline", "message": "Creating new baseline"}'
        return 1
    fi

    # Extract baseline time
    local baseline_time=$(extract_metric "$baseline_file" '.optimized.time')

    if [[ "$baseline_time" == "null" ]]; then
        baseline_time=$(extract_metric "$baseline_file" '.parallel.mean_seconds')
    fi

    if [[ "$baseline_time" == "null" ]]; then
        print_warn "Could not extract baseline time from: $baseline_file"
        echo '{"status": "no_baseline", "message": "Creating new baseline"}'
        return 1
    fi

    print_info "Baseline time: ${baseline_time}s"
    print_info "Current time:  ${current_time}s"

    # Calculate difference
    local diff=$(echo "$current_time - $baseline_time" | bc)
    local diff_pct=$(echo "scale=1; ($diff / $baseline_time) * 100" | bc)

    # Determine regression status
    local status="GREEN"
    if (( $(echo "$diff_pct > $REGRESSION_THRESHOLD" | bc -l) )); then
        status="RED"
        print_error "REGRESSION DETECTED: Build is ${diff_pct}% slower than baseline (threshold: ${REGRESSION_THRESHOLD}%)"
    elif (( $(echo "$diff_pct > 0" | bc -l) )); then
        status="YELLOW"
        print_warn "Possible slowdown: Build is ${diff_pct}% slower (within threshold)"
    else
        status="GREEN"
        print_success "No regression: Build is ${diff_pct}% faster than baseline"
    fi

    cat << EOF
{
  "status": "$status",
  "baseline_seconds": $baseline_time,
  "current_seconds": $current_time,
  "difference_seconds": $diff,
  "difference_percentage": $diff_pct,
  "threshold_percentage": $REGRESSION_THRESHOLD,
  "message": "$([ "$status" = "GREEN" ] && echo "Build performance within threshold" || echo "Build performance regression detected")"
}
EOF
}

# Create weekly summary report
create_weekly_summary() {
    local metrics_dir="$1"
    local summary_file="${metrics_dir}/weekly-summary-$(date +%Y-W%V).json"

    # Find all metrics from this week
    local week_start=$(date -d "$(date +%Y-W%V-1)" +%Y-%m-%d)
    local week_metrics=$(find "${metrics_dir}" -name "build-metrics-*.json" -type f -newer <(date -d "$week_start") 2>/dev/null)

    if [[ -z "$week_metrics" ]]; then
        print_info "No metrics found for this week"
        return 0
    fi

    local total_runs=0
    local total_time=0
    local avg_time=0
    local min_time=999999
    local max_time=0

    # Process each metrics file
    while IFS= read -r metric_file; do
        if [[ -z "$metric_file" ]]; then
            continue
        fi

        local par_mean=$(extract_metric "$metric_file" '.parallel.mean_seconds')
        if [[ "$par_mean" != "null" && -n "$par_mean" ]]; then
            total_time=$(echo "$total_time + $par_mean" | bc)
            ((total_runs++))

            # Update min/max
            min_time=$(echo "if ($par_mean < $min_time) $par_mean else $min_time" | bc)
            max_time=$(echo "if ($par_mean > $max_time) $par_mean else $max_time" | bc)
        fi
    done <<< "$week_metrics"

    if [[ $total_runs -gt 0 ]]; then
        avg_time=$(echo "scale=2; $total_time / $total_runs" | bc)
    fi

    cat > "${summary_file}" << EOF
{
  "week": "$(date +%Y-W%V)",
  "start_date": "$week_start",
  "end_date": "$(date +%Y-%m-%d)",
  "total_builds": $total_runs,
  "average_time_seconds": $avg_time,
  "min_time_seconds": $min_time,
  "max_time_seconds": $max_time,
  "generated_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

    print_success "Weekly summary created: $summary_file"
}

# ============================================================================
# MAIN EXECUTION
# ============================================================================

main() {
    parse_arguments "$@"

    print_info "YAWL Phase 4: Build Performance Monitoring"
    print_info "=========================================="
    print_info "Project: $PROJECT_ROOT"
    print_info "Metrics dir: $METRICS_DIR"
    print_info "Maven profile: $MAVEN_PROFILE"
    print_info "Regression threshold: ${REGRESSION_THRESHOLD}%"

    # Determine baseline file
    if [[ -z "$BASELINE_FILE" ]]; then
        BASELINE_FILE=$(find_baseline_file)
    fi

    print_info "Baseline file: $BASELINE_FILE"

    # Determine output file
    if [[ -z "$OUTPUT_FILE" ]]; then
        OUTPUT_FILE="${METRICS_DIR}/build-metrics-$(date +%Y%m%d-%H%M%S).json"
    fi

    # Run the build
    print_info ""
    local current_time=$(run_single_build)

    # Compare to baseline
    print_info ""
    print_info "Comparing to baseline..."
    local comparison=$(compare_to_baseline "$current_time" "$BASELINE_FILE")

    # Write comprehensive metrics
    cat > "${OUTPUT_FILE}" << EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "build_date": "$(date +%Y-%m-%d)",
  "maven_profile": "$MAVEN_PROFILE",
  "current_build": {
    "time_seconds": $current_time,
    "date": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  },
  "comparison": $comparison,
  "baseline_file": "$BASELINE_FILE"
}
EOF

    print_success ""
    print_success "Monitoring metrics saved: $OUTPUT_FILE"

    # Create weekly summary
    create_weekly_summary "${METRICS_DIR}"

    # Print comparison results
    print_info ""
    print_info "Comparison Results:"
    print_info "==================="
    echo "$comparison" | if command -v jq &> /dev/null; then
        jq '.'
    else
        cat
    fi

    # Print recommendation
    local status=$(echo "$comparison" | grep -o '"status":"[^"]*' | cut -d: -f2 | tr -d '"')
    print_info ""

    case "$status" in
        GREEN)
            print_success "Build performance is healthy. No action needed."
            return 0
            ;;
        YELLOW)
            print_warn "Build is slightly slower. Monitor for trends."
            return 0
            ;;
        RED)
            print_error "Build performance regression detected!"
            print_error "Investigate: Profile in more detail or optimize hot paths"
            return 1
            ;;
        *)
            print_info "Status: $status"
            return 0
            ;;
    esac
}

# Run main
main "$@"
