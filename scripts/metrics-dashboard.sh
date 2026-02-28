#!/bin/bash

#
# Real-time Metrics Dashboard for YAWL Stress Tests
# Displays live metrics, trends, and alerts
#

METRICS_DIR="${1:-.}"
REFRESH_INTERVAL="${2:-30}"

if [ ! -d "${METRICS_DIR}" ]; then
    echo "Error: Directory not found: ${METRICS_DIR}"
    exit 1
fi

clear

while true; do
    clear
    
    echo "╔════════════════════════════════════════════════════════════════════════════════╗"
    echo "║                YAWL STRESS TEST METRICS DASHBOARD                             ║"
    echo "║                        [$(date '+%Y-%m-%d %H:%M:%S')]                             ║"
    echo "╚════════════════════════════════════════════════════════════════════════════════╝"
    echo

    # Process each analysis file
    for analysis_file in "${METRICS_DIR}"/analysis-*.json; do
        if [ ! -f "$analysis_file" ]; then
            continue
        fi

        # Extract test name
        test_name=$(grep -oP '"test_name":"\K[^"]+' "$analysis_file" || echo "unknown")
        
        # Extract metrics
        heap_growth=$(grep -oP '"heap_growth_mb_per_hour":\K[\d.]+' "$analysis_file" || echo "0.0")
        heap_min=$(grep -oP '"heap_min_mb":\K\d+' "$analysis_file" || echo "0")
        heap_max=$(grep -oP '"heap_max_mb":\K\d+' "$analysis_file" || echo "0")
        heap_avg=$(grep -oP '"heap_avg_mb":\K[\d.]+' "$analysis_file" || echo "0.0")
        gc_pauses=$(grep -oP '"gc_pauses_per_minute":\K[\d.]+' "$analysis_file" || echo "0.0")
        gc_count=$(grep -oP '"gc_count_total":\K\d+' "$analysis_file" || echo "0")
        throughput=$(grep -oP '"avg_throughput":\K[\d.]+' "$analysis_file" || echo "0.0")
        thread_count=$(grep -oP '"thread_count_final":\K\d+' "$analysis_file" || echo "0")
        alert_status=$(grep -oP '"alert_status":"\K[^"]+' "$analysis_file" || echo "OK")
        lines_analyzed=$(grep -oP '"lines_analyzed":\K\d+' "$analysis_file" || echo "0")

        # Color-code alerts
        if [ "$alert_status" == "OK" ]; then
            status_display="✓ $alert_status"
        elif [ "$alert_status" == "HEAP_LEAK" ]; then
            status_display="⚠ $alert_status (${heap_growth} MB/h)"
        elif [ "$alert_status" == "GC_STORM" ]; then
            status_display="⚠ $alert_status (${gc_pauses} pauses/min)"
        else
            status_display="? $alert_status"
        fi

        # Format test name with padding
        test_name_padded=$(printf "%-12s" "$test_name")
        heap_growth_padded=$(printf "%8s" "$(printf '%.1f' $heap_growth)")
        gc_pauses_padded=$(printf "%6s" "$(printf '%.1f' $gc_pauses)")
        throughput_padded=$(printf "%7s" "$(printf '%.1f' $throughput)")

        echo "┌─ TEST: $test_name_padded ─────────────────────────────────────────────────────┐"
        echo "│                                                                              │"
        echo "│  Heap Memory:                                                               │"
        echo "│    Growth Rate: $heap_growth_padded MB/hour  │  Min: ${heap_min}MB  │  Avg: $(printf '%.1f' $heap_avg)MB  │  Max: ${heap_max}MB │"
        echo "│                                                                              │"
        echo "│  Garbage Collection:                                                        │"
        echo "│    Total GC Events: $gc_count  │  Pauses: $gc_pauses_padded /minute        │"
        echo "│                                                                              │"
        echo "│  Throughput & Threads:                                                      │"
        echo "│    Cases/sec: $throughput_padded  │  Active Threads: $thread_count  │  Samples: $lines_analyzed │"
        echo "│                                                                              │"
        echo "│  Status: $status_display"
        echo "└──────────────────────────────────────────────────────────────────────────────┘"
        echo

    done

    # Summary stats across all tests
    echo "╔════════════════════════════════════════════════════════════════════════════════╗"
    echo "║  Overall Health Check                                                          ║"
    
    total_alerts=$(grep -l "HEAP_LEAK\|GC_STORM" "${METRICS_DIR}"/analysis-*.json 2>/dev/null | wc -l)
    if [ $total_alerts -eq 0 ]; then
        echo "║  All systems: GREEN                                                          ║"
    else
        echo "║  Active alerts: $total_alerts                                                        ║"
    fi
    
    echo "║  Next update in ${REFRESH_INTERVAL} seconds (Ctrl+C to exit)                  ║"
    echo "╚════════════════════════════════════════════════════════════════════════════════╝"

    sleep "${REFRESH_INTERVAL}"
done
