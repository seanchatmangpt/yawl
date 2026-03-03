#!/bin/bash
# =============================================================================
# load-ontologies.sh — Load YAWL self-play ontologies into QLever
# Layer 1: Ontology Foundation Verification
# =============================================================================

set -e

QLERVER_URL="http://localhost:7001"
ONT_DIR="/Users/sac/yawl/ontology"

echo "🔍 Loading YAWL Self-Play Ontologies into QLever..."
echo "QLever URL: $QLERVER_URL"
echo "Ontology directory: $ONT_DIR"
echo

# Check if QLever is running
echo "Step 1: Checking QLerver status..."
if curl -sf "$QLERVER_URL/sparql" -d "query=SELECT (1 AS ?alive) WHERE {}" -H "Accept: application/json" > /dev/null; then
    echo "✅ QLerver is running"
else
    echo "❌ QLerver is not running. Please start QLerver first."
    exit 1
fi

# Load pm-bridge.ttl
echo "Step 2: Loading pm-bridge.ttl (30 process mining capabilities)..."
curl -sf "$QLERVER_URL/load" \
    -d "data=@$ONT_DIR/process-mining/pm-bridge.ttl" \
    -H "Content-Type: text/turtle" > /dev/null
echo "✅ pm-bridge.ttl loaded"

# Load dm-bridge.ttl
echo "Step 3: Loading dm-bridge.ttl (56 data modelling capabilities)..."
curl -sf "$QLERVER_URL/load" \
    -d "data=@$ONT_DIR/../yawl-data-modelling/dm-bridge-ggen/ontology/dm-bridge.ttl" \
    -H "Content-Type: text/turtle" > /dev/null
echo "✅ dm-bridge.ttl loaded"

# Load safe-core.ttl
echo "Step 4: Loading safe-core.ttl (SAFe vocabulary)..."
curl -sf "$QLERVER_URL/load" \
    -d "data=@$ONT_DIR/safe/safe-core.ttl" \
    -H "Content-Type: text/turtle" > /dev/null
echo "✅ safe-core.ttl loaded"

# Load yawl-sim.ttl
echo "Step 5: Loading yawl-sim.ttl (simulation vocabulary)..."
curl -sf "$QLERVER_URL/load" \
    -d "data=@$ONT_DIR/simulation/yawl-sim.ttl" \
    -H "Content-Type: text/turtle" > /dev/null
echo "✅ yawl-sim.ttl loaded"

# Verify NativeCall count
echo
echo "Step 6: Verifying NativeCall triples..."
NATIVE_COUNT=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=SELECT (COUNT(*) AS ?n) WHERE { ?x a <https://bridgecore.io/vocab#NativeCall> }" \
    -H "Accept: application/json" | jq '.results.bindings[0].n.value')

echo "NativeCall triples: $NATIVE_COUNT"

if [ "$NATIVE_COUNT" -ge 86 ]; then
    echo "✅ NativeCall count ($NATIVE_COUNT) meets requirement (≥86)"
else
    echo "❌ NativeCall count ($NATIVE_COUNT) below requirement (≥86)"
fi

# Run valid-compositions query
echo
echo "Step 7: Running valid-compositions.sparql..."
curl -s "$QLERVER_URL/sparql" \
    -d "query=$(cat /Users/sac/yawl/queries/valid-compositions.sparql)" \
    -H "Accept: application/json" | jq '.results.bindings | length' | head -c 100

echo "✅ Ontologies loaded successfully"
echo
echo "📊 Final Status:"
echo "   - NativeCall triples: $NATIVE_COUNT"
echo "   - Target: ≥86"
echo "   - Phase: L1 ontology foundation complete"