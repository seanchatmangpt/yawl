#!/bin/bash
set -euo pipefail

# YAWL Build Performance Analysis Utility
# Analyzes build-performance.json to show trends and insights

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PERF_FILE="${PROJECT_ROOT}/build-performance.json"

if [[ ! -f "$PERF_FILE" ]]; then
    echo "Error: No performance data found at $PERF_FILE"
    echo "Run ./build-timer.sh first to generate performance data"
    exit 1
fi

if ! command -v jq &>/dev/null; then
    echo "Error: jq is required for analysis"
    echo "Install with: apt-get install jq"
    exit 1
fi

show_summary() {
    echo "========================================"
    echo "  Build Performance Analysis"
    echo "========================================"
    echo ""

    local total_builds=$(jq '. | length' "$PERF_FILE")
    echo "Total Builds Tracked: $total_builds"
    echo ""

    if [[ "$total_builds" -eq 0 ]]; then
        echo "No build data available"
        return
    fi

    local latest=$(jq -r '.[-1] | "\(.timestamp): \(.total_time_seconds)s (\(.build_command))"' "$PERF_FILE")
    echo "Latest Build:"
    echo "  $latest"
    echo ""

    local avg_time=$(jq '[.[] | .total_time_seconds] | add / length' "$PERF_FILE")
    local min_time=$(jq '[.[] | .total_time_seconds] | min' "$PERF_FILE")
    local max_time=$(jq '[.[] | .total_time_seconds] | max' "$PERF_FILE")

    printf "Average Build Time: %.2fs\n" "$avg_time"
    printf "Fastest Build: %.2fs\n" "$min_time"
    printf "Slowest Build: %.2fs\n" "$max_time"
    echo ""
}

show_module_ranking() {
    echo "========================================"
    echo "  Slowest Modules (Average Times)"
    echo "========================================"
    echo ""

    jq -r '
        [.[] | .modules | to_entries[]] |
        group_by(.key) |
        map({
            module: .[0].key,
            avg: (map(.value) | add / length),
            min: (map(.value) | min),
            max: (map(.value) | max)
        }) |
        sort_by(.avg) |
        reverse |
        .[] |
        "\(.module): avg=\(.avg)s min=\(.min)s max=\(.max)s"
    ' "$PERF_FILE" | while IFS= read -r line; do
        echo "  $line"
    done
    echo ""
}

show_trends() {
    echo "========================================"
    echo "  Build Time Trends (Last 10 Builds)"
    echo "========================================"
    echo ""

    jq -r '.[-10:] | .[] | "\(.timestamp) \(.total_time_seconds)"' "$PERF_FILE" | \
    while read timestamp time; do
        local bars=$(echo "$time * 20" | bc | cut -d. -f1)
        local bar_str=$(printf '█%.0s' $(seq 1 $bars 2>/dev/null || echo ""))
        printf "%-25s %6.2fs %s\n" "$timestamp" "$time" "$bar_str"
    done
    echo ""
}

show_cache_effectiveness() {
    echo "========================================"
    echo "  Cache Effectiveness"
    echo "========================================"
    echo ""

    local total=$(jq '. | length' "$PERF_FILE")
    local cache_hits=$(jq '[.[] | select(.cache_hit == true)] | length' "$PERF_FILE")
    local cache_misses=$(echo "$total - $cache_hits" | bc)

    if [[ "$total" -gt 0 ]]; then
        local hit_rate=$(echo "scale=1; ($cache_hits / $total) * 100" | bc)
        echo "  Cache Hits: $cache_hits"
        echo "  Cache Misses: $cache_misses"
        printf "  Hit Rate: %.1f%%\n" "$hit_rate"
    else
        echo "  No data available"
    fi
    echo ""

    if [[ "$cache_hits" -gt 0 ]] && [[ "$cache_misses" -gt 0 ]]; then
        local avg_hit=$(jq '[.[] | select(.cache_hit == true) | .total_time_seconds] | add / length' "$PERF_FILE")
        local avg_miss=$(jq '[.[] | select(.cache_hit == false) | .total_time_seconds] | add / length' "$PERF_FILE")
        local savings=$(echo "scale=1; (($avg_miss - $avg_hit) / $avg_miss) * 100" | bc)

        printf "  Avg with cache: %.2fs\n" "$avg_hit"
        printf "  Avg without cache: %.2fs\n" "$avg_miss"
        printf "  Cache savings: %.1f%%\n" "$savings"
        echo ""
    fi
}

show_parallel_analysis() {
    echo "========================================"
    echo "  Parallel Build Analysis"
    echo "========================================"
    echo ""

    jq -r '
        group_by(.parallel_threads) |
        map({
            threads: .[0].parallel_threads,
            avg_time: (map(.total_time_seconds) | add / length),
            count: length
        }) |
        sort_by(.threads) |
        .[] |
        "  \(.threads) threads: avg \(.avg_time)s (\(.count) builds)"
    ' "$PERF_FILE"
    echo ""
}

export_csv() {
    local output_file="${1:-build-performance.csv}"

    echo "Exporting to $output_file..."

    echo "timestamp,command,total_time_seconds,parallel_threads,cache_hit" > "$output_file"

    jq -r '.[] | [.timestamp, .build_command, .total_time_seconds, .parallel_threads, .cache_hit] | @csv' "$PERF_FILE" >> "$output_file"

    echo "Export complete: $output_file"
}

show_help() {
    cat <<EOF
YAWL Build Performance Analyzer

Usage: $0 [command]

Commands:
    summary         Show performance summary (default)
    modules         Show module ranking by average time
    trends          Show build time trends
    cache           Show cache effectiveness
    parallel        Show parallel build analysis
    all             Show all analyses
    export [file]   Export data to CSV (default: build-performance.csv)
    help            Show this help message

Examples:
    $0                      # Show summary
    $0 all                  # Show all analyses
    $0 export my-data.csv   # Export to CSV

EOF
}

main() {
    local command="${1:-summary}"

    case "$command" in
        summary)
            show_summary
            ;;
        modules)
            show_module_ranking
            ;;
        trends)
            show_trends
            ;;
        cache)
            show_cache_effectiveness
            ;;
        parallel)
            show_parallel_analysis
            ;;
        all)
            show_summary
            show_module_ranking
            show_trends
            show_cache_effectiveness
            show_parallel_analysis
            ;;
        export)
            export_csv "${2:-build-performance.csv}"
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            echo "Unknown command: $command"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

main "$@"
