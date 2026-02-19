#!/usr/bin/env bash
# ==========================================================================
# observatory.sh — YAWL V6 Code Analysis Observatory (Parallel Version)
#
# Single entry point: generates all facts, diagrams, YAWL XML, receipt,
# and INDEX.md in docs/v6/latest/ (overwrite-in-place, latest-only).
#
# Phases 1-5 run in PARALLEL for ~60% faster execution.
# Only Phase 6 (Receipt) depends on them.
#
# Usage:
#   ./scripts/observatory/observatory.sh          # Full run
#   ./scripts/observatory/observatory.sh --facts   # Facts only
#   ./scripts/observatory/observatory.sh --diagrams # Diagrams only
#   OBSERVATORY_FORCE=1 ./scripts/observatory/observatory.sh  # Force regeneration
#
# Output: docs/v6/latest/{INDEX.md,receipts/,facts/,diagrams/}
# ==========================================================================
set -uo pipefail
# Note: -e omitted intentionally; individual commands may return non-zero

# ── Resolve script location and source libraries ─────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/lib"

source "${LIB_DIR}/util.sh"
source "${LIB_DIR}/locking.sh"
source "${LIB_DIR}/dependency-registry.sh"
source "${LIB_DIR}/discovery-cache.sh"
source "${LIB_DIR}/incremental.sh"
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
            echo "  (no args)   Full run: facts + diagrams + YAWL XML + integration + static-analysis + receipt (PARALLEL)"
            echo "  --facts     Generate fact JSON files only"
            echo "  --diagrams  Generate Mermaid diagrams only"
            echo "  --yawl      Generate YAWL XML only"
            echo "  --integration Generate MCP/A2A integration diagrams only"
            echo "  --static-analysis Generate static analysis facts and diagrams only"
            echo "  OBSERVATORY_FORCE=1  Force regeneration (ignore incremental cache)"
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
echo "  YAWL V6 Code Analysis Observatory (Parallel)"
echo "  Run ID:  ${RUN_ID}"
echo "  Branch:  $(git_branch)"
echo "  Commit:  $(git_commit)"
echo "  Java:    $(detect_java_version)"
echo "  Maven:   $(detect_maven_version)"
echo "  Mode:    $(is_force_mode && echo 'FORCE (full regeneration)' || echo 'INCREMENTAL (skip if unchanged)')"
echo "=================================================================="
echo ""

# ── Acquire exclusive lock to prevent concurrent runs ──────────────────────
if ! acquire_lock; then
    log_error "Failed to acquire observatory lock. Another run may be in progress."
    log_info "If this is a stale lock, run: rm -f ${OBSERVATORY_LOCK_FILE}"
    log_info "Or set OBSERVATORY_LOCK_TIMEOUT=30 to wait for lock release"
    exit 1
fi
trap release_lock EXIT

ensure_output_dirs

# ── Warm discovery cache (parallel) ───────────────────────────────────────
parallel_discover_all

# ── Parallel Phase Execution ──────────────────────────────────────────────
# Phases 1-5 run concurrently as background jobs
# Phase 6 (Receipt) runs after all complete

# Track PIDs and status
declare -A PHASE_PIDS
declare -A PHASE_STATUS
PHASE_STATUS[facts]="pending"
PHASE_STATUS[diagrams]="pending"
PHASE_STATUS[yawl]="pending"
PHASE_STATUS[integration]="pending"
PHASE_STATUS[static_analysis]="pending"

# Temp files for phase output
PHASE_TMP_DIR=$(mktemp -d)
trap "rm -rf $PHASE_TMP_DIR" EXIT

# ── Phase 1: Facts (background) ───────────────────────────────────────────
if $RUN_FACTS; then
    (
        log_info "Phase 1/6: Generating facts ... [PARALLEL]"
        timer_start
        record_memory "facts_start"
        reset_cache_stats 2>/dev/null || true
        emit_all_facts
        FACTS_ELAPSED=$(timer_elapsed_ms)
        record_phase_timing "facts" "$FACTS_ELAPSED"
        echo "$FACTS_ELAPSED" > "${PHASE_TMP_DIR}/facts_elapsed"
        # Export cache stats for aggregation (subshell isolation requires file export)
        if declare -f get_cache_summary_json >/dev/null 2>&1; then
            get_cache_summary_json > "${PHASE_TMP_DIR}/facts_cache_stats"
            log_info "Facts cache stats exported: $(cat "${PHASE_TMP_DIR}/facts_cache_stats")"
        fi
        log_ok "Facts completed in ${FACTS_ELAPSED}ms"
    ) &
    PHASE_PIDS[facts]=$!
