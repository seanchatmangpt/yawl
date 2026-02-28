#!/bin/bash

###############################################################################
# YAWL Integration Test Parallelization Benchmark
#
# Phase 3: Strategic Implementation - Benchmark parallel integration test execution
# 
# This script measures:
# 1. Baseline: Sequential integration test execution (forkCount=1)
# 2. Parallel configurations: forkCount=2, 3, 4
# 3. Performance metrics: execution time, CPU/memory, efficiency
# 4. Regression analysis: optimal configuration determination
#
# Usage:
#   ./scripts/benchmark-integration-tests.sh [--fast] [--forkcount <N>]
#
# Options:
#   --fast          Run minimal benchmarks (2 runs instead of 5)
#   --forkcount <N> Test only specific forkCount value
#   --verbose       Enable verbose Maven output
#   --dry-run       Print commands without executing
#
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BENCHMARK_DIR="$PROJECT_ROOT/.claude/profiles/benchmarks"
RESULTS_FILE="$BENCHMARK_DIR/integration-test-benchmark-$(date +%Y%m%d-%H%M%S).json"
METRICS_DIR="$BENCHMARK_DIR/metrics"

# Configuration
NUM_RUNS=5
FORKCOUNT_VALUES=(1 2 3 4)
VERBOSE=false
DRY_RUN=false
FAST_MODE=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Initialize metrics directory
mkdir -p "$METRICS_DIR"

###############################################################################
# Utility Functions
###############################################################################

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[OK]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_separator() {
    echo "=================================================================================================="
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --fast)
                FAST_MODE=true
                NUM_RUNS=2
                shift
                ;;
            --forkcount)
                FORKCOUNT_VALUES=("$2")
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
                log_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done
}

run_benchmark() {
    local forkcount=$1
    local run_number=$2
    local output_file="${METRICS_DIR}/integration-test-forkcount-${forkcount}-run-${run_number}.log"
    
    log_info "Running integration tests with forkCount=${forkcount} (run ${run_number}/${NUM_RUNS})..."
    
    local mvn_cmd="mvn clean verify -P integration-test -Dfailsafe.forkCount=${forkcount} -Dfailsafe.reuseForks=true"
    
    if [ "$VERBOSE" = true ]; then
        mvn_cmd="$mvn_cmd --debug"
    fi
    
    # Capture start time
    local start_time=$(date +%s%N)
    local start_date=$(date '+%Y-%m-%d %H:%M:%S')
    
    if [ "$DRY_RUN" = true ]; then
        log_warn "[DRY RUN] Would execute: $mvn_cmd"
        return 0
    fi
    
    # Run Maven and capture output
    if eval "$mvn_cmd" > "$output_file" 2>&1; then
        local end_time=$(date +%s%N)
        local end_date=$(date '+%Y-%m-%d %H:%M:%S')
        
        # Calculate execution time in seconds
        local duration_ns=$((end_time - start_time))
        local duration_sec=$(echo "scale=3; $duration_ns / 1000000000" | bc)
        
        # Extract test statistics from Maven output
        local tests_run=$(grep -oP '\d+(?= tests? in)' "$output_file" | tail -1 || echo "0")
        local failures=$(grep -oP '\d+(?= failure)' "$output_file" | tail -1 || echo "0")
        local skipped=$(grep -oP '\d+(?= skipped)' "$output_file" | tail -1 || echo "0")
        local cpu_usage=$(top -bn1 | grep "Cpu(s)" | awk '{print 100 - $8}')
        local memory_usage=$(free | grep Mem | awk '{printf "%.1f", ($3/$2) * 100}')
        
        # Store results in JSON format
        local result=$(cat <<RESULT
{
  "forkcount": ${forkcount},
  "run_number": ${run_number},
  "duration_seconds": ${duration_sec},
  "start_time": "${start_date}",
  "end_time": "${end_date}",
  "tests_run": ${tests_run},
  "failures": ${failures},
  "skipped": ${skipped},
  "cpu_usage_percent": ${cpu_usage},
  "memory_usage_percent": ${memory_usage},
  "status": "SUCCESS"
}
RESULT
        )
        
        echo "$result" > "${METRICS_DIR}/integration-test-forkcount-${forkcount}-run-${run_number}.json"
        
        log_success "Integration tests completed in ${duration_sec}s (forkCount=${forkcount}, run ${run_number})"
        echo "$result" | jq '.'
        
    else
        log_error "Integration tests failed with forkCount=${forkcount} (run ${run_number})"
        log_error "See output: $output_file"
        
        # Store failure result
        local end_time=$(date +%s%N)
        local duration_ns=$((end_time - start_time))
        local duration_sec=$(echo "scale=3; $duration_ns / 1000000000" | bc)
        
        local result=$(cat <<RESULT
{
  "forkcount": ${forkcount},
  "run_number": ${run_number},
  "duration_seconds": ${duration_sec},
  "status": "FAILED"
}
RESULT
        )
        
        echo "$result" > "${METRICS_DIR}/integration-test-forkcount-${forkcount}-run-${run_number}.json"
        return 1
    fi
}

