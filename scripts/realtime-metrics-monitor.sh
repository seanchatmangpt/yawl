#!/bin/bash

#
# Real-time Metrics Monitor for YAWL Stress Tests
# Parses JSONL metrics and generates alerts
# Can run independently alongside stress tests
#

set -euo pipefail

METRICS_DIR="${1:-.}"
SUMMARY_FILE="${METRICS_DIR}/realtime-analysis.txt"
VERBOSE="${2:-false}"

# Alert thresholds (from Java analyzer)
HEAP_GROWTH_ALERT_MB_PER_HOUR=1000
GC_PAUSE_ALERT_PER_MINUTE=2
CHECK_INTERVAL=10

echo "Starting Real-time Metrics Monitor"
echo "Metrics directory: ${METRICS_DIR}"
echo "Summary file: ${SUMMARY_FILE}"
echo "Verbose: ${VERBOSE}"
echo
echo "Alert thresholds:"
echo "  - Heap growth: ${HEAP_GROWTH_ALERT_MB_PER_HOUR} MB/hour"
echo "  - GC pauses: ${GC_PAUSE_ALERT_PER_MINUTE} per minute"
echo

# Initialize tracking
declare -A last_line_count
declare -A last_check_time

{
    echo "# Real-time Metrics Monitor Started: $(date)"
    echo "# Check interval: ${CHECK_INTERVAL}s"
} >> "${SUMMARY_FILE}"

while true; do
    for metrics_file in "${METRICS_DIR}"/metrics-*.jsonl; do
        [ -f "$metrics_file" ] || continue

        test_name=$(basename "$metrics_file" | sed 's/metrics-//;s/.jsonl//')
        
        # Count lines in metrics file
        current_line_count=$(wc -l < "$metrics_file" 2>/dev/null || echo 0)
        last_count=${last_line_count[$test_name]:-0}

        # Only analyze if file has grown
        if [ "$current_line_count" -gt "$last_count" ]; then
            last_line_count[$test_name]=$current_line_count

            # Extract latest metrics using tail and jq/grep
            latest_line=$(tail -1 "$metrics_file")

            # Parse metrics with grep
            timestamp=$(echo "$latest_line" | grep -oP '"timestamp":\K\d+' || echo 0)
            heap_used=$(echo "$latest_line" | grep -oP '"heap_used_mb":\K\d+' || echo 0)
            gc_count=$(echo "$latest_line" | grep -oP '"gc_collection_count":\K\d+' || echo 0)
            throughput=$(echo "$latest_line" | grep -oP '"throughput_cases_per_sec":\K[\d.]+' || echo 0)
            threads=$(echo "$latest_line" | grep -oP '"thread_count":\K\d+' || echo 0)

            # For growth rate, we need first and last line
            first_line=$(head -1 "$metrics_file")
            first_timestamp=$(echo "$first_line" | grep -oP '"timestamp":\K\d+' || echo 0)
            first_heap=$(echo "$first_line" | grep -oP '"heap_used_mb":\K\d+' || echo 0)

            # Calculate growth rate
            if [ "$first_timestamp" -ne "$timestamp" ] && [ "$timestamp" -ne 0 ]; then
                time_delta_ms=$((timestamp - first_timestamp))
                heap_delta_mb=$((heap_used - first_heap))
                heap_growth_per_hour=$(echo "scale=2; ($heap_delta_mb * 3600000) / $time_delta_ms" | bc 2>/dev/null || echo 0)
            else
                heap_growth_per_hour=0
            fi

            # Calculate GC pauses per minute
            if [ "$first_timestamp" -ne "$timestamp" ] && [ "$timestamp" -ne 0 ]; then
                time_delta_ms=$((timestamp - first_timestamp))
                gc_pauses_per_min=$(echo "scale=2; ($gc_count * 60000) / $time_delta_ms" | bc 2>/dev/null || echo 0)
            else
                gc_pauses_per_min=0
            fi

            # Generate analysis
            alert_status="OK"
            if (( $(echo "$heap_growth_per_hour > $HEAP_GROWTH_ALERT_MB_PER_HOUR" | bc -l) )); then
                alert_status="HEAP_LEAK"
            elif (( $(echo "$gc_pauses_per_min > $GC_PAUSE_ALERT_PER_MINUTE" | bc -l) )); then
                alert_status="GC_STORM"
            fi

            # Print to console
            time_str=$(date '+%H:%M:%S')
            printf "[%s] %-12s | Heap: %5d MB (+%.0f/h) | GC: %4.1f/min | TPut: %6.1f | Threads: %3d | %s\n" \
                "$time_str" "$test_name" "$heap_used" "$heap_growth_per_hour" \
                "$gc_pauses_per_min" "$throughput" "$threads" "$alert_status"

            # Write to summary file
            {
                printf "[%s] %s | Heap: %d MB (+%.1f MB/h) | GC: %.1f pauses/min | Throughput: %.1f c/s | Threads: %d | Alert: %s\n" \
                    "$time_str" "$test_name" "$heap_used" "$heap_growth_per_hour" \
                    "$gc_pauses_per_min" "$throughput" "$threads" "$alert_status"
            } >> "${SUMMARY_FILE}"

            # Alert if threshold exceeded
            if [ "$alert_status" != "OK" ]; then
                echo "[$(date '+%H:%M:%S')] ALERT: $test_name - $alert_status (Heap: ${heap_growth_per_hour} MB/h, GC: ${gc_pauses_per_min}/min)" >&2
                {
                    echo "# ALERT: $test_name - $alert_status"
                    echo "# Heap growth: ${heap_growth_per_hour} MB/hour"
                    echo "# GC pauses: ${gc_pauses_per_min} per minute"
                    echo "# Timestamp: $time_str"
                } >> "${SUMMARY_FILE}"
            fi

            if [ "$VERBOSE" == "true" ]; then
                {
                    echo "# Latest metrics from $test_name:"
                    echo "$latest_line"
                } >> "${SUMMARY_FILE}"
            fi
        fi
    done

    sleep "$CHECK_INTERVAL"
done
