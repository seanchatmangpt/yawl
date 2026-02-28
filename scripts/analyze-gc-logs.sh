#!/bin/bash
#
# GC Log Analyzer for YAWL Performance Profiling
# Parses JVM GC logs and produces human-readable summary
#
# Usage:
#   ./analyze-gc-logs.sh gc-profile-*.log
#

set -euo pipefail

if [ $# -eq 0 ]; then
    echo "Usage: $0 <gc-log-file>"
    exit 1
fi

GC_LOG="$1"

if [ ! -f "$GC_LOG" ]; then
    echo "Error: GC log not found: $GC_LOG"
    exit 1
fi

echo "=========================================="
echo "GC Log Analysis: $GC_LOG"
echo "=========================================="
echo ""

# Count GC events
echo "GC Event Summary:"
TOTAL_GC=$(grep -c "GC(" "$GC_LOG" 2>/dev/null || echo "0")
echo "  Total GC events: $TOTAL_GC"

# Count Full GC vs Young GC (heuristic)
FULL_GC=$(grep -c "\[Full GC" "$GC_LOG" 2>/dev/null || echo "0")
YOUNG_GC=$((TOTAL_GC - FULL_GC))
echo "  Full GC events: $FULL_GC"
echo "  Young GC events: $YOUNG_GC"
echo ""

# GC Pause Times
echo "GC Pause Time Statistics:"

# Extract pause times (format: "GC(ConcurrentStart) 0.123ms")
PAUSES=$(grep -oP "GC\([^)]*\) \K[0-9.]+(?=ms)" "$GC_LOG" 2>/dev/null | sort -n || echo "")

if [ -z "$PAUSES" ]; then
    echo "  Could not extract pause times from log"
else
    COUNT=$(echo "$PAUSES" | wc -l)
    SUM=$(echo "$PAUSES" | awk '{s+=$1} END {print s}')
    AVG=$(echo "scale=2; $SUM / $COUNT" | bc 2>/dev/null || echo "N/A")
    MIN=$(echo "$PAUSES" | head -1)
    P50=$(echo "$PAUSES" | awk '{a[NR]=$1} END {n=NR; if(n%2==0) print (a[n/2]+a[n/2+1])/2; else print a[(n+1)/2]}' 2>/dev/null || echo "N/A")
    P95=$(echo "$PAUSES" | awk '{a[NR]=$1} END {n=NR; idx=int(n*0.95); print a[idx]}' 2>/dev/null || echo "N/A")
    P99=$(echo "$PAUSES" | awk '{a[NR]=$1} END {n=NR; idx=int(n*0.99); print a[idx]}' 2>/dev/null || echo "N/A")
    MAX=$(echo "$PAUSES" | tail -1)
    
    echo "  Count: $COUNT"
    echo "  Min: ${MIN} ms"
    echo "  p50: ${P50} ms"
    echo "  p95: ${P95} ms"
    echo "  p99: ${P99} ms"
    echo "  Max: ${MAX} ms"
    echo "  Avg: ${AVG} ms"
fi
echo ""

# Heap Statistics
echo "Heap Statistics (sample from end of log):"
HEAP_SAMPLES=$(grep "Heap:" "$GC_LOG" 2>/dev/null | tail -10 || echo "")

if [ -z "$HEAP_SAMPLES" ]; then
    echo "  No heap statistics found"
else
    echo "$HEAP_SAMPLES"
fi
echo ""

# String Deduplication
echo "String Deduplication Statistics:"
DEDUP=$(grep "String Dedup\|String dedup" "$GC_LOG" 2>/dev/null | tail -5 || echo "")

if [ -z "$DEDUP" ]; then
    echo "  No deduplication statistics found"
else
    echo "$DEDUP"
fi
echo ""

# Tenuring Distribution
echo "Object Tenuring Distribution (sample):"
TENURING=$(grep "Tenuring Distribution\|Age " "$GC_LOG" 2>/dev/null | tail -5 || echo "")

if [ -z "$TENURING" ]; then
    echo "  No tenuring statistics found"
else
    echo "$TENURING"
fi
echo ""

# Timeline Summary
echo "GC Timeline (first 10 events):"
grep "^\[" "$GC_LOG" 2>/dev/null | head -10 || echo "No timeline data"
echo ""

echo "=========================================="
echo "Analysis Complete"