analyze_results() {
    log_info "Analyzing benchmark results..."
    
    # Aggregate results by forkCount
    local results_json="["
    local first=true
    
    for forkcount in "${FORKCOUNT_VALUES[@]}"; do
        local durations=()
        local all_passed=true
        
        for run in $(seq 1 $NUM_RUNS); do
            local result_file="${METRICS_DIR}/integration-test-forkcount-${forkcount}-run-${run}.json"
            if [ -f "$result_file" ]; then
                local status=$(jq -r '.status' "$result_file")
                if [ "$status" = "SUCCESS" ]; then
                    local duration=$(jq '.duration_seconds' "$result_file")
                    durations+=("$duration")
                else
                    all_passed=false
                fi
            fi
        done
        
        if [ ${#durations[@]} -gt 0 ]; then
            # Calculate statistics
            local min=$(printf '%s\n' "${durations[@]}" | sort -n | head -1)
            local max=$(printf '%s\n' "${durations[@]}" | sort -n | tail -1)
            local sum=0
            for d in "${durations[@]}"; do
                sum=$(echo "$sum + $d" | bc)
            done
            local mean=$(echo "scale=3; $sum / ${#durations[@]}" | bc)
            
            # Calculate standard deviation
            local variance=0
            for d in "${durations[@]}"; do
                local diff=$(echo "$d - $mean" | bc)
                variance=$(echo "$variance + ($diff * $diff)" | bc)
            done
            local variance=$(echo "scale=6; $variance / ${#durations[@]}" | bc)
            local stddev=$(echo "scale=3; sqrt($variance)" | bc)
            
            if [ "$first" = false ]; then
                results_json="$results_json,"
            fi
            first=false
            
            results_json="$results_json
  {
    \"forkcount\": ${forkcount},
    \"runs_completed\": ${#durations[@]},
    \"all_passed\": $([ "$all_passed" = true ] && echo "true" || echo "false"),
    \"duration_stats\": {
      \"min_seconds\": ${min},
      \"max_seconds\": ${max},
      \"mean_seconds\": ${mean},
      \"stddev_seconds\": ${stddev}
    }
  }"
        fi
    done
    
    results_json="$results_json
]"
    
    echo "$results_json" | jq '.' > "$RESULTS_FILE"
    log_success "Results written to: $RESULTS_FILE"
}

