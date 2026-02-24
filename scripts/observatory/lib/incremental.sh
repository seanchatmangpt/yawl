#!/usr/bin/env bash
set -euo pipefail
# ==========================================================================
# incremental.sh — Incremental Analysis for Observatory
#
# Skip regeneration if inputs unchanged based on mtime comparison.
# Provides 90%+ time savings on subsequent runs with no changes.
#
# Features:
#   - Cache statistics tracking (hits/misses/ratio)
#   - Registry-based dependency lookup
#   - Force mode support (OBSERVATORY_FORCE=1)
#
# Usage:
#   source lib/incremental.sh
#   source lib/dependency-registry.sh
#
#   # Using wrapper (recommended):
#   emit_if_stale "facts/modules.json" emit_modules
#
#   # Manual check:
#   if needs_regeneration "facts/modules.json" "pom.xml"; then
#       emit_modules
#   else
#       log_info "Skipping modules.json (up to date)"
#   fi
# ==========================================================================

# ── State file for tracking input hashes ──────────────────────────────────
INCREMENTAL_STATE_DIR="${OUT_DIR}/.incremental"
mkdir -p "$INCREMENTAL_STATE_DIR" 2>/dev/null || true

# ── Cache Statistics (Array-based tracking) ─────────────────────────────────
# Per-output cache status tracking for detailed reporting
declare -A CACHE_STATUS=()        # ["facts/modules.json"]="hit|miss|skipped"
declare -A CACHE_TIMINGS=()       # ["facts/modules.json"]="123" (ms saved or spent)
declare -i CACHE_TOTAL_HITS=0
declare -i CACHE_TOTAL_MISSES=0
declare -i CACHE_TOTAL_SKIPPED=0

# Reset cache statistics (call at start of observatory run)
reset_cache_stats() {
    CACHE_STATUS=()
    CACHE_TIMINGS=()
    CACHE_TOTAL_HITS=0
    CACHE_TOTAL_MISSES=0
    CACHE_TOTAL_SKIPPED=0
}

# Record a cache event with timing
# Args: output_file status (hit|miss|skipped) timing_ms
record_cache_event() {
    local output_file="$1"
    local status="$2"
    local timing_ms="${3:-0}"

    CACHE_STATUS["$output_file"]="$status"
    CACHE_TIMINGS["$output_file"]="$timing_ms"

    case "$status" in
        hit)     ((CACHE_TOTAL_HITS++)) ;;
        miss)    ((CACHE_TOTAL_MISSES++)) ;;
        skipped) ((CACHE_TOTAL_SKIPPED++)) ;;
    esac
}

# Get cache hit ratio (0.0 - 1.0)
get_cache_hit_ratio() {
    local total=$((CACHE_TOTAL_HITS + CACHE_TOTAL_MISSES))
    if [[ $total -eq 0 ]]; then
        echo "0.0"
    else
        local ratio
        ratio=$(echo "scale=2; $CACHE_TOTAL_HITS / $total" | bc 2>/dev/null || echo "0")
        # Ensure leading zero for values < 1
        if [[ "$ratio" == .* ]]; then
            echo "0$ratio"
        else
            echo "$ratio"
        fi
    fi
}

# Get cache statistics as JSON (detailed per-output)
get_cache_stats_json() {
    local ratio
    ratio=$(get_cache_hit_ratio)
    local total=$((CACHE_TOTAL_HITS + CACHE_TOTAL_MISSES))

    # Build detailed status entries
    local status_entries=""
    local first=true
    for output in "${!CACHE_STATUS[@]}"; do
        $first || status_entries+=","
        first=false
        local status="${CACHE_STATUS[$output]}"
        local timing="${CACHE_TIMINGS[$output]:-0}"
        status_entries+=$'\n'"      \"$(json_escape "$output")\": {\"status\": \"$status\", \"timing_ms\": $timing}"
    done

    cat << EOF
{
  "summary": {
    "hits": $CACHE_TOTAL_HITS,
    "misses": $CACHE_TOTAL_MISSES,
    "skipped": $CACHE_TOTAL_SKIPPED,
    "hit_ratio": $ratio,
    "total_checks": $total
  },
  "details": {${status_entries}
  }
}
EOF
}

