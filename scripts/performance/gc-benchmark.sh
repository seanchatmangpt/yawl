#!/bin/bash
# ==========================================================================
# gc-benchmark.sh - Compare GC performance between G1GC and ZGC
#
# Usage:
#   bash scripts/performance/gc-benchmark.sh [options]
#
# Options:
#   --duration SECONDS   Test duration (default: 60)
#   --heap SIZE          Heap size (default: 4g)
#   --gc TYPE            GC type: g1 or zgc (default: g1)
#   --output DIR         Output directory (default: performance-reports/gc)
#
# Output:
#   GC log files and summary statistics
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Default configuration
DURATION=60
HEAP_SIZE="4g"
GC_TYPE="g1"
OUTPUT_DIR="${PROJECT_ROOT}/performance-reports/gc"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --duration)
            DURATION="$2"
            shift 2
            ;;
        --heap)
            HEAP_SIZE="$2"
            shift 2
            ;;
        --gc)
            GC_TYPE="$2"
            shift 2
            ;;
        --output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --duration SECONDS   Test duration (default: 60)"
            echo "  --heap SIZE          Heap size (default: 4g)"
            echo "  --gc TYPE            GC type: g1 or zgc (default: g1)"
            echo "  --output DIR         Output directory"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Create output directory
mkdir -p "${OUTPUT_DIR}"

# GC configuration
case $GC_TYPE in
    g1)
        GC_FLAGS=(
            -XX:+UseG1GC
            -XX:MaxGCPauseMillis=200
            -XX:G1HeapRegionSize=16m
            -XX:InitiatingHeapOccupancyPercent=45
            -XX:G1MixedGCLiveThresholdPercent=85
            -XX:+G1UseAdaptiveIHOP
            -XX:+UseStringDeduplication
        )
        GC_NAME="G1GC"
        ;;
    zgc)
        GC_FLAGS=(
            -XX:+UseZGC
            -XX:+ZGenerational
            -XX:ZCollectionInterval=5
            -XX:ZAllocationSpikeTolerance=2
            -XX:+UnlockDiagnosticVMOptions
            -XX:+ZProactive
        )
        GC_NAME="ZGC Generational"
        ;;
    *)
        echo "Unknown GC type: $GC_TYPE (use 'g1' or 'zgc')"
        exit 1
        ;;
esac

# Heap configuration
HEAP_FLAGS=(
    -Xms"${HEAP_SIZE}"
    -Xmx"${HEAP_SIZE}"
    -XX:MetaspaceSize=256m
    -XX:MaxMetaspaceSize=512m
)

# GC log configuration
GC_LOG="${OUTPUT_DIR}/gc-${GC_TYPE}-${TIMESTAMP}.log"
GC_LOG_FLAGS=(
    -Xlog:gc*:file="${GC_LOG}":time,uptime,level,tags:filecount=1,filesize=100m
)

# Test class
TEST_CLASS="org.yawlfoundation.yawl.performance.jmh.MemoryUsageBenchmark"

echo "========================================="
echo "  YAWL GC Benchmark"
echo "========================================="
echo "  GC Type:     ${GC_NAME}"
echo "  Heap Size:   ${HEAP_SIZE}"
echo "  Duration:    ${DURATION}s"
echo "  Output:      ${OUTPUT_DIR}"
echo "========================================="

# Build if needed
if [[ ! -f "${PROJECT_ROOT}/target/classes" ]]; then
    echo "Compiling..."
    cd "${PROJECT_ROOT}"
    mvn -T 1.5C compile -q
fi

# Run GC benchmark
echo ""
echo "Starting GC benchmark..."
echo ""

cd "${PROJECT_ROOT}"

# Use a simple allocation test
java "${HEAP_FLAGS[@]}" "${GC_FLAGS[@]}" "${GC_LOG_FLAGS[@]}" \
    -cp "target/classes:target/test-classes:$(mvn dependency:build-classpath -q -DincludeScope=test -Dmdep.outputFile=/dev/stdout)" \
    -e "System.gc(); Thread.sleep(1000); for(int i=0; i<10; i++) { new byte[1024*1024*10]; Thread.sleep(500); } System.gc(); Thread.sleep(1000);" \
    2>&1 || true

# Analyze GC log
if [[ -f "${GC_LOG}" ]]; then
    echo ""
    echo "Analyzing GC log..."
    echo ""
    
    # Count GC events
    YOUNG_GC=$(grep -c "GC\]" "${GC_LOG}" 2>/dev/null || echo "0")
    FULL_GC=$(grep -c "Full GC" "${GC_LOG}" 2>/dev/null || echo "0")
    
    # Extract pause times (simplified)
    PAUSE_TIMES=$(grep -oP 'Pause.*\K[0-9]+\.[0-9]+ms' "${GC_LOG}" 2>/dev/null | head -20 || echo "N/A")
    
    echo "GC Statistics:"
    echo "  Young GC events: ${YOUNG_GC}"
    echo "  Full GC events:  ${FULL_GC}"
    echo ""
    echo "Sample pause times:"
    echo "${PAUSE_TIMES}" | head -10
fi

# Summary
SUMMARY_FILE="${OUTPUT_DIR}/gc-summary-${GC_TYPE}-${TIMESTAMP}.json"
cat > "${SUMMARY_FILE}" << EOF
{
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "gc_type": "${GC_NAME}",
    "heap_size": "${HEAP_SIZE}",
    "duration_seconds": ${DURATION},
    "gc_log": "${GC_LOG}",
    "statistics": {
        "young_gc_count": ${YOUNG_GC:-0},
        "full_gc_count": ${FULL_GC:-0}
    }
}
EOF

echo ""
echo "Summary saved to: ${SUMMARY_FILE}"
echo "GC log saved to:  ${GC_LOG}"
echo ""
echo "========================================="
echo "  Benchmark Complete"
echo "========================================="