calculate_speedup() {
    log_info "Calculating speedup metrics..."
    
    local baseline_duration=$(jq -r '.[0].duration_stats.mean_seconds' "$RESULTS_FILE")
    
    if [ -z "$baseline_duration" ] || [ "$baseline_duration" = "null" ]; then
        log_error "Could not extract baseline duration"
        return 1
    fi
    
    log_info "Baseline (forkCount=1): ${baseline_duration}s"
    print_separator
    
    jq -r '.[] | 
        "\(.forkcount): \(.duration_stats.mean_seconds)s (\(.runs_completed) runs), stddev: \(.duration_stats.stddev_seconds)s" +
        (if .forkcount > 1 then 
            " | speedup: \('"$baseline_duration"' / .duration_stats.mean_seconds | . * 100 | floor / 100)×, efficiency: \(100 / .forkcount / (.duration_stats.mean_seconds / '"$baseline_duration"') | . * 100 | floor / 100)%"
        else "" end)' \
        "$RESULTS_FILE"
    
    print_separator
}

generate_report() {
    log_info "Generating benchmark report..."
    
    cat > "$BENCHMARK_DIR/INTEGRATION-TEST-BENCHMARK.md" << 'REPORT'
# YAWL Integration Test Parallelization Benchmark Report

**Date**: $(date)
**Project**: YAWL v6.0.0-GA
**Branch**: claude/launch-agents-build-review-qkDBE
**Phase**: 3 - Strategic Implementation

## Executive Summary

This report documents the performance impact of parallelizing integration test execution through Surefire's forkCount parameter. Integration tests currently run sequentially (forkCount=1) to prevent YEngine singleton state corruption between concurrent test classes.

### Key Findings

- **Baseline (forkCount=1)**: Sequential execution baseline established
- **Parallelization Impact**: Tested forkCount={2,3,4} to identify optimal configuration
- **Acceptance Criteria**: 20-30% speedup target

## Methodology

1. **Baseline Measurement**: 5 runs with forkCount=1 (sequential)
2. **Parallel Configurations**: 5 runs each with forkCount=2, 3, 4
3. **Metrics Collected**:
   - Execution time (min/max/mean/stddev)
   - Test reliability (pass/fail rates)
   - Resource utilization (CPU, memory)
   - Performance regression indicators

## Results

### Raw Data

RESULTS_JSON_HERE

### Analysis

#### Speedup Calculation
- Speedup = Baseline Time / Configuration Time
- Efficiency = Speedup / forkCount × 100%

#### Observations

OBSERVATIONS_HERE

## Recommendations

RECOMMENDATIONS_HERE

## Next Steps

NEXT_STEPS_HERE

REPORT
    
    # Fill in actual results
    local results_json=$(cat "$RESULTS_FILE" | jq -c '.')
    sed -i "s|RESULTS_JSON_HERE|$results_json|g" "$BENCHMARK_DIR/INTEGRATION-TEST-BENCHMARK.md"
    
    log_success "Report generated: $BENCHMARK_DIR/INTEGRATION-TEST-BENCHMARK.md"
}

main() {
    parse_arguments "$@"
    
    print_separator
    log_info "YAWL Integration Test Parallelization Benchmark"
    print_separator
    
    log_info "Configuration:"
    log_info "  - Runs per configuration: $NUM_RUNS"
    log_info "  - forkCount values to test: ${FORKCOUNT_VALUES[*]}"
    log_info "  - Results directory: $BENCHMARK_DIR"
    log_info "  - Dry run: $DRY_RUN"
    print_separator
    
    # Run benchmarks
    local failed_runs=0
    for forkcount in "${FORKCOUNT_VALUES[@]}"; do
        for run in $(seq 1 $NUM_RUNS); do
            if ! run_benchmark "$forkcount" "$run"; then
                ((failed_runs++))
            fi
        done
    done
    
    if [ $failed_runs -gt 0 ]; then
        log_warn "$failed_runs runs failed; proceeding with analysis of successful runs"
    fi
    
    # Analyze and report
    analyze_results
    calculate_speedup
    generate_report
    
    print_separator
    log_success "Benchmark complete!"
    log_info "Results: $RESULTS_FILE"
}

main "$@"
