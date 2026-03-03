#!/bin/bash
set -e

echo "=== YAWL Self-Play Loop v3.0 Final Gate Check ==="
echo "Running final verification checks..."

# Gate 1: QLever running
echo "Gate 1: Checking QLever is running..."
if ! curl -sf http://localhost:7001/sparql -d "query=SELECT (1 AS ?n) WHERE {}" > /dev/null; then
    echo "❌ QLever is not running at http://localhost:7001"
    exit 1
fi
echo "✅ QLever is running"

# Gate 2: 86+ NativeCall triples
echo "Gate 2: Checking NativeCall triples..."
N=$(sparql --query "SELECT (COUNT(*) AS ?count) WHERE { ?s a <http://www.w3.org/2002/07/owl#ObjectProperty> ; <http://www.w3.org/2002/07/owl#inverseOf> ?p . FILTER(STRENDS(STR(?s), 'NativeCall')) }" | grep -o '[0-9]*')
if [ -z "$N" ]; then N=0; fi
echo "NativeCall triples found: $N"
if [ "$N" -lt 86 ]; then
    echo "❌ Expected at least 86 NativeCall triples, got $N"
    exit 1
fi
echo "✅ NativeCall triples count ($N) >= 86"

# Gate 3: 100+ compositions
echo "Gate 3: Checking composition count..."
C=$(get_composition_count 2>/dev/null || echo "0")
echo "Compositions found: $C"
if [ "$C" -lt 100 ]; then
    echo "❌ Expected at least 100 compositions, got $C"
    exit 1
fi
echo "✅ Composition count ($C) >= 100"

# Gate 4: PI OCEL exists with 50+ events
echo "Gate 4: Checking PI OCEL file..."
PI_FILE=$(ls /Users/sac/yawl/sim-output/pi-*.ocel 2>/dev/null | tail -1)
if [ -z "$PI_FILE" ]; then
    echo "❌ No PI OCEL file found in /Users/sac/yawl/sim-output/"
    exit 1
fi
echo "PI OCEL file: $PI_FILE"

EVENTS=$(python3 -c "import json; d=json.load(open('$PI_FILE')); print(len(d.get('ocel:events',{})))" 2>/dev/null || echo "0")
echo "Events in PI OCEL: $EVENTS"
if [ "$EVENTS" -lt 50 ]; then
    echo "❌ Expected at least 50 events in PI OCEL, got $EVENTS"
    exit 1
fi
echo "✅ PI OCEL has $EVENTS events (>= 50)"

# Gate 5: Conformance score in QLever
echo "Gate 5: Checking conformance score..."
SCORE=$(sparql --query "SELECT ?score WHERE { ?run a <https://yawl.io/sim#SimulationRun> ; <https://yawl.io/sim#conformanceScore> ?score } ORDER BY DESC(?run) LIMIT 1" | grep -o '[0-9.]*')
if [ -z "$SCORE" ]; then
    echo "❌ No conformance score found in QLever"
    exit 1
fi
echo "Latest conformance score: $SCORE"
echo "✅ Conformance score ($SCORE) found in QLever"

# Gate 6: THREE ITERATIONS with strictly increasing composition count
echo "Gate 6: Running three iterations with strictly increasing composition count..."
echo "This will take approximately 15-30 minutes..."

# Save current state
C0=$(get_composition_count)
echo "Initial composition count (C0): $C0"

# Run first iteration
echo "Running iteration 1/3..."
if ! ggen generate; then
    echo "❌ First iteration failed"
    exit 1
fi
C1=$(get_composition_count)
echo "Composition count after iteration 1 (C1): $C1"

# Run second iteration
echo "Running iteration 2/3..."
if ! ggen generate; then
    echo "❌ Second iteration failed"
    exit 1
fi
C2=$(get_composition_count)
echo "Composition count after iteration 2 (C2): $C2"

# Run third iteration
echo "Running iteration 3/3..."
if ! ggen generate; then
    echo "❌ Third iteration failed"
    exit 1
fi
C3=$(get_composition_count)
echo "Composition count after iteration 3 (C3): $C3"

# Verify strict increase: C1 > C0, C2 > C1, C3 > C2
echo ""
echo "=== Composition Count Analysis ==="
echo "C0: $C0"
echo "C1: $C1 (Δ: +$((C1 - C0)))"
echo "C2: $C2 (Δ: +$((C2 - C1)))"
echo "C3: $C3 (Δ: +$((C3 - C2)))"
echo "Total increase: +$((C3 - C0))"

if [ "$C1" -le "$C0" ]; then
    echo "❌ C1 ($C1) must be > C0 ($C0)"
    exit 1
fi
if [ "$C2" -le "$C1" ]; then
    echo "❌ C2 ($C2) must be > C1 ($C1)"
    exit 1
fi
if [ "$C3" -le "$C2" ]; then
    echo "❌ C3 ($C3) must be > C2 ($C2)"
    exit 1
fi

echo "✅ All three iterations show strictly increasing composition count"
echo "✅ THE ONE INVARIANT: C1 > C0, C2 > C1, C3 > C2"

echo ""
echo "=== ALL GATES PASSED ==="
echo "YAWL Self-Play Loop v3.0 verification complete!"
echo ""
echo "Final Composition Count Progression:"
echo "  C0: $C0"
echo "  C1: $C1"
echo "  C2: $C2"
echo "  C3: $C3"
echo ""
echo "Total increase: $((C3 - C0)) compositions"
echo "Average increase per iteration: $(((C3 - C0) / 3)) compositions"
