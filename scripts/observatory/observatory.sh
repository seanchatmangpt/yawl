#!/usr/bin/env bash
# ==========================================================================
# observatory.sh — YAWL V6 Code Analysis Observatory
#
# Single entry point: generates all facts, diagrams, YAWL XML, receipt,
# and INDEX.md in docs/v6/latest/ (overwrite-in-place, latest-only).
#
# Usage:
#   ./scripts/observatory/observatory.sh          # Full run
#   ./scripts/observatory/observatory.sh --facts   # Facts only
#   ./scripts/observatory/observatory.sh --diagrams # Diagrams only
#
# Output: docs/v6/latest/{INDEX.md,receipts/,facts/,diagrams/}
# ==========================================================================
set -uo pipefail
# Note: -e omitted intentionally; individual commands may return non-zero
# (e.g., grep with no matches). We handle errors explicitly.

# ── Resolve script location and source libraries ─────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/lib"

source "${LIB_DIR}/util.sh"
source "${LIB_DIR}/emit-facts.sh"
source "${LIB_DIR}/emit-coverage.sh"
source "${LIB_DIR}/emit-diagrams.sh"
source "${LIB_DIR}/emit-yawl-xml.sh"
source "${LIB_DIR}/emit-receipt.sh"

# ── Parse arguments ───────────────────────────────────────────────────────
RUN_FACTS=true
RUN_DIAGRAMS=true
RUN_YAWL=true
RUN_RECEIPT=true

for arg in "$@"; do
    case "$arg" in
        --facts)    RUN_DIAGRAMS=false; RUN_YAWL=false; RUN_RECEIPT=false ;;
        --diagrams) RUN_FACTS=false; RUN_YAWL=false; RUN_RECEIPT=false ;;
        --yawl)     RUN_FACTS=false; RUN_DIAGRAMS=false; RUN_RECEIPT=false ;;
        --help)
            echo "Usage: observatory.sh [--facts|--diagrams|--yawl|--help]"
            echo "  (no args)   Full run: facts + diagrams + YAWL XML + receipt"
            echo "  --facts     Generate fact JSON files only"
            echo "  --diagrams  Generate Mermaid diagrams only"
            echo "  --yawl      Generate YAWL XML only"
            exit 0
            ;;
    esac
done

# ── Initialize ────────────────────────────────────────────────────────────
RUN_ID=$(generate_run_id)
export RUN_ID

timer_start
GLOBAL_START=$(epoch_ms)

echo ""
echo "=================================================================="
echo "  YAWL V6 Code Analysis Observatory"
echo "  Run ID:  ${RUN_ID}"
echo "  Branch:  $(git_branch)"
echo "  Commit:  $(git_commit)"
echo "  Java:    $(detect_java_version)"
echo "  Maven:   $(detect_maven_version)"
echo "=================================================================="
echo ""

ensure_output_dirs

# ── Phase 1: Facts ────────────────────────────────────────────────────────
if $RUN_FACTS; then
    log_info "Phase 1/4: Generating facts ..."
    emit_all_facts
    echo ""
fi

# ── Phase 2: Diagrams ────────────────────────────────────────────────────
if $RUN_DIAGRAMS; then
    log_info "Phase 2/4: Generating diagrams ..."
    emit_all_diagrams
    echo ""
fi

# ── Phase 3: YAWL XML ────────────────────────────────────────────────────
if $RUN_YAWL; then
    log_info "Phase 3/4: Generating YAWL XML ..."
    emit_yawl_xml_all
    echo ""
fi

# ── Phase 4: Receipt + INDEX ─────────────────────────────────────────────
TOTAL_ELAPSED=$(( $(epoch_ms) - GLOBAL_START ))
export TOTAL_ELAPSED

if $RUN_RECEIPT; then
    log_info "Phase 4/4: Generating receipt and INDEX ..."
    emit_receipt_and_index
    echo ""
fi

# ── Final status line (required by PRD) ───────────────────────────────────
FINAL_STATUS="GREEN"
[[ ${#REFUSALS[@]} -gt 0 ]] && FINAL_STATUS="RED"
[[ ${#WARNINGS[@]} -gt 0 && "$FINAL_STATUS" == "GREEN" ]] && FINAL_STATUS="YELLOW"

echo ""
echo "=================================================================="
echo "  Observatory Complete"
echo "  Output: docs/v6/latest/"
echo "  Facts:    $(ls "$FACTS_DIR"/*.json 2>/dev/null | wc -l) files"
echo "  Diagrams: $(ls "$DIAGRAMS_DIR"/*.mmd 2>/dev/null | wc -l) files"
echo "  YAWL XML: $(ls "$YAWL_DIR"/*.xml 2>/dev/null | wc -l) files"
echo "  Refusals: ${#REFUSALS[@]}"
echo "  Warnings: ${#WARNINGS[@]}"
echo "=================================================================="
echo ""

# Required console tail
echo "STATUS=${FINAL_STATUS} RUN_ID=${RUN_ID} RECEIPT=docs/v6/latest/receipts/observatory.json"
