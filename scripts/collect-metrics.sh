#!/usr/bin/env bash
################################################################################
# YAWL Metrics Collection Helper
# Provides utilities for capturing build and test metrics:
#   - Timing information (compile, test, verify phases)
#   - Memory usage (peak RSS, JVM heap)
#   - CPU utilization (average, peak)
#   - Test results (passed, failed, skipped)
#   - Build cache statistics
#
# This script is meant to be sourced by other build scripts, not run directly.
#
# Usage (in another script):
#   source scripts/collect-metrics.sh
#   start_metrics "my-scenario"
#   # ... do work ...
#   end_metrics
#   save_metrics "output.json"
#
# Functions exported:
#   start_metrics SCENARIO         - Start metrics collection
#   end_metrics                    - Stop metrics collection
#   record_phase NAME START END    - Record phase timing
#   get_memory_usage               - Get current memory in MB
#   get_peak_memory                - Get peak memory in MB
#   save_metrics OUTPUT_FILE       - Save collected metrics to JSON
#
################################################################################

set -euo pipefail

# ============================================================================
# METRICS COLLECTION STATE
# ============================================================================

declare -A METRICS_START_TIME
declare -A METRICS_END_TIME
declare -A METRICS_PHASES
declare -A METRICS_MEMORY_PEAK
declare -A METRICS_CPU_AVG

METRICS_SCENARIO=""
METRICS_PID=""
METRICS_TEMP_FILE="/tmp/yawl-metrics-$$.json"

# ============================================================================
# CORE FUNCTIONS
# ============================================================================

# Initialize metrics collection for a scenario
start_metrics() {
    local scenario=$1
    METRICS_SCENARIO="${scenario}"
    METRICS_START_TIME[${scenario}]=$(date +%s%N)
    METRICS_PID=$$

    # Create temporary JSON structure
    cat > "${METRICS_TEMP_FILE}" << EOF
{
  "scenario": "${scenario}",
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "phases": {},
  "memory_peak_mb": 0,
  "cpu_avg_percent": 0,
  "test_counts": {
    "passed": 0,
    "failed": 0,
    "skipped": 0
  }
}
EOF
}

# End metrics collection and compute totals
end_metrics() {
    if [[ -z "${METRICS_SCENARIO}" ]]; then
        return
    fi

    local scenario="${METRICS_SCENARIO}"
    METRICS_END_TIME[${scenario}]=$(date +%s%N)

    local start_ns=${METRICS_START_TIME[${scenario}]}
    local end_ns=${METRICS_END_TIME[${scenario}]}
    local total_time_ms=$(( (end_ns - start_ns) / 1000000 ))

    # Update total time in temp JSON
    jq --arg total "${total_time_ms}" '.total_time_ms = ($total | tonumber)' \
        "${METRICS_TEMP_FILE}" > "${METRICS_TEMP_FILE}.tmp" && \
        mv "${METRICS_TEMP_FILE}.tmp" "${METRICS_TEMP_FILE}"
}

# Record a build phase's timing (compile, test, verify, etc.)
record_phase() {
    local phase_name=$1
    local start_ns=$2
    local end_ns=$3

    if [[ -z "${METRICS_SCENARIO}" ]]; then
        return
    fi

    local duration_ms=$(( (end_ns - start_ns) / 1000000 ))

    # Append phase to JSON phases object
    jq --arg phase "${phase_name}" --arg duration "${duration_ms}" \
        '.phases[$phase] = ($duration | tonumber)' \
        "${METRICS_TEMP_FILE}" > "${METRICS_TEMP_FILE}.tmp" && \
        mv "${METRICS_TEMP_FILE}.tmp" "${METRICS_TEMP_FILE}"
}

# Get current memory usage of process in MB
get_memory_usage() {
    local pid=${1:-$$}

    if [[ -f "/proc/${pid}/status" ]]; then
        awk '/^VmRSS:/ {print int($2/1024)}' "/proc/${pid}/status"
    else
        echo "0"
    fi
}

# Get peak memory usage recorded during execution
get_peak_memory() {
    local pid=${1:-$$}

    # Check /proc/pid/smaps for more detailed memory info (if available)
    if [[ -f "/proc/${pid}/smaps" ]]; then
        awk '/^[^ \t]/ {addr=$1} /^Rss:/ {rss+=$2} END {print int(rss/1024)}' "/proc/${pid}/smaps"
    elif [[ -f "/proc/${pid}/status" ]]; then
        awk '/^VmPeak:/ {print int($2/1024)}' "/proc/${pid}/status"
    else
        echo "0"
    fi
}

