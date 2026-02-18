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
source "${LIB_DIR}/emit-diagrams.sh"
source "${LIB_DIR}/emit-yawl-xml.sh"
source "${LIB_DIR}/emit-receipt.sh"
source "${LIB_DIR}/emit-integration-diagrams.sh"
source "${LIB_DIR}/emit-static-analysis.sh"

# ── Parse arguments ───────────────────────────────────────────────────────
RUN_FACTS=true
RUN_DIAGRAMS=true
RUN_YAWL=true
RUN_RECEIPT=true
RUN_INTEGRATION=true
RUN_STATIC_ANALYSIS=true

for arg in "$@"; do
    case "$arg" in
        --facts)    RUN_DIAGRAMS=false; RUN_YAWL=false; RUN_RECEIPT=false; RUN_INTEGRATION=false; RUN_STATIC_ANALYSIS=false ;;
        --diagrams) RUN_FACTS=false; RUN_YAWL=false; RUN_RECEIPT=false; RUN_INTEGRATION=false; RUN_STATIC_ANALYSIS=false ;;
        --yawl)     RUN_FACTS=false; RUN_DIAGRAMS=false; RUN_RECEIPT=false; RUN_INTEGRATION=false; RUN_STATIC_ANALYSIS=false ;;
        --integration) RUN_FACTS=false; RUN_DIAGRAMS=false; RUN_YAWL=false; RUN_RECEIPT=false; RUN_STATIC_ANALYSIS=false ;;
        --static-analysis) RUN_FACTS=false; RUN_DIAGRAMS=false; RUN_YAWL=false; RUN_RECEIPT=false; RUN_INTEGRATION=false ;;
        --help)
            echo "Usage: observatory.sh [--facts|--diagrams|--yawl|--integration|--static-analysis|--help]"
            echo "  (no args)   Full run: facts + diagrams + YAWL XML + integration + static-analysis + receipt"
            echo "  --facts     Generate fact JSON files only"
            echo "  --diagrams  Generate Mermaid diagrams only"
            echo "  --yawl      Generate YAWL XML only"
            echo "  --integration Generate MCP/A2A integration diagrams only"
            echo "  --static-analysis Generate static analysis facts and diagrams only"
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
    log_info "Phase 1/6: Generating facts ..."
    emit_all_facts
    echo ""
fi

# ── Phase 2: Diagrams ────────────────────────────────────────────────────
if $RUN_DIAGRAMS; then
    log_info "Phase 2/6: Generating diagrams ..."
    emit_all_diagrams
    echo ""
fi

# ── Phase 3: YAWL XML ────────────────────────────────────────────────────
if $RUN_YAWL; then
    log_info "Phase 3/6: Generating YAWL XML ..."
    emit_yawl_xml_all
    echo ""
fi

# ── Phase 4: Integration Diagrams ────────────────────────────────────────
if $RUN_INTEGRATION; then
    log_info "Phase 4/6: Generating MCP/A2A integration diagrams ..."
    emit_all_integration_diagrams
    echo ""
fi

# ── Phase 5: Static Analysis ─────────────────────────────────────────────
if $RUN_STATIC_ANALYSIS; then
    log_info "Phase 5/6: Generating static analysis facts and diagrams ..."
    emit_static_analysis_facts
    emit_static_analysis_diagrams
    echo ""
fi

# ── Phase 6: Receipt + INDEX ─────────────────────────────────────────────
TOTAL_ELAPSED=$(( $(epoch_ms) - GLOBAL_START ))
export TOTAL_ELAPSED

if $RUN_RECEIPT; then
    log_info "Phase 6/6: Generating receipt and INDEX ..."
    emit_receipt_and_index
    echo ""
fi

# ── Final status line (required by PRD) ───────────────────────────────────
FINAL_STATUS="GREEN"
[[ ${#REFUSALS[@]} -gt 0 ]] && FINAL_STATUS="RED"
[[ ${#WARNINGS[@]} -gt 0 && "$FINAL_STATUS" == "GREEN" ]] && FINAL_STATUS="YELLOW"

# Calculate totals for performance summary
facts_count=$(ls "$FACTS_DIR"/*.json 2>/dev/null | wc -l | tr -d ' ')
diagrams_count=$(ls "$DIAGRAMS_DIR"/*.mmd 2>/dev/null | wc -l | tr -d ' ')
yawl_count=$(ls "$YAWL_DIR"/*.xml 2>/dev/null | wc -l | tr -d ' ')

# Get peak memory
peak_mem=0
for mem in "${PHASE_MEMORY[@]:-}"; do
    [[ "${mem:-0}" -gt "$peak_mem" ]] && peak_mem="$mem"
done
peak_mem_mb=$(( peak_mem / 1024 ))

# Calculate throughput
total_outputs=$(( facts_count + diagrams_count + yawl_count ))
total_sec=0
if [[ "${TOTAL_ELAPSED:-0}" -gt 0 ]]; then
    total_sec=$(echo "scale=3; ${TOTAL_ELAPSED} / 1000" | bc 2>/dev/null || echo "0")
fi

echo ""
echo "=================================================================="
echo "  Observatory Complete"
echo "  Output: docs/v6/latest/"
echo "  Facts:    ${facts_count} files"
echo "  Diagrams: ${diagrams_count} files"
echo "  YAWL XML: ${yawl_count} files"
echo "  Refusals: ${#REFUSALS[@]}"
echo "  Warnings: ${#WARNINGS[@]}"
echo "------------------------------------------------------------------"
echo "  Performance Summary"
echo "  Total Time:    ${TOTAL_ELAPSED}ms (${total_sec}s)"
echo "  Peak Memory:   ${peak_mem_mb}MB"
echo "  Throughput:    ${total_outputs} outputs"
echo "  Facts:         ${FACTS_ELAPSED:-0}ms"
echo "  Diagrams:      ${DIAGRAMS_ELAPSED:-0}ms"
echo "  YAWL XML:      ${YAWL_XML_ELAPSED:-0}ms"
echo "  Receipt:       ${RECEIPT_ELAPSED:-0}ms"
echo "------------------------------------------------------------------"
echo "  Performance Report: docs/v6/latest/performance/"
echo "=================================================================="
echo ""

# Required console tail
echo "STATUS=${FINAL_STATUS} RUN_ID=${RUN_ID} RECEIPT=docs/v6/latest/receipts/observatory.json PERF=docs/v6/latest/performance/summary.json"
