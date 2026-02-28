#!/bin/bash
#
# GC Profiling Script for YAWL 1M Case Scale
# Executes GCProfilingTest with optimized ZGC configuration and detailed metrics
#
# Usage:
#   ./gc-profile.sh [options]
#
# Options:
#   --cases N           Target case count (default: 1000000)
#   --duration-hours H  Test duration in hours (default: 1)
#   --heap-min SIZE     Min heap size (default: 2g)
#   --heap-max SIZE     Max heap size (default: 8g)
#   --output DIR        Output directory for GC logs and profiles (default: ./gc-profiles)
#   --help              Show this help
#

set -euo pipefail

# Configuration
CASES=1000000
DURATION_HOURS=1
HEAP_MIN="2g"
HEAP_MAX="8g"
OUTPUT_DIR="./gc-profiles"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
GC_LOG="$OUTPUT_DIR/gc-profile-$TIMESTAMP.log"
PROFILE_OUTPUT="$OUTPUT_DIR/gc-profile-$TIMESTAMP.json"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --cases)
            CASES="$2"
            shift 2
            ;;
        --duration-hours)
            DURATION_HOURS="$2"
            shift 2
            ;;
        --heap-min)
            HEAP_MIN="$2"
            shift 2
            ;;
        --heap-max)
            HEAP_MAX="$2"
            shift 2
            ;;
        --output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --help)
            grep "^#" "$0" | grep -v "^#!/bin/bash" | cut -c 3-
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo "=========================================="
echo "YAWL GC Profiling Test"
echo "=========================================="
echo "Timestamp: $TIMESTAMP"
echo "Target cases: $CASES"
echo "Duration: $DURATION_HOURS hours"
echo "Heap: $HEAP_MIN to $HEAP_MAX"
echo "Output: $OUTPUT_DIR"
echo ""

# ZGC JVM Options
# - UseZGC: ZGC garbage collector (low-latency)
# - UseCompactObjectHeaders: 12 bytes → 8 bytes per object header
# - PrintGCDetails: Detailed GC information
# - PrintGCDateStamps: Timestamps for each GC
# - PrintGCTimeStamps: Relative time for GC events
# - PrintGCApplicationStoppedTime: Time app is stopped for GC
# - PrintStringDeduplicationStatistics: String dedup effectiveness
# - PrintTenuringDistribution: Object aging in young generation
# - XX:+PrintGCMemoryStatistics: Memory after GC
# - XX:ConcGCThreads=N: Concurrent GC threads
# - XX:InitiatingHeapOccupancyPercent=35: Start GC at 35% occupancy

JVM_OPTS=(
    "-Xms$HEAP_MIN"
    "-Xmx$HEAP_MAX"
    "-XX:+UseZGC"
    "-XX:+UseCompactObjectHeaders"
    "-XX:+PrintGCDetails"
    "-XX:+PrintGCDateStamps"
    "-XX:+PrintGCTimeStamps"
    "-Xloggc:$GC_LOG"
    "-XX:+PrintGCApplicationStoppedTime"
    "-XX:+PrintStringDeduplicationStatistics"
    "-XX:+PrintTenuringDistribution"
    "-XX:ConcGCThreads=8"
    "-XX:InitiatingHeapOccupancyPercent=35"
    "-XX:+AlwaysPreTouch"
    "-XX:+UseLargePages"
    "-XX:LargePageSizeInBytes=2m"
    "-XX:-UseBiasedLocking"
    "-Dprof.cases=$CASES"
    "-Dprof.duration.hours=$DURATION_HOURS"
)

echo "JVM Options:"
for opt in "${JVM_OPTS[@]}"; do
    echo "  $opt"
done
echo ""

# Build classpath
cd "$(dirname "$0")/.."

if [ ! -f "target/classes/org/yawlfoundation/yawl/stateless/YStatelessEngine.class" ]; then
    echo "Building YAWL..."
    bash scripts/dx.sh compile > /dev/null 2>&1 || {
        echo "Error: Build failed. Run: bash scripts/dx.sh all"
        exit 1
    }
fi

CLASSPATH="target/classes:yawl-benchmark/target/classes:yawl-benchmark/target/test-classes"

# Add all dependency JARs
if [ -d "target/dependency" ]; then
    CLASSPATH="$CLASSPATH:target/dependency/*"
fi

echo "Starting GC profiling test..."
echo "Output: $PROFILE_OUTPUT"
echo ""

# Run test
java \
    "${JVM_OPTS[@]}" \
    -cp "$CLASSPATH" \
    org.junit.platform.console.ConsoleLauncher \
    --scan-classpath \
    --include-engine=junit-jupiter \
    --select-class=org.yawlfoundation.yawl.benchmark.GCProfilingTest \
    --config junit.jupiter.execution.parallel.enabled=true \
    --config junit.jupiter.execution.parallel.mode.default=concurrent

EXIT_CODE=$?

echo ""
echo "=========================================="
echo "GC Profiling Test Complete"
echo "=========================================="
echo "GC Log: $GC_LOG"
echo "Profile Output: $PROFILE_OUTPUT"
echo "Exit code: $EXIT_CODE"
echo ""

# Parse GC log and show summary
if [ -f "$GC_LOG" ]; then
    echo "GC Statistics Summary:"
    echo ""
    
    # Count GC events
    gc_count=$(grep -c "GC(" "$GC_LOG" 2>/dev/null || echo "0")
    echo "  Total GC events: $gc_count"
    
    # Average pause time
    avg_pause=$(awk '/GC\(.*?\) [0-9.]+ms/ {sum+=$NF; count++} END {if (count>0) printf "%.2f", sum/count; else print "N/A"}' "$GC_LOG" 2>/dev/null || echo "N/A")
    echo "  Average pause: $avg_pause ms"
    
    # Max pause time
    max_pause=$(grep -oP "GC\([^)]*\) \K[0-9.]+(?=ms)" "$GC_LOG" 2>/dev/null | sort -n | tail -1 || echo "N/A")
    echo "  Max pause: $max_pause ms"
    
    # Heap statistics
    final_heap=$(tail -50 "$GC_LOG" 2>/dev/null | grep -oP "Heap: \K[^M]*" | tail -1 || echo "N/A")
    echo "  Final heap: $final_heap"
fi

exit $EXIT_CODE