# Get average CPU utilization percentage
get_cpu_average() {
    local duration_ms=${1:-1000}
    local duration_sec=$(( duration_ms / 1000 ))

    # Use /proc/stat-based calculation
    if [[ ! -f "/proc/stat" ]]; then
        echo "0"
        return
    fi

    local start_cpu=$(awk '/^cpu / {print $2+$3+$4+$5+$6+$7+$8}' /proc/stat)
    local start_idle=$(awk '/^cpu / {print $5}' /proc/stat)

    sleep "${duration_sec}" || true

    local end_cpu=$(awk '/^cpu / {print $2+$3+$4+$5+$6+$7+$8}' /proc/stat)
    local end_idle=$(awk '/^cpu / {print $5}' /proc/stat)

    local total_cpu=$((end_cpu - start_cpu))
    local total_idle=$((end_idle - start_idle))
    local total=$((total_cpu + total_idle))

    if [[ ${total} -gt 0 ]]; then
        echo $((total_cpu * 100 / total))
    else
        echo "0"
    fi
}

# Parse Surefire test reports and extract test counts
parse_test_results() {
    local surefire_dir="${1:-target/surefire-reports}"
    local passed=0
    local failed=0
    local skipped=0

    if [[ ! -d "${surefire_dir}" ]]; then
        echo "0 0 0"
        return
    fi

    # Parse XML test reports
    for xml_file in "${surefire_dir}"/TEST-*.xml; do
        if [[ -f "${xml_file}" ]]; then
            # Extract test counts from testuite element
            local tests=$(grep -o 'tests="[^"]*"' "${xml_file}" | grep -o '[0-9]*' | head -1)
            local failures=$(grep -o 'failures="[^"]*"' "${xml_file}" | grep -o '[0-9]*' | head -1)
            local skips=$(grep -o 'skipped="[^"]*"' "${xml_file}" | grep -o '[0-9]*' | head -1)

            passed=$((passed + (tests - failures - skips)))
            failed=$((failed + failures))
            skipped=$((skipped + skips))
        fi
    done

    echo "${passed} ${failed} ${skipped}"
}

# Record test results from Surefire reports
record_test_results() {
    local surefire_dir="${1:-target/surefire-reports}"

    if [[ -z "${METRICS_SCENARIO}" ]]; then
        return
    fi

    local results=$(parse_test_results "${surefire_dir}")
    local passed=$(echo "${results}" | awk '{print $1}')
    local failed=$(echo "${results}" | awk '{print $2}')
    local skipped=$(echo "${results}" | awk '{print $3}')

    jq \
        --arg passed "${passed}" \
        --arg failed "${failed}" \
        --arg skipped "${skipped}" \
        '.test_counts.passed = ($passed | tonumber) |
         .test_counts.failed = ($failed | tonumber) |
         .test_counts.skipped = ($skipped | tonumber)' \
        "${METRICS_TEMP_FILE}" > "${METRICS_TEMP_FILE}.tmp" && \
        mv "${METRICS_TEMP_FILE}.tmp" "${METRICS_TEMP_FILE}"
}

# Record memory and CPU metrics
record_system_metrics() {
    if [[ -z "${METRICS_SCENARIO}" ]]; then
        return
    fi

    local memory_mb=$(get_peak_memory "${METRICS_PID}")
    local cpu_avg=$(get_cpu_average 1000)

    jq \
        --arg memory "${memory_mb}" \
        --arg cpu "${cpu_avg}" \
        '.memory_peak_mb = ($memory | tonumber) |
         .cpu_avg_percent = ($cpu | tonumber)' \
        "${METRICS_TEMP_FILE}" > "${METRICS_TEMP_FILE}.tmp" && \
        mv "${METRICS_TEMP_FILE}.tmp" "${METRICS_TEMP_FILE}"
}

# Save collected metrics to JSON file
save_metrics() {
    local output_file=$1

    if [[ ! -f "${METRICS_TEMP_FILE}" ]]; then
        echo "ERROR: No metrics collected" >&2
        return 1
    fi

    # Record system metrics before saving
    record_system_metrics

    # Pretty-print and save
    jq '.' "${METRICS_TEMP_FILE}" > "${output_file}"

    # Clean up temp file
    rm -f "${METRICS_TEMP_FILE}" "${METRICS_TEMP_FILE}.tmp"
}

# Get metrics as JSON object (without saving)
get_metrics_json() {
    if [[ -f "${METRICS_TEMP_FILE}" ]]; then
        jq '.' "${METRICS_TEMP_FILE}"
    else
        echo "{}"
    fi
}

# Clean up metrics on exit
cleanup_metrics() {
    if [[ -f "${METRICS_TEMP_FILE}" ]]; then
        rm -f "${METRICS_TEMP_FILE}" "${METRICS_TEMP_FILE}.tmp"
    fi
}

trap cleanup_metrics EXIT

# Export functions for use by sourcing scripts
export -f start_metrics
export -f end_metrics
export -f record_phase
export -f get_memory_usage
export -f get_peak_memory
export -f get_cpu_average
export -f parse_test_results
export -f record_test_results
export -f record_system_metrics
export -f save_metrics
export -f get_metrics_json
export -f cleanup_metrics

# Only run this section if script is executed directly (not sourced)
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    echo "This script is meant to be sourced, not executed directly."
    echo "Usage: source scripts/collect-metrics.sh"
    exit 1
fi
