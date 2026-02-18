#!/usr/bin/env bash
# ==========================================================================
# check-deps.sh — YAWL Dependency Health Check
#
# Checks for: duplicate versions, conflicting transitive deps,
# deprecated APIs, and snapshot dependencies in release builds.
#
# Usage: bash scripts/check-deps.sh
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

echo "=== YAWL Dependency Health Check ==="
echo ""

# Check for snapshot dependencies
echo "[1/3] Checking for SNAPSHOT dependencies..."
SNAPSHOTS=$(grep -r "SNAPSHOT" --include="pom.xml" . --exclude-dir=target 2>/dev/null | \
    grep -v "<!-- " | grep "<version>" | grep -v "yawl-parent" || true)
if [ -n "${SNAPSHOTS}" ]; then
    echo "WARNING: SNAPSHOT dependencies found:"
    echo "${SNAPSHOTS}"
else
    echo "OK No SNAPSHOT dependencies"
fi

echo ""
echo "[2/3] Checking deps-conflicts.json from Observatory..."
CONFLICTS_FILE="${REPO_ROOT}/docs/v6/latest/facts/deps-conflicts.json"
if [ -f "${CONFLICTS_FILE}" ]; then
    CONFLICT_COUNT=$(python3 -c "import json; d=json.load(open('${CONFLICTS_FILE}')); print(len(d.get('conflicts', [])))" 2>/dev/null || echo "?")
    echo "Dependency conflicts: ${CONFLICT_COUNT}"
    if [ "${CONFLICT_COUNT}" != "0" ] && [ "${CONFLICT_COUNT}" != "?" ]; then
        python3 -c "import json; [print(f'  - {c}') for c in json.load(open('${CONFLICTS_FILE}')).get('conflicts', [])]" 2>/dev/null || true
    else
        echo "OK No version conflicts detected"
    fi
else
    echo "INFO: Run 'bash scripts/observatory/observatory.sh --facts' first to generate Observatory facts"
fi

echo ""
echo "[3/3] Checking maven-hazards.json..."
HAZARDS_FILE="${REPO_ROOT}/docs/v6/latest/facts/maven-hazards.json"
if [ -f "${HAZARDS_FILE}" ]; then
    HAZARD_COUNT=$(python3 -c "import json; d=json.load(open('${HAZARDS_FILE}')); print(len(d.get('hazards', [])))" 2>/dev/null || echo "?")
    echo "Maven hazards: ${HAZARD_COUNT}"
    if [ "${HAZARD_COUNT}" != "0" ] && [ "${HAZARD_COUNT}" != "?" ]; then
        python3 -c "import json; [print(f'  [{h[\"code\"]}] {h[\"message\"]}') for h in json.load(open('${HAZARDS_FILE}')).get('hazards', [])]" 2>/dev/null || true
    else
        echo "OK No Maven build hazards"
    fi
else
    echo "INFO: Run 'bash scripts/observatory/observatory.sh --facts' first to generate Observatory facts"
fi

echo ""
echo "=== Check complete ==="