fi

# ── Phase 2: Diagrams (background) ────────────────────────────────────────
if $RUN_DIAGRAMS; then
    (
        log_info "Phase 2/6: Generating diagrams ... [PARALLEL]"
        timer_start
        record_memory "diagrams_start"
        reset_cache_stats 2>/dev/null || true
        emit_all_diagrams
        DIAGRAMS_ELAPSED=$(timer_elapsed_ms)
        record_phase_timing "diagrams" "$DIAGRAMS_ELAPSED"
        echo "$DIAGRAMS_ELAPSED" > "${PHASE_TMP_DIR}/diagrams_elapsed"
        # Export cache stats for aggregation (subshell isolation requires file export)
        if declare -f get_cache_summary_json >/dev/null 2>&1; then
            get_cache_summary_json > "${PHASE_TMP_DIR}/diagrams_cache_stats"
            log_info "Diagrams cache stats exported: $(cat "${PHASE_TMP_DIR}/diagrams_cache_stats")"
        fi
        log_ok "Diagrams completed in ${DIAGRAMS_ELAPSED}ms"
    ) &
    PHASE_PIDS[diagrams]=$!
fi

# ── Phase 3: YAWL XML (background) ────────────────────────────────────────
if $RUN_YAWL; then
    (
        log_info "Phase 3/6: Generating YAWL XML ... [PARALLEL]"
        timer_start
        record_memory "yawl_start"
        reset_cache_stats 2>/dev/null || true
        emit_yawl_xml_all
        YAWL_XML_ELAPSED=$(timer_elapsed_ms)
        record_phase_timing "yawl_xml" "$YAWL_XML_ELAPSED"
        echo "$YAWL_XML_ELAPSED" > "${PHASE_TMP_DIR}/yawl_elapsed"
        # Export cache stats for aggregation (subshell isolation requires file export)
        if declare -f get_cache_summary_json >/dev/null 2>&1; then
            get_cache_summary_json > "${PHASE_TMP_DIR}/yawl_cache_stats"
            log_info "YAWL cache stats exported: $(cat "${PHASE_TMP_DIR}/yawl_cache_stats")"
        fi
        log_ok "YAWL XML completed in ${YAWL_XML_ELAPSED}ms"
    ) &
    PHASE_PIDS[yawl]=$!
fi

# ── Phase 4: Integration Diagrams (background) ────────────────────────────
if $RUN_INTEGRATION; then
    (
        log_info "Phase 4/6: Generating MCP/A2A integration diagrams ... [PARALLEL]"
        timer_start
        record_memory "integration_start"
        reset_cache_stats 2>/dev/null || true
        emit_all_integration_diagrams
        INTEGRATION_ELAPSED=$(timer_elapsed_ms)
        record_phase_timing "integration" "$INTEGRATION_ELAPSED"
        echo "$INTEGRATION_ELAPSED" > "${PHASE_TMP_DIR}/integration_elapsed"
        # Export cache stats for aggregation (subshell isolation requires file export)
        if declare -f get_cache_summary_json >/dev/null 2>&1; then
            get_cache_summary_json > "${PHASE_TMP_DIR}/integration_cache_stats"
            log_info "Integration cache stats exported: $(cat "${PHASE_TMP_DIR}/integration_cache_stats")"
        fi
        log_ok "Integration diagrams completed in ${INTEGRATION_ELAPSED}ms"
    ) &
    PHASE_PIDS[integration]=$!
fi

