#!/bin/bash
# Outputs live loop state as context for Claude
# Called by SessionStart hook
# Exit codes: 0 = success (always, this is informational)
#
# QLever is an EMBEDDED Java 25 Panama FFI engine (NOT Docker, NOT HTTP).
# It requires libqleverjni.so on the system library path or via $QLEVER_NATIVE_LIB.
# SPARQL queries run in-process via QLeverEmbeddedSparqlEngine + QLeverFfiBindings.

set -euo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

echo "=== YAWL SELF-PLAY LOOP — SESSION START STATE ==="
echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo ""

# QLever status — embedded FFI engine, NOT a network/Docker service
echo "QLever: EMBEDDED Java 25 Panama FFI (not Docker, not HTTP)"

# Check 1: Native library (mirrors QLeverFfiBindings.findNativeLibrary() search order)
NATIVE_LIB=""
if [ -n "${QLEVER_NATIVE_LIB:-}" ] && [ -f "${QLEVER_NATIVE_LIB}" ]; then
    NATIVE_LIB="${QLEVER_NATIVE_LIB}"
elif [ -f "/usr/local/lib/libqleverjni.so" ]; then
    NATIVE_LIB="/usr/local/lib/libqleverjni.so"
elif [ -f "/usr/lib/libqleverjni.so" ]; then
    NATIVE_LIB="/usr/lib/libqleverjni.so"
elif [ -f "/usr/local/lib/libqleverjni.dylib" ]; then
    NATIVE_LIB="/usr/local/lib/libqleverjni.dylib"
fi

if [ -n "${NATIVE_LIB}" ]; then
    echo "  Native library (libqleverjni): PRESENT at ${NATIVE_LIB}"
else
    echo "  Native library (libqleverjni): NOT FOUND"
    echo "  Set QLEVER_NATIVE_LIB or install libqleverjni.so to system path"
fi

# Check 2: Compiled Java FFI classes
QLEVER_CLASS="${PROJECT_DIR}/yawl-qlever/target/classes/org/yawlfoundation/yawl/qlever/QLeverEmbeddedSparqlEngine.class"
if [ -f "${QLEVER_CLASS}" ]; then
    echo "  Java FFI classes (yawl-qlever): COMPILED"
else
    echo "  Java FFI classes (yawl-qlever): NOT COMPILED"
    echo "  Run: mvn compile -pl yawl-qlever"
fi

echo ""

# OCEL output state — guard against missing directory (sim-output may not exist)
OCEL_COUNT=0
if [ -d "${PROJECT_DIR}/sim-output" ]; then
  OCEL_COUNT=$(find "${PROJECT_DIR}/sim-output" -name "pi-*.ocel" 2>/dev/null | wc -l | tr -d ' ' || echo 0)
fi
echo "PI OCEL files in sim-output/: ${OCEL_COUNT}"
if [ "${OCEL_COUNT}" -eq 0 ]; then
  echo "  WARNING: YawlSimulator.runPI() has never completed"
fi

# Generated TTL state
TTL_COUNT=$(find "${PROJECT_DIR}" -name "*.ttl" -path "*/generated/*" 2>/dev/null | wc -l | tr -d ' ' || echo 0)
echo "Generated TTL files: ${TTL_COUNT}"

echo ""
echo "=== RULES IN EFFECT ==="
echo "1. A phase is ✅ only when verification command output is quoted"
echo "2. ls/find/grep proves existence only — never proves correctness"
echo "3. Mocks passing ≠ integration working"
echo "4. READY FOR APPROVAL requires invariant script exit 0"
echo "5. Composition count must be STRICTLY greater after loop iteration"
echo "6. QLever is EMBEDDED FFI (not Docker, not HTTP) — use QLeverEmbeddedSparqlEngine via Java tests"
echo "=================================================="

exit 0