# Get simplified cache summary for receipt embedding
get_cache_summary_json() {
    local ratio
    ratio=$(get_cache_hit_ratio)
    cat << EOF
{"hits": $CACHE_TOTAL_HITS, "misses": $CACHE_TOTAL_MISSES, "skipped": $CACHE_TOTAL_SKIPPED, "hit_ratio": $ratio}
EOF
}

# Backwards compatible globals for existing code
CACHE_HITS=${CACHE_TOTAL_HITS}
CACHE_MISSES=${CACHE_TOTAL_MISSES}
CACHE_SKIPPED=${CACHE_TOTAL_SKIPPED}

# ── Get the newest mtime from a list of paths ──────────────────────────────
get_newest_mtime() {
    local newest=0
    for path in "$@"; do
        if [[ -e "${REPO_ROOT}/${path}" ]]; then
            local mtime
            mtime=$(stat -c %Y "${REPO_ROOT}/${path}" 2>/dev/null || stat -f %m "${REPO_ROOT}/${path}" 2>/dev/null || echo "0")
            [[ "$mtime" -gt "$newest" ]] && newest="$mtime"
        fi
    done
    echo "$newest"
}

# ── Get the mtime of an output file ────────────────────────────────────────
get_output_mtime() {
    local output_file="$1"
    if [[ -f "$output_file" ]]; then
        stat -c %Y "$output_file" 2>/dev/null || stat -f %m "$output_file" 2>/dev/null || echo "0"
    else
        echo "0"
    fi
}

# ── Check if output needs regeneration ─────────────────────────────────────
# Args: output_file input_path1 [input_path2 ...]
# Returns: 0 if needs regeneration, 1 if up-to-date
needs_regeneration() {
    local output_file="$1"
    shift
    local inputs=("$@")

    # If output doesn't exist, always regenerate
    if [[ ! -f "$output_file" ]]; then
        return 0
    fi

    # Get output mtime
    local output_mtime
    output_mtime=$(get_output_mtime "$output_file")

    # Check each input
    for input in "${inputs[@]}"; do
        local input_path="${REPO_ROOT}/${input}"

        # Handle glob patterns
        if [[ "$input" == *"*"* ]]; then
            # For globs, check if any matching file is newer
            while IFS= read -r file; do
                [[ -z "$file" ]] && continue
                local file_mtime
                file_mtime=$(stat -c %Y "$file" 2>/dev/null || stat -f %m "$file" 2>/dev/null || echo "0")
                if [[ "$file_mtime" -gt "$output_mtime" ]]; then
                    return 0
                fi
            done < <(eval "find ${REPO_ROOT}/${input} -type f 2>/dev/null" | head -100)
        elif [[ -e "$input_path" ]]; then
            local input_mtime
            input_mtime=$(stat -c %Y "$input_path" 2>/dev/null || stat -f %m "$input_path" 2>/dev/null || echo "0")
            if [[ "$input_mtime" -gt "$output_mtime" ]]; then
                return 0
            fi
        fi
    done

    # All inputs are older than output, no regeneration needed
    return 1
}

# ── Wrapper for incremental emit functions ────────────────────────────────
# Usage: incremental_emit "output_file" "input1 input2 ..." emit_function
incremental_emit() {
    local output_file="$1"
    local inputs="$2"
    local emit_func="$3"

    # Convert inputs string to array
    read -ra input_array <<< "$inputs"

    if needs_regeneration "$output_file" "${input_array[@]}"; then
        $emit_func
        return 0
    else
        log_info "Skipping $(basename "$output_file") (up to date)"
        return 0
    fi
}

# ── Force flag to bypass incremental checks ────────────────────────────────
# Set OBSERVATORY_FORCE=1 to force full regeneration
is_force_mode() {
    [[ "${OBSERVATORY_FORCE:-0}" == "1" ]]
}

