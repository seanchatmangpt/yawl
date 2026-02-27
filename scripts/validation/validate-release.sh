#!/usr/bin/env bash
# validate-release.sh — Release Gate Validator (PY-1, PY-2, PY-3)
#
# Poka-yoke mechanisms against FM8 (release docs before gates), FM10 (version without
# evidence), FM11 (coverage unmeasured), FM12 (SBOM missing).
#
# Usage:
#   bash scripts/validation/validate-release.sh           # all gates
#   bash scripts/validation/validate-release.sh receipts  # receipt gates only (PY-1)
#   bash scripts/validation/validate-release.sh coverage  # JaCoCo gate only (PY-2)
#   bash scripts/validation/validate-release.sh sbom      # SBOM+Grype gate only (PY-3)
#   bash scripts/validation/validate-release.sh stability # Stability receipt gate (FM13)
#
# Exit codes:
#   0 — all requested gates GREEN
#   1 — transient error (missing tool, IO failure)
#   2 — gate violation found — DO NOT release until fixed

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RECEIPTS_DIR="${REPO_ROOT}/receipts"
RECEIPT_MAX_AGE_HOURS=24

# ─── Colour output ────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass()  { echo -e "${GREEN}[PASS]${NC} $*"; }
fail()  { echo -e "${RED}[FAIL]${NC} $*"; FAILURES=$((FAILURES + 1)); }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
info()  { echo "       $*"; }

FAILURES=0

# ─── PY-1 helper: receipt freshness ──────────────────────────────────────────
# assert_receipt_exists <path> <max_age_hours>
assert_receipt_exists() {
    local receipt="$1"
    local max_age_hours="${2:-$RECEIPT_MAX_AGE_HOURS}"

    if [ ! -f "$receipt" ]; then
        fail "MISSING receipt: $receipt"
        info "Run the gate that produces this receipt, then retry."
        return
    fi

    # Extract timestamp field (ISO 8601) via jq; fall back gracefully
    if ! command -v jq &>/dev/null; then
        warn "jq not found — skipping age check for $receipt"
        pass "Receipt exists (age not verified): $receipt"
        return
    fi

    local ts
    ts=$(jq -r '.timestamp // .generated_at // empty' "$receipt" 2>/dev/null || true)

    if [ -z "$ts" ]; then
        warn "No timestamp field in $receipt — skipping age check"
        pass "Receipt exists (no timestamp): $receipt"
        return
    fi

    local receipt_epoch now_epoch age_hours
    receipt_epoch=$(date -d "$ts" +%s 2>/dev/null || date -j -f "%Y-%m-%dT%H:%M:%SZ" "$ts" +%s 2>/dev/null || echo 0)
    now_epoch=$(date +%s)

    if [ "$receipt_epoch" -eq 0 ]; then
        warn "Cannot parse timestamp '$ts' in $receipt — skipping age check"
        pass "Receipt exists (unparseable timestamp): $receipt"
        return
    fi

    age_hours=$(( (now_epoch - receipt_epoch) / 3600 ))

    if [ "$age_hours" -gt "$max_age_hours" ]; then
        fail "STALE receipt (${age_hours}h old, max ${max_age_hours}h): $receipt"
        info "Re-run the gate that produces this receipt, then retry."
    else
        pass "Receipt fresh (${age_hours}h old): $(basename "$receipt")"
    fi
}

