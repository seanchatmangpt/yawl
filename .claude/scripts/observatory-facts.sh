#!/bin/bash
# Observatory Facts Wrapper Script
#
# Generates and caches YAWL codebase facts using the Observatory system.
# Facts are re-generated when pom.xml changes (detected via hash tracking).
#
# Observatory extracts metadata about:
#   - Maven modules and dependency structure
#   - Package interdependencies
#   - Test coverage distribution
#   - Code duplication analysis
#   - Shared source locations
#
# Output:
#   Facts written to: docs/v6/latest/
#   Hash cache: .yawl/.dx-state/observatory-pom-hash.txt
#   Receipt: .claude/receipts/observatory-receipt.json
#
# Exit codes:
#   0 = Facts generated successfully (or cached, up-to-date)
#   1 = Transient error (network, disk space)
#   2 = Fatal error (invalid pom.xml)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RECEIPTS_DIR="${REPO_ROOT}/.claude/receipts"
DX_STATE_DIR="${REPO_ROOT}/.yawl/.dx-state"
RECEIPT_FILE="${RECEIPTS_DIR}/observatory-receipt.json"
POM_HASH_FILE="${DX_STATE_DIR}/observatory-pom-hash.txt"

# Create directories
mkdir -p "${RECEIPTS_DIR}" "${DX_STATE_DIR}"

# ──────────────────────────────────────────────────────────────────────────────
# CACHE FRESHNESS CHECK
# ──────────────────────────────────────────────────────────────────────────────

is_cache_fresh() {
    [[ -f "${POM_HASH_FILE}" ]] || return 1

    local stored_hash=$(cat "${POM_HASH_FILE}" 2>/dev/null || echo "")
    local current_hash=$(sha256sum "${REPO_ROOT}/pom.xml" 2>/dev/null | awk '{print $1}')

    [[ "${stored_hash}" == "${current_hash}" ]]
}

# ──────────────────────────────────────────────────────────────────────────────
# RUN OBSERVATORY
# ──────────────────────────────────────────────────────────────────────────────

OBSERVATORY_START=$(date +%s%N)

if is_cache_fresh; then
    echo "📚 Observatory facts cache is fresh (pom.xml unchanged)"
    CACHE_HIT=true
    OBSERVATORY_EXIT=0
else
    echo "🔍 Running Observatory to generate codebase facts..."

    cd "${REPO_ROOT}"

    # Run observatory.sh in facts-only mode (faster startup)
    if bash scripts/observatory/observatory.sh --facts > /tmp/observatory-$$.log 2>&1; then
        OBSERVATORY_EXIT=0
        echo "✅ Observatory facts generated successfully"

        # Update hash cache
        sha256sum "${REPO_ROOT}/pom.xml" 2>/dev/null | awk '{print $1}' \
            > "${POM_HASH_FILE}" || true

        CACHE_HIT=false
    else
        OBSERVATORY_EXIT=$?
        echo "⚠️  Observatory generation failed (exit ${OBSERVATORY_EXIT})"
        CACHE_HIT=false
    fi
fi

OBSERVATORY_END=$(date +%s%N)
OBSERVATORY_DURATION=$(( (OBSERVATORY_END - OBSERVATORY_START) / 1000000 ))

# ──────────────────────────────────────────────────────────────────────────────
# COUNT FACTS
# ──────────────────────────────────────────────────────────────────────────────

FACTS_COUNT=0
if [[ -f "${REPO_ROOT}/docs/v6/latest/INDEX.md" ]]; then
    FACTS_COUNT=$(grep -c "^- " "${REPO_ROOT}/docs/v6/latest/INDEX.md" 2>/dev/null || echo "0")
fi

# ──────────────────────────────────────────────────────────────────────────────
# WRITE RECEIPT
# ──────────────────────────────────────────────────────────────────────────────

STATUS=$([ ${OBSERVATORY_EXIT} -eq 0 ] && echo "GREEN" || echo "RED")

cat > "${RECEIPT_FILE}" << EOF
{
  "phase": "Observatory",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "duration_ms": ${OBSERVATORY_DURATION},
  "status": "${STATUS}",
  "exit_code": ${OBSERVATORY_EXIT},
  "cache_hit": ${CACHE_HIT},
  "facts_count": ${FACTS_COUNT},
  "facts_directory": "docs/v6/latest/",
  "cache_file": "${POM_HASH_FILE}"
}
EOF

# ──────────────────────────────────────────────────────────────────────────────
# DISPLAY RESULTS
# ──────────────────────────────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
echo "📚 Observatory Facts Generation"
echo "───────────────────────────────────────────────────────────────────────────"
echo "Status: ${STATUS}"
echo "Duration: ${OBSERVATORY_DURATION}ms"
echo "Cache hit: ${CACHE_HIT}"
echo "Facts generated: ${FACTS_COUNT}"
echo "Directory: ${REPO_ROOT}/docs/v6/latest/"
echo "Receipt: ${RECEIPT_FILE}"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""

exit ${OBSERVATORY_EXIT}