# ── Phase 5: Static Analysis (background) ─────────────────────────────────
if $RUN_STATIC_ANALYSIS; then
    (
        log_info "Phase 5/6: Generating static analysis facts and diagrams ... [PARALLEL]"
        timer_start
        record_memory "static_analysis_start"
        reset_cache_stats 2>/dev/null || true
        emit_static_analysis_facts
        emit_static_analysis_diagrams
        STATIC_ANALYSIS_ELAPSED=$(timer_elapsed_ms)
        record_phase_timing "static_analysis" "$STATIC_ANALYSIS_ELAPSED"
        echo "$STATIC_ANALYSIS_ELAPSED" > "${PHASE_TMP_DIR}/static_analysis_elapsed"
        # Export cache stats for aggregation (subshell isolation requires file export)
        if declare -f get_cache_summary_json >/dev/null 2>&1; then
            get_cache_summary_json > "${PHASE_TMP_DIR}/static_analysis_cache_stats"
            log_info "Static analysis cache stats exported: $(cat "${PHASE_TMP_DIR}/static_analysis_cache_stats")"
        fi
        log_ok "Static analysis completed in ${STATIC_ANALYSIS_ELAPSED}ms"
    ) &
    PHASE_PIDS[static_analysis]=$!
fi

# ── Wait for all parallel phases ──────────────────────────────────────────
log_info "Waiting for parallel phases to complete..."

FAILED_PHASES=()
for phase in "${!PHASE_PIDS[@]}"; do
    pid="${PHASE_PIDS[$phase]}"
    if ! wait "$pid"; then
        FAILED_PHASES+=("$phase")
        PHASE_STATUS[$phase]="failed"
    else
        PHASE_STATUS[$phase]="success"
    fi
done

# Read elapsed times from temp files
[[ -f "${PHASE_TMP_DIR}/facts_elapsed" ]] && FACTS_ELAPSED=$(cat "${PHASE_TMP_DIR}/facts_elapsed")
[[ -f "${PHASE_TMP_DIR}/diagrams_elapsed" ]] && DIAGRAMS_ELAPSED=$(cat "${PHASE_TMP_DIR}/diagrams_elapsed")
[[ -f "${PHASE_TMP_DIR}/yawl_elapsed" ]] && YAWL_XML_ELAPSED=$(cat "${PHASE_TMP_DIR}/yawl_elapsed")
[[ -f "${PHASE_TMP_DIR}/integration_elapsed" ]] && INTEGRATION_ELAPSED=$(cat "${PHASE_TMP_DIR}/integration_elapsed")
[[ -f "${PHASE_TMP_DIR}/static_analysis_elapsed" ]] && STATIC_ANALYSIS_ELAPSED=$(cat "${PHASE_TMP_DIR}/static_analysis_elapsed")

# ── Aggregate cache statistics from parallel phases ─────────────────────────
AGGREGATED_CACHE_HITS=0
AGGREGATED_CACHE_MISSES=0
AGGREGATED_CACHE_SKIPPED=0

aggregate_phase_cache_stats() {
    local phase="$1"
    local stats_file="${PHASE_TMP_DIR}/${phase}_cache_stats"
    if [[ -f "$stats_file" ]]; then
        local hits misses skipped
        # Robust JSON parsing with error handling
        hits=$(python3 -c "import json; print(json.load(open('$stats_file')).get('hits', 0))" 2>/dev/null || echo "0")
        misses=$(python3 -c "import json; print(json.load(open('$stats_file')).get('misses', 0))" 2>/dev/null || echo "0")
        skipped=$(python3 -c "import json; print(json.load(open('$stats_file')).get('skipped', 0))" 2>/dev/null || echo "0")
        # Ensure numeric values (handle edge cases)
        hits=${hits:-0}
        misses=${misses:-0}
        skipped=${skipped:-0}
        AGGREGATED_CACHE_HITS=$((AGGREGATED_CACHE_HITS + hits))
        AGGREGATED_CACHE_MISSES=$((AGGREGATED_CACHE_MISSES + misses))
        AGGREGATED_CACHE_SKIPPED=$((AGGREGATED_CACHE_SKIPPED + skipped))
        log_info "Phase '$phase' cache: hits=$hits, misses=$misses, skipped=$skipped"
    else
        log_info "Phase '$phase' cache stats file not found (phase may have been skipped)"
    fi
}

# Aggregate stats from all phases that ran
log_info "Aggregating cache statistics from parallel phases..."
for phase in facts diagrams yawl integration static_analysis; do
    aggregate_phase_cache_stats "$phase"
