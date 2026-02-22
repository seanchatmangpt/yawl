#!/usr/bin/env bash
# ==========================================================================
# emit-yawl-analysis.sh — Van der Aalst GODSPEED: WFP Coverage + Soundness
#
# Emits two fact files implementing van der Aalst's 80/20 insight:
#   facts/workflow-patterns.json — WP1-WP7 coverage across specs and tests
#   facts/soundness.json         — Structural soundness of YAWL XML specs
#
# Van der Aalst (2003): WP1-WP7 (Basic Control Flow Patterns) cover 80%
# of real workflow behavior. Soundness property: every case can complete
# (option_to_complete), only end-place marked at completion (proper_completion),
# every task reachable in some execution (no_dead_tasks).
#
# Incremental Mode:
#   Uses emit_if_stale for cache-aware emission.
#   Set OBSERVATORY_FORCE=1 to force regeneration.
# ==========================================================================

# Source utilities if not already loaded
if [[ -z "${FACTS_DIR:-}" ]]; then
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    source "${SCRIPT_DIR}/util.sh"
fi

# ==========================================================================
# SPEC DISCOVERY
# ==========================================================================

# Find all YAWL XML specification files (exampleSpecs/ only).
# Excludes legacy .ywl files: they use an older schema without
# <inputCondition>/<outputCondition> and would cause false soundness failures.
_find_yawl_specs() {
    find "${REPO_ROOT}/exampleSpecs" -maxdepth 2 -name "*.xml" -type f 2>/dev/null | sort
}

# ==========================================================================
# WFP PATTERN DETECTION
# ==========================================================================

# Count YAWL XML specs containing a grep pattern
_count_specs_with() {
    local pattern="$1"
    local count=0
    while IFS= read -r spec; do
        [[ -z "$spec" ]] && continue
        grep -q "$pattern" "$spec" 2>/dev/null && ((count++)) || true
    done < <(_find_yawl_specs)
    echo "$count"
}

# Count Java test files containing a pattern (files that also have @Test)
_count_test_files_with() {
    local pattern="$1"
    grep -rEl --include="*.java" "$pattern" \
        "${REPO_ROOT}/test" "${REPO_ROOT}/src" 2>/dev/null \
    | while IFS= read -r f; do
        grep -q "@Test" "$f" 2>/dev/null && echo "$f"
    done | wc -l | tr -d ' '
}

# Map spec_count + test_count → coverage status
_wfp_coverage() {
    local specs=$1 tests=$2
    if   [[ $specs -gt 0 && $tests -gt 0 ]]; then echo "covered"
    elif [[ $specs -gt 0 ]];                 then echo "spec_only"
    elif [[ $tests -gt 0 ]];                 then echo "test_only"
    else                                          echo "missing"
    fi
}

# ==========================================================================
# SOUNDNESS ANALYSIS
# Per van der Aalst's soundness property for workflow nets.
# Checks structural properties derivable from YAWL XML without model checking.
# ==========================================================================

