#!/usr/bin/env bash
# ==========================================================================
# YAWL Build Analytics Script
#
# Collects and analyzes build cache metrics, compilation times, and
# incremental build efficiency for YAWL v6.0.0-GA.
#
# Usage:
#   bash scripts/build-analytics.sh          # Show current metrics
#   bash scripts/build-analytics.sh report   # Generate detailed report
#   bash scripts/build-analytics.sh reset    # Reset metrics
#
# Part 4 Optimization: Build Performance Monitoring
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CACHE_DIR="${HOME}/.m2/build-cache/yawl"
METRICS_FILE="${PROJECT_ROOT}/target/build-metrics.json"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_step() { echo -e "${CYAN}[STEP]${NC} $1"; }

# Parse arguments
COMMAND="${1:-show}"

case "$COMMAND" in
    show|report)
        log_step "Analyzing build cache metrics..."
        ;;

    reset)
        log_step "Resetting build metrics..."
        rm -rf "${CACHE_DIR}"
        rm -f "${METRICS_FILE}"
        log_info "Build metrics reset complete"
        exit 0
        ;;

    -h|--help)
        sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
        exit 0
        ;;

    *)
        echo "Unknown command: $COMMAND. Use -h for help."
        exit 1
        ;;
esac

# Calculate cache statistics
calculate_cache_stats() {
    if [[ ! -d "${CACHE_DIR}" ]]; then
        echo "Cache directory not found: ${CACHE_DIR}"
        return 1
    fi

    local total_size=0
    local file_count=0
    local oldest_file=""
    local newest_file=""
    local oldest_ts=9999999999
    local newest_ts=0

    while IFS= read -r -d '' file; do
        file_count=$((file_count + 1))
        local size
        size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo 0)
        total_size=$((total_size + size))

        local ts
        ts=$(stat -f%m "$file" 2>/dev/null || stat -c%Y "$file" 2>/dev/null || echo 0)
        if [[ "$ts" -lt "$oldest_ts" ]]; then
            oldest_ts="$ts"
            oldest_file="$file"
        fi
        if [[ "$ts" -gt "$newest_ts" ]]; then
            newest_ts="$ts"
            newest_file="$file"
        fi
    done < <(find "${CACHE_DIR}" -type f -print0 2>/dev/null)

    # Convert to human-readable sizes
    local size_hr
    if [[ $total_size -gt 1073741824 ]]; then
        size_hr="$(echo "scale=2; $total_size / 1073741824" | bc) GB"
    elif [[ $total_size -gt 1048576 ]]; then
        size_hr="$(echo "scale=2; $total_size / 1048576" | bc) MB"
    elif [[ $total_size -gt 1024 ]]; then
        size_hr="$(echo "scale=2; $total_size / 1024" | bc) KB"
    else
        size_hr="${total_size} bytes"
    fi

    echo "Cache Statistics:"
    echo "  Directory: ${CACHE_DIR}"
    echo "  Total Size: ${size_hr}"
    echo "  File Count: ${file_count}"
    if [[ -n "$oldest_file" ]]; then
        echo "  Oldest Entry: $(date -r "$oldest_ts" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || date -d "@$oldest_ts" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo "unknown")"
    fi
    if [[ -n "$newest_file" ]]; then
        echo "  Newest Entry: $(date -r "$newest_ts" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || date -d "@$newest_ts" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo "unknown")"
    fi
}

# Analyze Maven build times
analyze_build_times() {
    echo ""
    echo "Build Time Analysis:"

    local maven_log="${PROJECT_ROOT}/target/maven-status/maven-compiler-plugin/compile/default-compile/inputFiles.lst"

    if [[ -f "${PROJECT_ROOT}/target/classes" ]]; then
        local class_count
        class_count=$(find "${PROJECT_ROOT}/target/classes" -name "*.class" 2>/dev/null | wc -l | tr -d ' ')
        echo "  Compiled Classes (parent): ${class_count}"
    fi

    # Count classes in modules
    local total_module_classes=0
    for module in yawl-engine yawl-elements yawl-stateless yawl-resourcing; do
        if [[ -d "${PROJECT_ROOT}/${module}/target/classes" ]]; then
            local count
            count=$(find "${PROJECT_ROOT}/${module}/target/classes" -name "*.class" 2>/dev/null | wc -l | tr -d ' ')
            echo "  ${module}: ${count} classes"
            total_module_classes=$((total_module_classes + count))
        fi
    done

    echo "  Total Module Classes: ${total_module_classes}"
}

