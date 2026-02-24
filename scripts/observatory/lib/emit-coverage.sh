#!/usr/bin/env bash
set -euo pipefail
# ==========================================================================
# emit-coverage.sh — Parse JaCoCo CSV reports and emit coverage.json fact
#
# Called by emit_all_facts() in emit-facts.sh when the observatory runs.
# Reads per-module JaCoCo CSV reports from target/site/jacoco/jacoco.csv,
# computes line/branch/instruction percentages, and compares against targets
# from quality/test-metrics/coverage-targets.yaml.
#
# Usage (standalone):
#   source scripts/observatory/lib/util.sh
#   source scripts/observatory/lib/emit-coverage.sh
#   emit_coverage
#
# Output: docs/v6/latest/facts/coverage.json
# ==========================================================================

emit_coverage() {
    local out="${FACTS_DIR}/coverage.json"
    log_info "Emitting facts/coverage.json ..."

    local TARGETS_YAML="${REPO_ROOT}/quality/test-metrics/coverage-targets.yaml"

    # Ordered list of modules that participate in JaCoCo reporting.
    # Must match Maven reactor module directory names.
    local -a MODULES=(
        "yawl-utilities"
        "yawl-elements"
        "yawl-authentication"
        "yawl-engine"
        "yawl-stateless"
        "yawl-resourcing"
        "yawl-worklet"
        "yawl-scheduling"
        "yawl-security"
        "yawl-integration"
        "yawl-monitoring"
        "yawl-control-panel"
    )

    # Aggregate counters (integer arithmetic only — no floating point in shell)
    local TOTAL_INS_COV=0  TOTAL_INS_MISS=0
    local TOTAL_BR_COV=0   TOTAL_BR_MISS=0
    local TOTAL_LN_COV=0   TOTAL_LN_MISS=0

    local MODULE_ENTRIES=""
    local FIRST=true

    for MODULE in "${MODULES[@]}"; do
        local CSV="${REPO_ROOT}/${MODULE}/target/site/jacoco/jacoco.csv"

        if [[ ! -f "${CSV}" ]]; then
            local ENTRY
            ENTRY=$(printf '{"module":"%s","status":"no_report","line_pct":0,"branch_pct":0,"instruction_pct":0,"lines_covered":0,"lines_missed":0}' \
                "${MODULE}")
        else
            # CSV column layout (JaCoCo 0.8.x):
            # GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,
            # BRANCH_MISSED,BRANCH_COVERED,COMPLEXITY_MISSED,COMPLEXITY_COVERED,
            # LINE_MISSED,LINE_COVERED,METHOD_MISSED,METHOD_COVERED
            local INS_MISS=0 INS_COV=0 BR_MISS=0 BR_COV=0 LN_MISS=0 LN_COV=0
            while IFS=',' read -r GROUP PACKAGE CLASS IMISS ICOV BMISS BCOV CMISS CCOV LMISS LCOV MMISS MCOV; do
                # Skip the header row
                [[ "${GROUP}" == "GROUP" ]] && continue
                # Accumulate — treat non-numeric tokens as zero
                INS_MISS=$(( INS_MISS + ${IMISS:-0} ))
                INS_COV=$(( INS_COV  + ${ICOV:-0}  ))
                BR_MISS=$(( BR_MISS  + ${BMISS:-0} ))
                BR_COV=$(( BR_COV    + ${BCOV:-0}  ))
                LN_MISS=$(( LN_MISS  + ${LMISS:-0} ))
                LN_COV=$(( LN_COV    + ${LCOV:-0}  ))
            done < "${CSV}"

            local INS_TOTAL=$(( INS_MISS + INS_COV ))
            local BR_TOTAL=$(( BR_MISS + BR_COV ))
            local LN_TOTAL=$(( LN_MISS + LN_COV ))

            # Compute percentages with one decimal place via bc; fall back to 0
            local INS_PCT=0 BR_PCT=0 LN_PCT=0
            if [[ "${INS_TOTAL}" -gt 0 ]]; then
                INS_PCT=$(echo "scale=1; ${INS_COV} * 100 / ${INS_TOTAL}" | bc -l 2>/dev/null || echo "0")
            fi
            if [[ "${BR_TOTAL}" -gt 0 ]]; then
                BR_PCT=$(echo "scale=1; ${BR_COV} * 100 / ${BR_TOTAL}" | bc -l 2>/dev/null || echo "0")
            fi
            if [[ "${LN_TOTAL}" -gt 0 ]]; then
                LN_PCT=$(echo "scale=1; ${LN_COV} * 100 / ${LN_TOTAL}" | bc -l 2>/dev/null || echo "0")
            fi

            # Accumulate into global totals
            TOTAL_INS_COV=$(( TOTAL_INS_COV + INS_COV ))
            TOTAL_INS_MISS=$(( TOTAL_INS_MISS + INS_MISS ))
            TOTAL_BR_COV=$(( TOTAL_BR_COV + BR_COV ))
            TOTAL_BR_MISS=$(( TOTAL_BR_MISS + BR_MISS ))
            TOTAL_LN_COV=$(( TOTAL_LN_COV + LN_COV ))
            TOTAL_LN_MISS=$(( TOTAL_LN_MISS + LN_MISS ))

            local ENTRY
            ENTRY=$(printf \
                '{"module":"%s","status":"measured","line_pct":%s,"branch_pct":%s,"instruction_pct":%s,"lines_covered":%d,"lines_missed":%d}' \
                "${MODULE}" "${LN_PCT}" "${BR_PCT}" "${INS_PCT}" "${LN_COV}" "${LN_MISS}")
        fi

        if [[ "${FIRST}" == true ]]; then
            MODULE_ENTRIES="${ENTRY}"
            FIRST=false
        else
            MODULE_ENTRIES="${MODULE_ENTRIES},${ENTRY}"
        fi
    done

    # ── Aggregate percentage computation ─────────────────────────────────────
    local AGG_LN_TOTAL=$(( TOTAL_LN_COV + TOTAL_LN_MISS ))
    local AGG_BR_TOTAL=$(( TOTAL_BR_COV + TOTAL_BR_MISS ))
    local AGG_LN_PCT=0 AGG_BR_PCT=0
    if [[ "${AGG_LN_TOTAL}" -gt 0 ]]; then
        AGG_LN_PCT=$(echo "scale=1; ${TOTAL_LN_COV} * 100 / ${AGG_LN_TOTAL}" | bc -l 2>/dev/null || echo "0")
    fi
    if [[ "${AGG_BR_TOTAL}" -gt 0 ]]; then
        AGG_BR_PCT=$(echo "scale=1; ${TOTAL_BR_COV} * 100 / ${AGG_BR_TOTAL}" | bc -l 2>/dev/null || echo "0")
    fi

    # ── Read enforcement targets from coverage-targets.yaml ──────────────────
    # Targets stored as ratios (0.65) in YAML; convert to percentages (65.0).
    local TARGET_LINE_PCT=50
    local TARGET_BRANCH_PCT=40
    if [[ -f "${TARGETS_YAML}" ]]; then
        local RAW_LINE RAW_BRANCH
        RAW_LINE=$(grep -E '^\s+line_ratio:' "${TARGETS_YAML}" | head -1 | awk '{print $2}')
        RAW_BRANCH=$(grep -E '^\s+branch_ratio:' "${TARGETS_YAML}" | head -1 | awk '{print $2}')
        if [[ -n "${RAW_LINE}" ]]; then
            TARGET_LINE_PCT=$(echo "scale=0; ${RAW_LINE} * 100 / 1" | bc -l 2>/dev/null || echo "50")
        fi
        if [[ -n "${RAW_BRANCH}" ]]; then
            TARGET_BRANCH_PCT=$(echo "scale=0; ${RAW_BRANCH} * 100 / 1" | bc -l 2>/dev/null || echo "40")
        fi
    fi

    # ── Boolean gate evaluation ───────────────────────────────────────────────
    local MEETS_LINE="false"
    local MEETS_BRANCH="false"
    if command -v bc >/dev/null 2>&1; then
        [[ $(echo "${AGG_LN_PCT} >= ${TARGET_LINE_PCT}" | bc -l 2>/dev/null) -eq 1 ]] && MEETS_LINE="true"
        [[ $(echo "${AGG_BR_PCT} >= ${TARGET_BRANCH_PCT}" | bc -l 2>/dev/null) -eq 1 ]] && MEETS_BRANCH="true"
    fi

    local TS
    TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "${TS}"
        printf '  "aggregate": {\n'
        printf '    "line_pct": %s,\n'        "${AGG_LN_PCT}"
        printf '    "branch_pct": %s,\n'      "${AGG_BR_PCT}"
        printf '    "lines_covered": %d,\n'   "${TOTAL_LN_COV}"
        printf '    "lines_missed": %d,\n'    "${TOTAL_LN_MISS}"
        printf '    "target_line_pct": %s,\n' "${TARGET_LINE_PCT}"
        printf '    "target_branch_pct": %s,\n' "${TARGET_BRANCH_PCT}"
        printf '    "meets_line_target": %s,\n'   "${MEETS_LINE}"
        printf '    "meets_branch_target": %s,\n' "${MEETS_BRANCH}"
        printf '    "note": "Run mvn test then bash scripts/observatory/observatory.sh --facts to update"\n'
        printf '  },\n'
        printf '  "modules": [%s]\n' "${MODULE_ENTRIES}"
        printf '}\n'
    } > "${out}"

    log_info "Coverage fact written: ${out}"
}
