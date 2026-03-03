#!/bin/bash
# Outputs live loop state as context for Claude
# Called by SessionStart hook
# Exit codes: 0 = success (always, this is informational)
#
# IMPORTANT: QLever is an embedded Java/C++ FFI bridge (NOT Docker, NOT HTTP)
# All SPARQL queries run in-process via QLeverEmbeddedSparqlEngine

set -euo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

echo "=== YAWL SELF-PLAY LOOP — SESSION START STATE ==="
echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo ""

# QLever status - check via Java test, NOT curl
# QLever is an embedded FFI engine, not an HTTP service
echo "QLever: EMBEDDED Java/C++ FFI (not Docker, not HTTP)"
echo ""

# Check if QLever embedded engine classes are available
if [ -f "yawl-qlever/target/classes/org/yawlfoundation/yawl/qlever/QLeverEmbeddedSparqlEngine.class" ]; then
    echo "QLever embedded engine: COMPILED"

    # Try to get counts via Java test (non-blocking, 5s timeout)
    NATIVE_COUNT=$(timeout 5 mvn test -Dtest=QLeverOntologyTest#testNativeCallCount -q 2>/dev/null | grep -o 'NativeCall.*[0-9]*' | grep -o '[0-9]*' | head -1 || echo "")
    if [ -n "${NATIVE_COUNT:-}" ]; then
        echo "NativeCall triples: ${NATIVE_COUNT}"
        if [ "${NATIVE_COUNT}" -lt 86 ] && [ "${NATIVE_COUNT}" -gt 0 ]; then
            echo "  WARNING: Need 86+, have ${NATIVE_COUNT}"
        elif [ "${NATIVE_COUNT}" -eq 0 ]; then
            echo "  WARNING: No NativeCall triples loaded — run gen-ttl and reload"
        fi
    else
        echo "NativeCall triples: (run mvn test to query)"
    fi

    # Composition count
    COMPOSITION_COUNT=$(timeout 5 mvn test -Dtest=V7SelfPlayLoopTest#testCompositionCount -q 2>/dev/null | grep -o 'composition.*[0-9]*' | grep -o '[0-9]*' | head -1 || echo "")
    if [ -n "${COMPOSITION_COUNT:-}" ]; then
        echo "Composition count: ${COMPOSITION_COUNT}"
    else
        echo "Composition count: (run mvn test to query)"
    fi
else
    echo "QLever embedded engine: NOT COMPILED"
    echo "  Run: cd yawl-qlever && mvn compile"
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
echo "6. QLever is EMBEDDED FFI (not Docker, not HTTP) — use QLeverEmbeddedSparqlEngine"
echo "=================================================="

exit 0