# ── Check with force mode support ──────────────────────────────────────────
needs_regeneration_with_force() {
    if is_force_mode; then
        return 0  # Always regenerate in force mode
    fi
    needs_regeneration "$@"
}

# ── Get incremental status summary ────────────────────────────────────────
get_incremental_status() {
    local facts_upto_date=0
    local facts_stale=0
    local diagrams_upto_date=0
    local diagrams_stale=0

    # Check facts using registry
    for fact_file in "$FACTS_DIR"/*.json; do
        [[ -f "$fact_file" ]] || continue
        local basename
        basename=$(basename "$fact_file")
        local output_key="facts/${basename}"

        # Get inputs from registry
        local inputs
        inputs=$(get_inputs "$output_key" 2>/dev/null || echo "src/")

        # Convert to array and check
        read -ra input_array <<< "$inputs"
        if needs_regeneration "$fact_file" "${input_array[@]}"; then
            ((facts_stale++))
        else
            ((facts_upto_date++))
        fi
    done

    # Check diagrams using registry
    for diagram_file in "$DIAGRAMS_DIR"/*.mmd; do
        [[ -f "$diagram_file" ]] || continue
        local basename
        basename=$(basename "$diagram_file")
        local output_key="diagrams/${basename}"

        local inputs
        inputs=$(get_inputs "$output_key" 2>/dev/null || echo "src/")

        read -ra input_array <<< "$inputs"
        if needs_regeneration "$diagram_file" "${input_array[@]}"; then
            ((diagrams_stale++))
        else
            ((diagrams_upto_date++))
        fi
    done

    cat << EOF
{
  "facts": {
    "up_to_date": $facts_upto_date,
    "stale": $facts_stale
  },
  "diagrams": {
    "up_to_date": $diagrams_upto_date,
    "stale": $diagrams_stale
  },
  "cache": $(get_cache_summary_json),
  "force_mode": $(is_force_mode && echo "true" || echo "false")
}
EOF
}

# ==========================================================================
# EMIT_IF_STALE WRAPPER
# ==========================================================================

# Wrapper that checks staleness using dependency registry before emitting
# Args: output_file emit_function [additional_inputs...]
#
# Usage:
#   emit_if_stale "facts/modules.json" emit_modules
#   emit_if_stale "facts/reactor.json" emit_reactor
#
# Returns: 0 on success (emitted or cached), 1 on error
#
# Behavior:
#   1. Look up inputs from DEPENDENCY_REGISTRY
#   2. Check if output needs regeneration
#   3. If stale: call emit_function, increment CACHE_MISSES
#   4. If fresh: skip, increment CACHE_HITS
#
# Debug Mode: Set INCREMENTAL_DEBUG=1 for verbose logging
emit_if_stale() {
    local output_file="$1"
    local emit_func="$2"
    shift 2
    local extra_inputs=("$@")

    # Get inputs from registry (if available)
    local registry_inputs=""
    if declare -p DEPENDENCY_REGISTRY >/dev/null 2>&1; then
        registry_inputs=$(get_inputs "$output_file" 2>/dev/null || echo "")
    fi

    # Combine registry inputs with extra inputs
    local all_inputs=()
    if [[ -n "$registry_inputs" ]]; then
        read -ra reg_arr <<< "$registry_inputs"
        all_inputs=("${reg_arr[@]}")
    fi
    all_inputs+=("${extra_inputs[@]}")

    # Default to src and pom.xml if no inputs found
    if [[ ${#all_inputs[@]} -eq 0 ]]; then
        all_inputs=("src" "pom.xml")
    fi

    # Build full output path
    local full_output_path
    case "$output_file" in
        facts/*)
            full_output_path="${FACTS_DIR}/${output_file#facts/}"
            ;;
        diagrams/*)
            full_output_path="${DIAGRAMS_DIR}/${output_file#diagrams/}"
            ;;
        *)
            full_output_path="${OUT_DIR}/${output_file}"
            ;;
    esac

    # Debug logging for cache decisions
    if [[ "${INCREMENTAL_DEBUG:-0}" == "1" ]]; then
        log_info "[DEBUG] emit_if_stale: output=$output_file inputs=${all_inputs[*]} path=$full_output_path"
    fi

    # Record start time for timing
    local check_start
    check_start=$(epoch_ms)

    # Check staleness (with force mode support)
    if needs_regeneration_with_force "$full_output_path" "${all_inputs[@]}"; then
        # Output is stale or doesn't exist - regenerate
        local emit_start emit_elapsed
        emit_start=$(epoch_ms)

        log_info "Cache MISS: $output_file (regenerating)"

        # Call the emit function
        if declare -f "$emit_func" >/dev/null; then
            "$emit_func"
            local result=$?
            emit_elapsed=$(( $(epoch_ms) - emit_start ))
            record_cache_event "$output_file" "miss" "$emit_elapsed"
            ((CACHE_TOTAL_MISSES++))
            CACHE_MISSES=${CACHE_TOTAL_MISSES}

            # Debug logging for miss timing
            if [[ "${INCREMENTAL_DEBUG:-0}" == "1" ]]; then
                log_info "[DEBUG] Cache MISS completed: $output_file in ${emit_elapsed}ms (result=$result)"
            fi
            return $result
        else
            log_error "emit_if_stale: Function '$emit_func' not found"
            record_cache_event "$output_file" "error" "0"
            return 1
        fi
    else
        # Output is fresh - use cache
        local time_saved=$(( $(epoch_ms) - check_start ))
        record_cache_event "$output_file" "hit" "$time_saved"
        ((CACHE_TOTAL_HITS++))
        CACHE_HITS=${CACHE_TOTAL_HITS}
        log_ok "Cache HIT: $output_file (up to date, saved ~${time_saved}ms)"

        # Debug logging for hit
        if [[ "${INCREMENTAL_DEBUG:-0}" == "1" ]]; then
            log_info "[DEBUG] Cache HIT: $output_file exists at $full_output_path"
        fi
        return 0
    fi
}

# Variant that always emits (ignores cache) for critical outputs
# Args: output_file emit_function
emit_force() {
    local output_file="$1"
    local emit_func="$2"

    local emit_start emit_elapsed
    emit_start=$(epoch_ms)

    log_info "Force emit: $output_file"

    if declare -f "$emit_func" >/dev/null; then
        "$emit_func"
        local result=$?
        emit_elapsed=$(( $(epoch_ms) - emit_start ))
        record_cache_event "$output_file" "forced" "$emit_elapsed"
        ((CACHE_TOTAL_MISSES++))
        CACHE_MISSES=${CACHE_TOTAL_MISSES}
        return $result
    else
        log_error "emit_force: Function '$emit_func' not found"
        record_cache_event "$output_file" "error" "0"
        return 1
    fi
}

# Emit with fallback to default if function doesn't exist
# Args: output_file emit_function default_function
emit_with_fallback() {
    local output_file="$1"
    local emit_func="$2"
    local default_func="$3"

    if declare -f "$emit_func" >/dev/null; then
        emit_if_stale "$output_file" "$emit_func"
    elif declare -f "$default_func" >/dev/null; then
        log_warn "Using fallback emitter for $output_file"
        emit_if_stale "$output_file" "$default_func"
    else
        log_error "No emitter available for $output_file"
        return 1
    fi
}

# Print cache summary for logging
print_cache_summary() {
    local ratio
    ratio=$(get_cache_hit_ratio)
    local total=$((CACHE_TOTAL_HITS + CACHE_TOTAL_MISSES))

    echo "Cache Summary: ${CACHE_TOTAL_HITS} hits, ${CACHE_TOTAL_MISSES} misses, ${CACHE_TOTAL_SKIPPED} skipped, ${total} total (${ratio} hit ratio)"
}

# Export cache stats to file for receipt consumption
export_cache_stats() {
    local output_file="${1:-${PERF_DIR}/cache-stats.json}"
    mkdir -p "$(dirname "$output_file")" 2>/dev/null
    get_cache_stats_json > "$output_file"
    echo "$output_file"
}