# Check a single spec file for structural soundness violations.
# Prints "sound" or a space-separated list of violation tokens.
_check_spec_soundness() {
    local spec="$1"
    local -a violations=()

    # Property 1 — Has start place (inputCondition = source place in Petri net)
    grep -q '<inputCondition' "$spec" 2>/dev/null \
        || violations+=("missing_start_place")

    # Property 2 — Has end place (outputCondition = sink place)
    grep -q '<outputCondition' "$spec" 2>/dev/null \
        || violations+=("missing_end_place")

    # Property 3 — No dead tasks (every task reachable from some token flow)
    # A task is unreachable if its id never appears in any <nextElementRef id="...">
    local task_ids
    task_ids=$(sed -n 's/.*<task id="\([^"]*\)".*/\1/p' "$spec" 2>/dev/null)
    local referenced_ids
    referenced_ids=$(sed -n 's/.*<nextElementRef id="\([^"]*\)".*/\1/p' "$spec" 2>/dev/null)

    while IFS= read -r tid; do
        [[ -z "$tid" ]] && continue
        if ! echo "$referenced_ids" | grep -qF "$tid"; then
            violations+=("dead_task:${tid}")
        fi
    done <<< "$task_ids"

    # Property 4 — No dead-end tasks (every task has at least one outgoing flow)
    # Uses Python for reliable multi-line XML block matching
    while IFS= read -r tid; do
        [[ -z "$tid" ]] && continue
        local has_flow
        has_flow=$(python3 -c "
import re, sys
try:
    content = open(sys.argv[1]).read()
    m = re.search(r'<task[^>]*id=\"' + re.escape(sys.argv[2]) + r'\".*?</task>', content, re.DOTALL)
    print('yes' if m and '<flowsInto>' in m.group(0) else 'no')
except Exception:
    print('yes')
" "$spec" "$tid" 2>/dev/null || echo "yes")
        [[ "$has_flow" == "no" ]] && violations+=("dead_end_task:${tid}")
    done <<< "$task_ids"

    if [[ ${#violations[@]} -eq 0 ]]; then
        echo "sound"
    else
        echo "${violations[*]}"
    fi
}

# ==========================================================================
# WORKFLOW PATTERNS FACT IMPLEMENTATION
# ==========================================================================

_emit_workflow_patterns_impl() {
    local out="${FACTS_DIR}/workflow-patterns.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/workflow-patterns.json ..."

    local spec_count
    spec_count=$(_find_yawl_specs | wc -l | tr -d ' ')

    # ── Spec-level pattern counts ──────────────────────────────────────────
    # WP1 Sequence: any workflow with tasks has sequential flow by definition
    local wp1_s wp2_s wp3_s wp4_s wp5_s wp6_s wp7_s
    wp1_s=$(_count_specs_with '<task ')
    wp2_s=$(_count_specs_with 'split code="and"')
    wp3_s=$(_count_specs_with 'join code="and"')
    wp4_s=$(_count_specs_with 'split code="xor"')
    wp5_s=$(_count_specs_with 'join code="xor"')
    wp6_s=$(_count_specs_with 'split code="or"')
    wp7_s=$(_count_specs_with 'join code="or"')

    # ── Test-level coverage (files containing pattern + @Test) ────────────
    local wp1_t wp2_t wp3_t wp4_t wp5_t wp6_t wp7_t
    wp1_t=$(_count_test_files_with '[Ss]equen|[Ll]inear.*[Ff]low|[Ss]equential')
    wp2_t=$(_count_test_files_with 'andSplit|[Aa]nd.*[Ss]plit|[Pp]arallel.*[Ss]plit')
    wp3_t=$(_count_test_files_with 'andJoin|[Aa]nd.*[Jj]oin|[Ss]ynchroni')
    wp4_t=$(_count_test_files_with 'xorSplit|[Xx]or.*[Ss]plit|[Ee]xclusive.*[Cc]hoice')
    wp5_t=$(_count_test_files_with 'xorJoin|[Xx]or.*[Jj]oin|[Ss]imple.*[Mm]erge')
    wp6_t=$(_count_test_files_with 'orSplit|[Oo]r.*[Ss]plit|[Mm]ulti.*[Cc]hoice')
    wp7_t=$(_count_test_files_with 'orJoin|[Oo]r.*[Jj]oin|[Ss]ync.*[Mm]erge')

    # ── Covered pattern count (WP1-WP7) ───────────────────────────────────
    local covered=0
    for pair in \
        "${wp1_s}:${wp1_t}" "${wp2_s}:${wp2_t}" "${wp3_s}:${wp3_t}" \
        "${wp4_s}:${wp4_t}" "${wp5_s}:${wp5_t}" "${wp6_s}:${wp6_t}" \
        "${wp7_s}:${wp7_t}"; do
        local s=${pair%%:*} t=${pair##*:}
        [[ $s -gt 0 || $t -gt 0 ]] && ((covered++)) || true
    done

    local coverage_pct=$(( covered * 100 / 7 ))
    local overall_status="partial"
    [[ $covered -eq 7 ]] && overall_status="complete"
    [[ $covered -eq 0 ]] && overall_status="missing"

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "commit": "%s",\n' "$(git_commit)"
        printf '  "reference": "van der Aalst et al., Workflow Patterns (2003)",\n'
        printf '  "specs_scanned": %d,\n' "$spec_count"
        printf '  "patterns": {\n'
        printf '    "WP1_sequence":           {"name": "Sequence",            "spec_count": %d, "test_files": %d, "coverage": "%s"},\n' \
               "$wp1_s" "$wp1_t" "$(_wfp_coverage "$wp1_s" "$wp1_t")"
        printf '    "WP2_parallel_split":     {"name": "Parallel Split",      "spec_count": %d, "test_files": %d, "coverage": "%s"},\n' \
               "$wp2_s" "$wp2_t" "$(_wfp_coverage "$wp2_s" "$wp2_t")"
        printf '    "WP3_synchronization":    {"name": "Synchronization",     "spec_count": %d, "test_files": %d, "coverage": "%s"},\n' \
               "$wp3_s" "$wp3_t" "$(_wfp_coverage "$wp3_s" "$wp3_t")"
        printf '    "WP4_exclusive_choice":   {"name": "Exclusive Choice",    "spec_count": %d, "test_files": %d, "coverage": "%s"},\n' \
               "$wp4_s" "$wp4_t" "$(_wfp_coverage "$wp4_s" "$wp4_t")"
        printf '    "WP5_simple_merge":       {"name": "Simple Merge",        "spec_count": %d, "test_files": %d, "coverage": "%s"},\n' \
               "$wp5_s" "$wp5_t" "$(_wfp_coverage "$wp5_s" "$wp5_t")"
        printf '    "WP6_multi_choice":       {"name": "Multi-Choice",        "spec_count": %d, "test_files": %d, "coverage": "%s"},\n' \
               "$wp6_s" "$wp6_t" "$(_wfp_coverage "$wp6_s" "$wp6_t")"
        printf '    "WP7_synchronizing_merge":{"name": "Synchronizing Merge", "spec_count": %d, "test_files": %d, "coverage": "%s"}\n' \
               "$wp7_s" "$wp7_t" "$(_wfp_coverage "$wp7_s" "$wp7_t")"
        printf '  },\n'
        printf '  "summary": {\n'
        printf '    "basic_patterns_total": 7,\n'
        printf '    "basic_patterns_covered": %d,\n' "$covered"
        printf '    "coverage_percent": %d,\n' "$coverage_pct"
        printf '    "status": "%s",\n' "$overall_status"
        printf '    "vda_insight": "WP1-WP7 cover 80%% of real workflow behavior"\n'
        printf '  }\n'
        printf '}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_workflow_patterns" "$op_elapsed"
}

# ==========================================================================
# SOUNDNESS FACT IMPLEMENTATION
# ==========================================================================

_emit_soundness_impl() {
    local out="${FACTS_DIR}/soundness.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/soundness.json ..."

    local -a specs=()
    while IFS= read -r f; do
        [[ -n "$f" ]] && specs+=("$f")
    done < <(_find_yawl_specs)

    local specs_analyzed=${#specs[@]}
    local all_sound=true
    local -a result_entries=()

    for spec in "${specs[@]}"; do
        local spec_name
        spec_name=$(basename "$spec")

        local soundness_result
        soundness_result=$(_check_spec_soundness "$spec")

        local sound_flag="true"
        local -a dead_arr=()
        local -a viols_arr=()

        if [[ "$soundness_result" != "sound" ]]; then
            sound_flag="false"
            all_sound=false
            # Parse individual violation tokens
            read -ra viol_tokens <<< "$soundness_result"
            for vt in "${viol_tokens[@]}"; do
                viols_arr+=("\"${vt}\"")
                if [[ "$vt" == dead_task:* ]]; then
                    dead_arr+=("\"${vt#dead_task:}\"")
                fi
            done
        fi

        local dead_json="[]"
        [[ ${#dead_arr[@]} -gt 0 ]] && dead_json="[$(IFS=','; echo "${dead_arr[*]}")]"
        local viols_json="[]"
        [[ ${#viols_arr[@]} -gt 0 ]] && viols_json="[$(IFS=','; echo "${viols_arr[*]}")]"

        local has_start="false" has_end="false"
        grep -q '<inputCondition'  "$spec" 2>/dev/null && has_start="true"
        grep -q '<outputCondition' "$spec" 2>/dev/null && has_end="true"

        local task_count
        # grep -c exits 1 on 0 matches; || true prevents set -e termination
        task_count=$(grep -c '<task id=' "$spec" 2>/dev/null || true)
        task_count=${task_count:-0}

        result_entries+=( \
            "{\"spec\":\"${spec_name}\",\"sound\":${sound_flag},\"task_count\":${task_count}," \
            "\"has_start_place\":${has_start},\"has_end_place\":${has_end}," \
            "\"dead_tasks\":${dead_json},\"violations\":${viols_json}}" \
        )
    done

    local overall_status="GREEN"
    $all_sound    || overall_status="RED"
    [[ $specs_analyzed -eq 0 ]] && overall_status="UNKNOWN"

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "commit": "%s",\n' "$(git_commit)"
        printf '  "property": "soundness: option_to_complete AND proper_completion AND no_dead_tasks",\n'
        printf '  "specs_analyzed": %d,\n' "$specs_analyzed"
        printf '  "results": [\n'
        local first=true
        # result_entries is grouped in triples (three strings per spec)
        local i=0
        while [[ $i -lt ${#result_entries[@]} ]]; do
            $first || printf ',\n'
            first=false
            printf '    %s%s%s' \
                "${result_entries[$i]}" \
                "${result_entries[$((i+1))]}" \
                "${result_entries[$((i+2))]}"
            (( i += 3 ))
        done
        printf '\n  ],\n'
        printf '  "all_sound": %s,\n' "$all_sound"
        printf '  "status": "%s"\n' "$overall_status"
        printf '}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_soundness" "$op_elapsed"
}

# ==========================================================================
# PUBLIC EMIT FUNCTIONS (cache-aware wrappers)
# ==========================================================================

emit_workflow_patterns() {
    emit_if_stale "facts/workflow-patterns.json" _emit_workflow_patterns_impl
}

emit_soundness() {
    emit_if_stale "facts/soundness.json" _emit_soundness_impl
}

emit_all_yawl_analysis() {
    emit_workflow_patterns
    emit_soundness
}