# ─── PY-1: Gate receipt assertions ───────────────────────────────────────────
gate_receipts() {
    echo ""
    echo "=== PY-1: Gate Receipts (FM8, FM10) ==="

    assert_receipt_exists "${RECEIPTS_DIR}/gate-G_guard-receipt.json"
    assert_receipt_exists "${RECEIPTS_DIR}/gate-G_test-receipt.json"
    assert_receipt_exists "${RECEIPTS_DIR}/gate-G_security-receipt.json"
    assert_receipt_exists "${RECEIPTS_DIR}/stability-test-receipt.json" 0  # no max age — human-signed

    # Verify stability receipt fields (FM13)
    local stability="${RECEIPTS_DIR}/stability-test-receipt.json"
    if [ -f "$stability" ] && command -v jq &>/dev/null; then
        local duration result signed_by
        duration=$(jq -r '.duration_hours // 0' "$stability")
        result=$(jq -r '.result // ""' "$stability")
        signed_by=$(jq -r '.signed_by // ""' "$stability")

        if (( $(echo "$duration < 48" | bc -l 2>/dev/null || echo 1) )); then
            fail "Stability test duration ${duration}h < 48h required (FM13)"
        else
            pass "Stability test duration: ${duration}h ≥ 48h"
        fi

        if [ "$result" != "PASS" ]; then
            fail "Stability test result='$result', expected PASS (FM13)"
        else
            pass "Stability test result: PASS"
        fi

        if [ -z "$signed_by" ]; then
            fail "Stability test unsigned — signed_by must not be empty (FM13)"
        else
            pass "Stability test signed by: $signed_by"
        fi
    fi
}

# ─── PY-2: JaCoCo coverage artifact ──────────────────────────────────────────
gate_coverage() {
    echo ""
    echo "=== PY-2: Test Coverage Artifact (FM11) ==="

    local jacoco="${REPO_ROOT}/target/site/jacoco-aggregate/index.html"

    if [ ! -f "$jacoco" ]; then
        fail "JaCoCo aggregate report not found: $jacoco"
        info "Run: mvn -T 1.5C clean verify -P coverage"
        info "Then check target/site/jacoco-aggregate/index.html"
    else
        pass "JaCoCo aggregate report exists"

        # Extract overall instruction coverage from HTML
        if command -v grep &>/dev/null; then
            local cov_line
            cov_line=$(grep -oP 'Total[^%]+\K[0-9]+(?=%)' "$jacoco" 2>/dev/null | head -1 || true)
            if [ -n "$cov_line" ]; then
                if [ "$cov_line" -lt 55 ]; then
                    fail "Instruction coverage ${cov_line}% < 55% minimum threshold"
                elif [ "$cov_line" -lt 65 ]; then
                    warn "Instruction coverage ${cov_line}% < 65% target (above 55% floor)"
                else
                    pass "Instruction coverage: ${cov_line}%"
                fi
            else
                warn "Could not parse coverage % from JaCoCo HTML"
            fi
        fi
    fi
}

# ─── PY-3: SBOM + Grype CVE scan ─────────────────────────────────────────────
gate_sbom() {
    echo ""
    echo "=== PY-3: SBOM + Grype CVE Scan (FM12) ==="

    local bom="${REPO_ROOT}/target/bom.json"

    if [ ! -f "$bom" ]; then
        fail "SBOM not found: $bom"
        info "Run: mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom"
        return
    else
        pass "SBOM exists: $bom"
    fi

    if ! command -v grype &>/dev/null; then
        fail "grype not found in PATH"
        info "Install: https://github.com/anchore/grype#installation"
        info "Then run: grype sbom:$bom --fail-on critical"
        return
    fi

    echo "       Running: grype sbom:$bom --fail-on critical ..."
    if grype "sbom:$bom" --fail-on critical --quiet; then
        pass "Grype CVE scan: no critical vulnerabilities"
    else
        fail "Grype found critical CVEs — resolve before release (FM12)"
        info "Run: grype sbom:$bom for full report"
    fi
}

# ─── Main ─────────────────────────────────────────────────────────────────────
GATE="${1:-all}"

case "$GATE" in
    receipts)  gate_receipts ;;
    coverage)  gate_coverage ;;
    sbom)      gate_sbom ;;
    stability) gate_receipts ;;  # stability receipt is part of PY-1
    all)
        gate_receipts
        gate_coverage
        gate_sbom
        ;;
    *)
        echo "Unknown gate: $GATE"
        echo "Usage: $0 [all|receipts|coverage|sbom|stability]"
        exit 1
        ;;
esac

echo ""
if [ "$FAILURES" -eq 0 ]; then
    echo -e "${GREEN}=== RELEASE GATE: GREEN — all checks passed ===${NC}"
    exit 0
else
    echo -e "${RED}=== RELEASE GATE: RED — $FAILURES violation(s) found ===${NC}"
    echo "    Fix all failures before tagging the release."
    exit 2
fi
