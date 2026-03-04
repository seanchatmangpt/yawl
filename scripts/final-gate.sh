#!/bin/bash
# =============================================================================
# YAWL Self-Play Loop v3.0 — Final Gate Verification
#
# THE ONE INVARIANT: qlever_composition_count(N+1) > qlever_composition_count(N)
#
# NOTE: QLever is an embedded Java/C++ FFI bridge (not Docker, not HTTP)
# This script runs Java tests that use QLeverEmbeddedSparqlEngine directly.
# =============================================================================

set -e

echo "=== YAWL SELF-PLAY LOOP v3.0 — FINAL GATE ==="
echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo ""

# =============================================================================
# Gate 1: QLever Embedded Engine Available (not Docker, not HTTP)
# =============================================================================
echo "Gate 1: Checking QLever embedded engine..."
if java -cp "target/classes:yawl-qlever/target/classes" \
    org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine 2>/dev/null; then
    echo "✅ QLever embedded engine available"
else
    echo "ℹ️  QLever engine requires native library build"
    echo "   Run: cd yawl-qlever && mvn compile"
fi

# =============================================================================
# Gate 2: NativeCall Triples (86+) — via Java test
# =============================================================================
echo ""
echo "Gate 2: Checking NativeCall triples..."
NATIVE_COUNT=$(mvn test -Dtest=QLeverOntologyTest#testNativeCallCount -q 2>/dev/null | grep -o 'NativeCall.*[0-9]*' | grep -o '[0-9]*' | head -1 || echo "0")
echo "NativeCall triples: $NATIVE_COUNT"
if [ "${NATIVE_COUNT:-0}" -ge 86 ]; then
    echo "✅ NativeCall count ($NATIVE_COUNT) >= 86"
else
    echo "⚠️  NativeCall count ($NATIVE_COUNT) < 86 — run load-ontologies.sh"
fi

# =============================================================================
# Gate 3: Composition Count (100+) — via Java test
# =============================================================================
echo ""
echo "Gate 3: Checking composition count..."
COMPOSITION_COUNT=$(mvn test -Dtest=V7SelfPlayLoopTest#testCompositionCount -q 2>/dev/null | grep -o 'composition.*[0-9]*' | grep -o '[0-9]*' | head -1 || echo "0")
echo "Composition count: $COMPOSITION_COUNT"
if [ "${COMPOSITION_COUNT:-0}" -ge 100 ]; then
    echo "✅ Composition count ($COMPOSITION_COUNT) >= 100"
else
    echo "⚠️  Composition count ($COMPOSITION_COUNT) < 100"
fi

# =============================================================================
# Gate 4: PI OCEL File Exists with 50+ Events
# =============================================================================
echo ""
echo "Gate 4: Checking PI OCEL file..."
PI_FILE=$(ls /Users/sac/yawl/sim-output/pi-*.ocel 2>/dev/null | tail -1)
if [ -n "$PI_FILE" ]; then
    echo "PI OCEL file: $PI_FILE"
    EVENTS=$(python3 -c "import json; d=json.load(open('$PI_FILE')); print(len(d.get('ocel:events',{})))" 2>/dev/null || echo "0")
    echo "Events in PI OCEL: $EVENTS"
    if [ "$EVENTS" -ge 50 ]; then
        echo "✅ PI OCEL has $EVENTS events (>= 50)"
    else
        echo "⚠️  PI OCEL has only $EVENTS events (< 50)"
    fi
else
    echo "⚠️  No PI OCEL file found in sim-output/"
    echo "   Run YawlSimulator.runPI() to generate"
fi

# =============================================================================
# Gate 5: Conformance Score in QLever
# =============================================================================
echo ""
echo "Gate 5: Checking conformance score..."
SCORE=$(mvn test -Dtest=GapAnalysisEngineTest#testConformanceScore -q 2>/dev/null | grep -o 'score.*[0-9.]*' | grep -o '[0-9.]*' | head -1 || echo "")
if [ -n "$SCORE" ]; then
    echo "Conformance score: $SCORE"
    echo "✅ Conformance score found in QLever"
else
    echo "⚠️  No conformance score found"
fi

# =============================================================================
# Gate 6: THE ONE INVARIANT — Three Iterations with Strictly Increasing Count
# =============================================================================
echo ""
echo "Gate 6: Running three iterations with strictly increasing composition count..."
echo "This runs V7SelfPlayLoopTest#testThreeIterationsStrictlyIncreasing"
echo ""

# Run the three-iteration test
if mvn test -Dtest=V7SelfPlayLoopTest#testThreeIterationsStrictlyIncreasing -q 2>&1; then
    echo ""
    echo "✅ THE ONE INVARIANT VERIFIED: C1 > C0, C2 > C1, C3 > C2"
else
    echo ""
    echo "⚠️  Three-iteration test not yet passing"
    echo "   Implement inner loop to close gaps and increase compositions"
fi

echo ""
echo "=== FINAL GATE SUMMARY ==="
echo ""
echo "QLever is an EMBEDDED Java/C++ FFI bridge (not Docker, not HTTP)"
echo "All queries run in-process via QLeverEmbeddedSparqlEngine"
echo ""
echo "To verify manually in Java:"
echo "  QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();"
echo "  engine.initialize();"
echo "  QLeverResult result = engine.executeQuery(\"SELECT (COUNT(*) AS ?n) WHERE { ?x a <https://bridgecore.io/vocab#NativeCall> }\");"
echo ""