done

# Calculate aggregate hit ratio
AGGREGATED_TOTAL=$((AGGREGATED_CACHE_HITS + AGGREGATED_CACHE_MISSES))
if [[ $AGGREGATED_TOTAL -gt 0 ]]; then
    AGGREGATED_HIT_RATIO=$(echo "scale=2; $AGGREGATED_CACHE_HITS / $AGGREGATED_TOTAL" | bc 2>/dev/null || echo "0")
    # Ensure leading zero for values < 1
    if [[ "$AGGREGATED_HIT_RATIO" == .* ]]; then
        AGGREGATED_HIT_RATIO="0${AGGREGATED_HIT_RATIO}"
    fi
else
    AGGREGATED_HIT_RATIO="0.00"
fi

# Log cache performance
log_info "Cache Performance Summary: ${AGGREGATED_CACHE_HITS} hits, ${AGGREGATED_CACHE_MISSES} misses, ${AGGREGATED_CACHE_SKIPPED} skipped, ${AGGREGATED_HIT_RATIO} hit ratio"

# Report phase status
for phase in "${!PHASE_STATUS[@]}"; do
    status="${PHASE_STATUS[$phase]}"
    if [[ "$status" == "failed" ]]; then
        log_error "Phase $phase FAILED"
    else
        log_ok "Phase $phase: SUCCESS"
    fi
done

# ── Phase 6: Receipt + INDEX (sequential, depends on all phases) ──────────
TOTAL_ELAPSED=$(( $(epoch_ms) - GLOBAL_START ))
export TOTAL_ELAPSED

if $RUN_RECEIPT; then
    log_info "Phase 6/6: Generating receipt and INDEX ..."
    emit_receipt_and_index
    echo ""
fi

# ── Final status line (required by PRD) ───────────────────────────────────
FINAL_STATUS="GREEN"
[[ ${#FAILED_PHASES[@]} -gt 0 || ${#REFUSALS[@]} -gt 0 ]] && FINAL_STATUS="RED"
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
echo "  Observatory Complete (Parallel)"
echo "  Output: docs/v6/latest/"
echo "  Facts:    ${facts_count} files"
echo "  Diagrams: ${diagrams_count} files"
echo "  YAWL XML: ${yawl_count} files"
echo "  Refusals: ${#REFUSALS[@]}"
echo "  Warnings: ${#WARNINGS[@]}"
echo "  Failed phases: ${#FAILED_PHASES[@]}"
echo "------------------------------------------------------------------"
echo "  Performance Summary (PARALLEL)"
echo "  Total Time:    ${TOTAL_ELAPSED}ms (${total_sec}s)"
echo "  Peak Memory:   ${peak_mem_mb}MB"
echo "  Throughput:    ${total_outputs} outputs"
echo "  Facts:         ${FACTS_ELAPSED:-0}ms"
echo "  Diagrams:      ${DIAGRAMS_ELAPSED:-0}ms"
echo "  YAWL XML:      ${YAWL_XML_ELAPSED:-0}ms"
echo "  Integration:   ${INTEGRATION_ELAPSED:-0}ms"
echo "  Static Analysis: ${STATIC_ANALYSIS_ELAPSED:-0}ms"
echo "------------------------------------------------------------------"
echo "  Cache Performance (Incremental)"
echo "  Cache Hits:    ${AGGREGATED_CACHE_HITS}"
echo "  Cache Misses:  ${AGGREGATED_CACHE_MISSES}"
echo "  Cache Skipped: ${AGGREGATED_CACHE_SKIPPED}"
echo "  Hit Ratio:     ${AGGREGATED_HIT_RATIO}"
echo "------------------------------------------------------------------"
echo "  Performance Report: docs/v6/latest/performance/"
echo "=================================================================="
echo ""

# Required console tail
echo "STATUS=${FINAL_STATUS} RUN_ID=${RUN_ID} RECEIPT=docs/v6/latest/receipts/observatory.json PERF=docs/v6/latest/performance/summary.json"

# Exit with error if critical phases failed
if [[ "$FINAL_STATUS" == "RED" ]]; then
    exit 1
fi
