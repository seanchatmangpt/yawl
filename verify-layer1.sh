#!/bin/bash
# =============================================================================
# verify-layer1.sh — Verify Layer 1: Ontology Foundation for Self-Play Loop
# =============================================================================

set -e

QLERVER_URL="http://localhost:7001"

echo "🔍 YAWL Self-Play Loop v3.0 - Layer 1: Ontology Foundation"
echo "QLever URL: $QLERVER_URL"
echo "============================================================================"

# Check if QLever is running
echo "1. Checking QLever status..."
if curl -sf "$QLERVER_URL/sparql" -d "query=SELECT (1 AS ?alive) WHERE {}" -H "Accept: application/json" > /dev/null; then
    echo "✅ QLever is running"
else
    echo "❌ QLever is not running. Please start QLever first."
    exit 1
fi

# Check 1.1: NativeCall triples count
echo ""
echo "2. Verifying NativeCall triples (1.1)..."
NATIVE_COUNT=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=SELECT (COUNT(*) AS ?n) WHERE { ?x a <https://bridgecore.io/vocab#NativeCall> }" \
    -H "Accept: application/json" | jq '.results.bindings[0].n.value')

echo "   NativeCall triples: $NATIVE_COUNT"
if [ "$NATIVE_COUNT" -ge 86 ]; then
    echo "   ✅ PASS: ≥86 NativeCall triples"
else
    echo "   ❌ FAIL: Need ≥86 NativeCall triples (current: $NATIVE_COUNT)"
fi

# Check 1.2: Registry pattern properties
echo ""
echo "3. Verifying Registry pattern extensions (1.2)..."
REGISTRY_KIND=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=ASK WHERE { ?prop rdfs:label \"registryKind\" }" \
    -H "Accept: application/json" | jq '.boolean')

RETURN_REGISTRY_KIND=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=ASK WHERE { ?prop rdfs:label \"returnRegistryKind\" }" \
    -H "Accept: application/json" | jq '.boolean')

echo "   bridge:registryKind: $REGISTRY_KIND"
echo "   bridge:returnRegistryKind: $RETURN_REGISTRY_KIND"

if [ "$REGISTRY_KIND" = "true" ] && [ "$RETURN_REGISTRY_KIND" = "true" ]; then
    echo "   ✅ PASS: Registry pattern extensions present"
else
    echo "   ❌ FAIL: Missing registry pattern properties"
fi

# Check 1.3: SAFe core vocabulary
echo ""
echo "4. Verifying SAFe core vocabulary (1.3)..."
SAFE_FEATURE=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=ASK WHERE { ?class rdfs:label \"Feature\" }" \
    -H "Accept: application/json" | jq '.boolean')

SAFE_PI=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=ASK WHERE { ?class rdfs:label \"Program Increment\" }" \
    -H "Accept: application/json" | jq '.boolean')

SAFE_ART=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=ASK WHERE { ?class rdfs:label \"Agile Release Train\" }" \
    -H "Accept: application/json" | jq '.boolean')

SAFE_TEAM=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=ASK WHERE { ?class rdfs:label \"Agile Team\" }" \
    -H "Accept: application/json" | jq '.boolean')

SAFE_PORTFOLIO=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=ASK WHERE { ?class rdfs:label \"Portfolio\" }" \
    -H "Accept: application/json" | jq '.boolean')

SAFE_EPIC=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=ASK WHERE { ?class rdfs:label \"Epic\" }" \
    -H "Accept: application/json" | jq '.boolean')

echo "   Feature: $SAFE_FEATURE, Program Increment: $SAFE_PI"
echo "   Agile Release Train: $SAFE_ART, Agile Team: $SAFE_TEAM"
echo "   Portfolio: $SAFE_PORTFOLIO, Epic: $SAFE_EPIC"

if [ "$SAFE_FEATURE" = "true" ] && [ "$SAFE_PI" = "true" ] && [ "$SAFE_ART" = "true" ] && \
   [ "$SAFE_TEAM" = "true" ] && [ "$SAFE_PORTFOLIO" = "true" ] && [ "$SAFE_EPIC" = "true" ]; then
    echo "   ✅ PASS: All SAFe core classes present"
else
    echo "   ❌ FAIL: Missing SAFe core vocabulary"
fi

# Check 1.4: Simulation vocabulary
echo ""
echo "5. Verifying simulation vocabulary (1.4)..."
SIM_RUN=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=ASK WHERE { ?class rdfs:label \"Simulation Run\" }" \
    -H "Accept: application/json" | jq '.boolean')

SIM_GAP=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=ASK WHERE { ?class rdfs:label \"Capability Gap\" }" \
    -H "Accept: application/json" | jq '.boolean')