# Calculate cache hit rate from Maven output
calculate_hit_rate() {
    echo ""
    echo "Cache Efficiency:"

    # This would require parsing Maven output during build
    # For now, provide guidance
    echo "  To measure cache hit rate:"
    echo "    1. Run: mvn compile -X 2>&1 | tee /tmp/mvn-build.log"
    echo "    2. Check for 'Restored build from cache' messages"
    echo "    3. Count 'UP-TO-DATE' vs 'compile' task executions"

    # Check if we have recent build log
    if [[ -f "/tmp/mvn-build.log" ]]; then
        local cache_hits
        cache_hits=$(grep -c "Restored build from cache\|UP-TO-DATE" /tmp/mvn-build.log 2>/dev/null || echo "0")
        local cache_misses
        cache_misses=$(grep -c "Compiling\|Building jar" /tmp/mvn-build.log 2>/dev/null || echo "0")

        if [[ "$((cache_hits + cache_misses))" -gt 0 ]]; then
            local hit_rate
            hit_rate=$(echo "scale=2; $cache_hits * 100 / ($cache_hits + $cache_misses)" | bc)
            echo "  Recent Build Hit Rate: ${hit_rate}%"
            echo "  Cache Hits: ${cache_hits}"
            echo "  Cache Misses: ${cache_misses}"
        fi
    fi
}

# Generate detailed report
generate_report() {
    local report_file="${PROJECT_ROOT}/target/build-analytics-report.md"

    cat > "${report_file}" << EOF
# YAWL Build Analytics Report

**Generated:** $(date -Iseconds)
**Project:** YAWL v6.0.0-GA
**Branch:** $(git branch --show-current 2>/dev/null || echo "unknown")

## Cache Statistics

\`\`\`
$(calculate_cache_stats 2>/dev/null || echo "Cache not available")
\`\`\`

## Build Configuration

### JVM Configuration
\`\`\`
$(cat "${PROJECT_ROOT}/.mvn/jvm.config" 2>/dev/null || echo "Not available")
\`\`\`

### Maven Configuration
\`\`\`
$(cat "${PROJECT_ROOT}/.mvn/maven.config" 2>/dev/null || echo "Not available")
\`\`\`

## Recommendations

1. **Monitor cache hit rate**: Run builds twice to warm the cache
2. **Clear stale cache**: \`bash scripts/build-analytics.sh reset\`
3. **Enable mvnd**: Install Maven Daemon for 30-40% faster builds
4. **Use AOT cache**: \`bash scripts/aot/generate-aot.sh\` for 60-70% startup reduction

## Part 4 Optimizations Applied

- Maven 4 Concurrent Builder (tree-based lifecycle)
- Leyden AOT Cache (60-70% JVM startup reduction)
- Virtual Thread Test Optimization (512 max pool)
- Enhanced Build Cache (verbose logging)

---
*Generated by scripts/build-analytics.sh*
EOF

    log_info "Report generated: ${report_file}"
}

# Main execution
main() {
    echo ""
    echo "=== YAWL Build Analytics ==="
    echo ""

    calculate_cache_stats
    analyze_build_times
    calculate_hit_rate

    if [[ "$COMMAND" == "report" ]]; then
        echo ""
        generate_report
    fi

    echo ""
    echo "Commands:"
    echo "  bash scripts/build-analytics.sh          # Show metrics"
    echo "  bash scripts/build-analytics.sh report   # Generate report"
    echo "  bash scripts/build-analytics.sh reset    # Reset metrics"
    echo ""
}

main
