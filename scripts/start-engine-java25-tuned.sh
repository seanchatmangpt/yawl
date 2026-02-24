#!/usr/bin/env bash
# YAWL Engine Startup Script - P4 JVM Tuning for Java 25
#
# P4 MEDIUM - JVM Tuning for Java 25:
#   - G1GC with low-latency targets for workflow engine responsiveness
#   - Virtual thread configuration for inter-service I/O
#   - Metaspace sizing to avoid adaptive-GC overhead on startup
#   - GC logging for production monitoring
#
# Performance targets:
#   - GC pause time: <200ms (p99), target <50ms typical
#   - Full GC frequency: <10 per hour
#   - Engine startup: <60 seconds from JVM start
#   - Heap: 2-4GB tuned for 8GB server (leave headroom for OS, buffers, Metaspace)
#
# Usage:
#   ./scripts/start-engine-java25-tuned.sh [WAR_PATH] [PORT]
#
# Requires Java 25 or later.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
LOG_DIR="${PROJECT_DIR}/logs"
mkdir -p "${LOG_DIR}"

# ─── WAR / port configuration ────────────────────────────────────────────────
WAR_PATH="${1:-${PROJECT_DIR}/build/yawl-engine.war}"
SERVER_PORT="${2:-8080}"

# ─── GC log file ─────────────────────────────────────────────────────────────
GC_LOG="${LOG_DIR}/gc-$(date +%Y%m%d_%H%M%S).log"

# ─── P4: Java 25 JVM tuning flags ────────────────────────────────────────────
#
# HEAP
#   -Xms2g  : start at 2 GB to avoid repeated heap expansion on startup
#   -Xmx4g  : cap at 4 GB; leaves 4 GB for OS + Metaspace + virtual-thread stacks
#
# GC - G1GC (recommended for low-latency workflow engines)
#   -XX:+UseG1GC                  : enable G1GC
#   -XX:MaxGCPauseMillis=200      : target pause goal (G1 best-effort, not a hard cap)
#   -XX:G1HeapRegionSize=16m      : larger regions reduce region fragmentation for
#                                    workflow payloads (XML documents, net data)
#   -XX:InitiatingHeapOccupancyPercent=45 : trigger concurrent marking earlier to
#                                    reduce Full GC risk under steady-state load
#   -XX:G1MixedGCLiveThresholdPercent=85  : more aggressive old-gen reclaim
#   -XX:+G1UseAdaptiveIHOP        : let G1 tune IHOP from observed allocation rates
#
# METASPACE
#   -XX:MetaspaceSize=256m        : pre-size to avoid early GC on class loading
#   -XX:MaxMetaspaceSize=512m     : hard cap prevents runaway class-loader leaks
#
# VIRTUAL THREADS (Java 25)
#   -Djdk.virtualThreadScheduler.parallelism=N  : carrier thread count = vCPU count
#   -Djdk.virtualThreadScheduler.maxPoolSize=256 : max carrier pool (virtual threads
#                                                  themselves are cheap, carriers are not)
#
# THREAD LOCAL ALLOCATION BUFFERS
#   -XX:+ResizeTLAB               : let JVM tune TLAB size per-thread
#   -XX:TLABSize=512k             : initial TLAB size for virtual thread burst allocation
#
# GC LOGGING (production monitoring)
#   -Xlog:gc*:file=${GC_LOG}:time,uptime,level,tags:filecount=5,filesize=20m
#
# ERGONOMICS
#   -XX:+UseStringDeduplication   : deduplicate String instances in old-gen (workflow
#                                    engines hold many repeated task/spec IDs as strings)
#   -XX:+OptimizeStringConcat     : reduces allocation from string concatenation in
#                                    hot workflow logging paths

CPU_COUNT=$(nproc 2>/dev/null || echo 4)
VT_PARALLELISM="${CPU_COUNT}"

JVM_FLAGS=(
    # Heap
    -Xms2g
    -Xmx4g

    # GC - G1GC
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=200
    -XX:G1HeapRegionSize=16m
    -XX:InitiatingHeapOccupancyPercent=45
    -XX:G1MixedGCLiveThresholdPercent=85
    -XX:+G1UseAdaptiveIHOP
    -XX:+UseStringDeduplication

    # Metaspace
    -XX:MetaspaceSize=256m
    -XX:MaxMetaspaceSize=512m

    # TLAB
    -XX:+ResizeTLAB
    -XX:TLABSize=512k

    # Virtual thread scheduler (Java 25)
    "-Djdk.virtualThreadScheduler.parallelism=${VT_PARALLELISM}"
    -Djdk.virtualThreadScheduler.maxPoolSize=256

    # GC logging
    "-Xlog:gc*:file=${GC_LOG}:time,uptime,level,tags:filecount=5,filesize=20m"

    # Diagnostics (disable in extreme latency scenarios)
    -XX:+OptimizeStringConcat

    # Server port
    "-Dserver.port=${SERVER_PORT}"
)

echo "============================================================"
echo " YAWL Engine - P4 Java 25 Tuned Startup"
echo "============================================================"
echo " Java:     $(java -version 2>&1 | head -1)"
echo " CPU:      ${CPU_COUNT} vCPU(s)"
echo " VT pool:  parallelism=${VT_PARALLELISM}, maxPool=256"
echo " Heap:     2g initial / 4g max"
echo " GC log:   ${GC_LOG}"
echo " Port:     ${SERVER_PORT}"
echo "============================================================"

# Verify Java 25+
JAVA_VER=$(java -version 2>&1 | grep -oP '(?<=version ")[^"]+' | head -1)
JAVA_MAJOR=$(echo "${JAVA_VER}" | cut -d'.' -f1)
if [ "${JAVA_MAJOR}" -lt 25 ] 2>/dev/null; then
    echo "WARNING: Java 25+ recommended for virtual thread tuning. Found: ${JAVA_VER}"
fi

exec java "${JVM_FLAGS[@]}" -jar "${WAR_PATH}" "$@"