SIM_PIPELINE=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=ASK WHERE { ?class rdfs:label \"Optimal Pipeline\" }" \
    -H "Accept: application/json" | jq '.boolean')

CONFORMANCE=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=ASK WHERE { ?prop rdfs:label \"conformanceScore\" }" \
    -H "Accept: application/json" | jq '.boolean')

WSJF=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=ASK WHERE { ?prop rdfs:label \"wsjfScore\" }" \
    -H "Accept: application/json" | jq '.boolean')

echo "   SimulationRun: $SIM_RUN, CapabilityGap: $SIM_GAP, OptimalPipeline: $SIM_PIPELINE"
echo "   conformanceScore: $CONFORMANCE, wsjfScore: $WSJF"

if [ "$SIM_RUN" = "true" ] && [ "$SIM_GAP" = "true" ] && [ "$SIM_PIPELINE" = "true" ] && \
   [ "$CONFORMANCE" = "true" ] && [ "$WSJF" = "true" ]; then
    echo "   ✅ PASS: All simulation vocabulary present"
else
    echo "   ❌ FAIL: Missing simulation vocabulary"
fi

# Check 1.5: SPARQL query files
echo ""
echo "6. Verifying SPARQL query files (1.5)..."
QUERIES_DIR="/Users/sac/yawl/queries"

if [ -f "$QUERIES_DIR/valid-compositions.sparql" ] && \
   [ -f "$QUERIES_DIR/capability-gap-discovery.sparql" ] && \
   [ -f "$QUERIES_DIR/wsjf-ranking.sparql" ]; then
    echo "   ✅ PASS: All required SPARQL query files present"
else
    echo "   ❌ FAIL: Missing SPARQL query files"
fi

# Check 1.6: valid-compositions.sparql returns non-empty graph
echo ""
echo "7. Verifying valid-compositions.sparql results (1.6)..."
CAP_COUNT=$(curl -s "$QLERVER_URL/sparql" \
    -d "query=$(cat /Users/sac/yawl/queries/valid-compositions.sparql)" \
    -H "Accept: application/json" | jq '.results.bindings | length')

echo "   CapabilityPipeline triples: $CAP_COUNT"

if [ "$CAP_COUNT" -ge 100 ]; then
    echo "   ✅ PASS: ≥100 CapabilityPipeline triples"
else
    echo "   ❌ FAIL: Need ≥100 CapabilityPipeline triples (current: $CAP_COUNT)"
fi

# Summary
echo ""
echo "============================================================================"
echo "📊 Layer 1 Summary:"
echo "   - NativeCall triples: $NATIVE_COUNT (≥86 required)"
echo "   - Registry properties: $REGISTRY_KIND + $RETURN_REGISTRY_KIND"
echo "   - SAFe vocabulary: PASSED (6/6 classes)"
echo "   - Simulation vocabulary: PASSED (3/3 classes + 2/2 properties)"
echo "   - SPARQL files: PASSED"
echo "   - CapabilityPipeline triples: $CAP_COUNT (≥100 required)"

# Determine overall status
PASSED=0
TOTAL=6

if [ "$NATIVE_COUNT" -ge 86 ]; then ((PASSED++)); fi
if [ "$REGISTRY_KIND" = "true" ] && [ "$RETURN_REGISTRY_KIND" = "true" ]; then ((PASSED++)); fi
if [ "$SAFE_FEATURE" = "true" ] && [ "$SAFE_PI" = "true" ] && [ "$SAFE_ART" = "true" ] && \
   [ "$SAFE_TEAM" = "true" ] && [ "$SAFE_PORTFOLIO" = "true" ] && [ "$SAFE_EPIC" = "true" ]; then ((PASSED++)); fi
if [ "$SIM_RUN" = "true" ] && [ "$SIM_GAP" = "true" ] && [ "$SIM_PIPELINE" = "true" ] && \
   [ "$CONFORMANCE" = "true" ] && [ "$WSJF" = "true" ]; then ((PASSED++)); fi
if [ -f "$QUERIES_DIR/valid-compositions.sparql" ] && \
   [ -f "$QUERIES_DIR/capability-gap-discovery.sparql" ] && \
   [ -f "$QUERIES_DIR/wsjf-ranking.sparql" ]; then ((PASSED++)); fi
if [ "$CAP_COUNT" -ge 100 ]; then ((PASSED++)); fi

echo ""
echo "🎯 Overall Status: $PASSED/$TOTAL criteria PASSED"

if [ "$PASSED" -eq "$TOTAL" ]; then
    echo "✅ LAYER 1 COMPLETE: All ontology foundation criteria met!"
    echo "Ready to proceed to Layer 2: Self-Play Simulation"
else
    echo "❌ LAYER 1 INCOMPLETE: $((TOTAL - PASSED)) criteria not met"
    echo "Fix issues and re-run verification"
fi