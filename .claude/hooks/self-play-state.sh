#!/bin/bash
# Outputs live loop state as context for Claude
# Called by SessionStart hook
# Exit codes: 0 = success (always, this is informational)

set -euo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

echo "=== YAWL SELF-PLAY LOOP — SESSION START STATE ==="
echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo ""

# QLever status
QLever_URL="${QLEVER_URL:-http://localhost:7001}"
if curl -sf "${QLever_URL}/sparql" \
   -d "query=SELECT (1 AS ?n) WHERE {}" > /dev/null 2>&1; then
  echo "QLever: RUNNING at ${QLever_URL}"

  # NativeCall count
  N=$(curl -s "${QLever_URL}/sparql" \
    -d "query=SELECT (COUNT(*) AS ?n) WHERE { ?x a <https://bridgecore.io/vocab#NativeCall> }" \
    2>/dev/null | grep -o '"value":"[0-9]*"' | grep -o '[0-9]*' || echo "0")
  echo "NativeCall triples: ${N:-QUERY FAILED}"
  if [ "${N:-0}" -lt 86 ] && [ "${N:-0}" -gt 0 ]; then
    echo "  WARNING: Need 86+, have ${N:-0}"
  elif [ "${N:-0}" -eq 0 ]; then
    echo "  WARNING: No NativeCall triples loaded — run gen-ttl and reload"
  fi

  # Composition count
  C=$(curl -s "${QLever_URL}/sparql" \
    -d "query=SELECT (COUNT(*) AS ?n) WHERE { ?x a <https://bridgecore.io/vocab#CapabilityPipeline> }" \
    2>/dev/null | grep -o '"value":"[0-9]*"' | grep -o '[0-9]*' || echo "0")
  echo "CapabilityPipeline triples: ${C:-QUERY FAILED}"

  # Last conformance score
  SCORE=$(curl -s "${QLever_URL}/sparql" \
    -d "query=SELECT ?s WHERE { ?r a <https://yawl.io/sim#SimulationRun> ; <https://yawl.io/sim#conformanceScore> ?s } ORDER BY DESC(?s) LIMIT 1" \
    2>/dev/null | grep -o '"value":"[0-9.]*"' | grep -o '[0-9.]*' | head -1 || echo "")
  if [ -n "${SCORE:-}" ]; then
    echo "Last conformance score: ${SCORE}"
  else
    echo "Last conformance score: NONE (loop has not run)"
  fi

  # Gap count
  GAPS=$(curl -s "${QLever_URL}/sparql" \
    -d "query=SELECT (COUNT(*) AS ?n) WHERE { ?x a <https://yawl.io/sim#CapabilityGap> }" \
    2>/dev/null | grep -o '"value":"[0-9]*"' | grep -o '[0-9]*' || echo "0")
  echo "Open capability gaps: ${GAPS:-QUERY FAILED}"
else
  echo "QLever: NOT RUNNING at ${QLever_URL}"
  echo ""
  echo "CRITICAL: QLever is not running. All SPARQL queries will fail."
  echo "Start QLever first: docker-compose up -d qlever OR ./scripts/qlever-start.sh"
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
echo "6. QLever must return JSON (not HTML error) for SPARQL queries"
echo "=================================================="

exit 0
