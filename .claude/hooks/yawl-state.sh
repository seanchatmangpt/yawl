#!/bin/bash
# YAWL build system state — runs at SessionStart on all platforms (web + local)
# Shows observatory freshness, module count, and last H/Q receipt status.
# Always exits 0 (informational only).

set -euo pipefail

_ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
_FACTS_DIR="${_ROOT}/docs/v6/latest/facts"
_RECEIPTS_DIR="${_ROOT}/.claude/receipts"

echo "=== YAWL — SESSION START STATE ==="
echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo ""

# Observatory freshness: compare pom.xml sha256 against last-stored hash
# Portable: sha256sum (Linux/GNU) or shasum -a 256 (macOS BSD)
_pom_hash() {
    if command -v sha256sum &>/dev/null; then
        sha256sum "$1" 2>/dev/null | cut -d' ' -f1
    elif command -v shasum &>/dev/null; then
        shasum -a 256 "$1" 2>/dev/null | cut -d' ' -f1
    fi || echo ""
}

_POM_HASH_FILE="${_ROOT}/.yawl/.dx-state/observatory-pom-hash.txt"
_CURRENT_HASH=$(_pom_hash "${_ROOT}/pom.xml")
_STORED_HASH=$(cat "$_POM_HASH_FILE" 2>/dev/null || echo "")
if [[ -n "$_CURRENT_HASH" && "$_CURRENT_HASH" == "$_STORED_HASH" ]]; then
    echo "Observatory facts: FRESH"
else
    echo "Observatory facts: STALE — run: bash scripts/observatory/observatory.sh"
fi

# Module count from facts
if [[ -f "$_FACTS_DIR/modules.json" ]]; then
    _MOD_COUNT=$(python3 -c "import json; d=json.load(open('$_FACTS_DIR/modules.json')); print(len(d['modules']))" 2>/dev/null || echo "?")
    echo "Modules: ${_MOD_COUNT}"
else
    echo "Modules: facts missing — run observatory"
fi

# Dependency conflicts
if [[ -f "$_FACTS_DIR/deps-conflicts.json" ]]; then
    _CONFLICTS=$(python3 -c "import json; d=json.load(open('$_FACTS_DIR/deps-conflicts.json')); print(len(d.get('conflicts', [])))" 2>/dev/null || echo "?")
    echo "Dependency conflicts: ${_CONFLICTS}"
    if [[ "$_CONFLICTS" != "0" && "$_CONFLICTS" != "?" ]]; then
        echo "  WARNING: Resolve conflicts before committing (see deps-conflicts.json)"
    fi
fi

echo ""

# H (Guards) phase — last receipt
_GUARD_RECEIPT="$_RECEIPTS_DIR/guard-receipt.json"
if [[ -f "$_GUARD_RECEIPT" ]]; then
    _G_STATUS=$(python3 -c "import json; d=json.load(open('$_GUARD_RECEIPT')); print(d.get('status','UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")
    _G_TS=$(python3 -c "import json; d=json.load(open('$_GUARD_RECEIPT')); print(d.get('timestamp','?'))" 2>/dev/null || echo "?")
    echo "H (Guards) last run: ${_G_STATUS} at ${_G_TS}"
    if [[ "$_G_STATUS" == "RED" ]]; then
        _G_V=$(python3 -c "import json; d=json.load(open('$_GUARD_RECEIPT')); s=d.get('summary',{}); print(s.get('total_violations', d.get('violations_found','?')))" 2>/dev/null || echo "?")
        echo "  WARNING: ${_G_V} guard violations — fix before committing (run dx.sh all)"
    fi
else
    echo "H (Guards) last run: never (run dx.sh all)"
fi

# Q (Invariants) phase — last receipt
_INV_RECEIPT="$_RECEIPTS_DIR/invariant-receipt.json"
if [[ -f "$_INV_RECEIPT" ]]; then
    _Q_STATUS=$(python3 -c "import json; d=json.load(open('$_INV_RECEIPT')); print(d.get('status','UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")
    _Q_TS=$(python3 -c "import json; d=json.load(open('$_INV_RECEIPT')); print(d.get('timestamp','?'))" 2>/dev/null || echo "?")
    echo "Q (Invariants) last run: ${_Q_STATUS} at ${_Q_TS}"
    if [[ "$_Q_STATUS" == "RED" ]]; then
        echo "  WARNING: Invariant violations — fix before committing"
    fi
else
    echo "Q (Invariants) last run: never (run dx.sh all)"
fi

echo ""
echo "=== RULES IN EFFECT ==="
echo "1. A phase is complete only when its verification command ran and output is quoted"
echo "2. ls/find/grep proves existence only — never proves correctness"
echo "3. dx.sh all must be GREEN before any commit (H + Q gates)"
echo "4. READY FOR APPROVAL requires dx.sh all to have exited 0 with output shown"
echo "5. No mock/stub/TODO in production code — hyper-validate.sh blocks on every write"
echo "=================================================="

exit 0
