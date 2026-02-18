#!/usr/bin/env bash
# ==========================================================================
# incremental.sh — Incremental Analysis for Observatory
#
# Skip regeneration if inputs unchanged based on mtime comparison.
# Provides 90%+ time savings on subsequent runs with no changes.
#
# Usage:
#   source lib/incremental.sh
#
#   if needs_regeneration "facts/modules.json" "pom.xml" ".mvn/"; then
#       emit_modules
#   else
#       log_info "Skipping modules.json (up to date)"
#   fi
# ==========================================================================

# ── State file for tracking input hashes ──────────────────────────────────
INCREMENTAL_STATE_DIR="${OUT_DIR}/.incremental"
mkdir -p "$INCREMENTAL_STATE_DIR" 2>/dev/null || true

# ── Get the newest mtime from a list of paths ──────────────────────────────
get_newest_mtime() {
    local newest=0
    for path in "$@"; do
        if [[ -e "${REPO_ROOT}/${path}" ]]; then
            local mtime
            mtime=$(stat -f %m "${REPO_ROOT}/${path}" 2>/dev/null || stat -c %Y "${REPO_ROOT}/${path}" 2>/dev/null || echo "0")
            [[ "$mtime" -gt "$newest" ]] && newest="$mtime"
        fi
    done
    echo "$newest"
}

# ── Get the mtime of an output file ────────────────────────────────────────
get_output_mtime() {
    local output_file="$1"
    if [[ -f "$output_file" ]]; then
        stat -f %m "$output_file" 2>/dev/null || stat -c %Y "$output_file" 2>/dev/null || echo "0"
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
                file_mtime=$(stat -f %m "$file" 2>/dev/null || stat -c %Y "$file" 2>/dev/null || echo "0")
                if [[ "$file_mtime" -gt "$output_mtime" ]]; then
                    return 0
                fi
            done < <(eval "find ${REPO_ROOT}/${input} -type f 2>/dev/null" | head -100)
        elif [[ -e "$input_path" ]]; then
            local input_mtime
            input_mtime=$(stat -f %m "$input_path" 2>/dev/null || stat -c %Y "$input_path" 2>/dev/null || echo "0")
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

    # Check facts
    for fact_file in "$FACTS_DIR"/*.json; do
        [[ -f "$fact_file" ]] || continue
        local basename
        basename=$(basename "$fact_file")

        case "$basename" in
            modules.json)
                if needs_regeneration "$fact_file" "pom.xml"; then
                    ((facts_stale++))
                else
                    ((facts_upto_date++))
                fi
                ;;
            reactor.json)
                if needs_regeneration "$fact_file" "pom.xml" "yawl-*/pom.xml"; then
                    ((facts_stale++))
                else
                    ((facts_upto_date++))
                fi
                ;;
            *)
                # Generic check
                if needs_regeneration "$fact_file" "src/"; then
                    ((facts_stale++))
                else
                    ((facts_upto_date++))
                fi
                ;;
        esac
    done

    # Check diagrams
    for diagram_file in "$DIAGRAMS_DIR"/*.mmd; do
        [[ -f "$diagram_file" ]] || continue
        if needs_regeneration "$diagram_file" "src/"; then
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
  "force_mode": $(is_force_mode && echo "true" || echo "false")
}
EOF
}
