#!/usr/bin/env bash
# ==========================================================================
# detect-semantic-changes.sh — Detect Semantic vs Text-Only Changes
#
# Compares current module semantics with cached versions to determine
# if changes are actual code changes or just formatting/whitespace.
#
# Outputs a detailed report showing:
# - Modules with semantic changes (must recompile)
# - Modules with text-only changes (can skip recompile)
# - File-level details of what changed
#
# Usage:
#   bash scripts/detect-semantic-changes.sh <module>    # check one module
#   bash scripts/detect-semantic-changes.sh --all       # check all changed modules
#   bash scripts/detect-semantic-changes.sh --graph     # output dependency impact
#
# Output:
#   JSON report with semantic and text-only changes
#   Can be piped to tools for impact analysis
#
# Environment:
#   VERBOSE=1          Show detailed diffs
#   SHOW_IMPACT=1      Compute transitive dependencies affected
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Configuration
readonly SEMANTIC_CACHE_DIR="${REPO_ROOT}/.yawl/cache/semantic-hashes"
readonly VERBOSE="${VERBOSE:-0}"
readonly SHOW_IMPACT="${SHOW_IMPACT:-0}"

# Color codes
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_CYAN='\033[96m'
readonly C_YELLOW='\033[93m'

# ============================================================================
# Utility Functions
# ============================================================================

log() {
    if [[ "$VERBOSE" == "1" ]]; then
        echo -e "${C_CYAN}[DETECT]${C_RESET} $*" >&2
    fi
}

error() {
    echo -e "${C_RED}[DETECT]${C_RESET} ERROR: $*" >&2
}

# Detect which Java files changed in a module since last commit
detect_changed_files() {
    local module="$1"

    # Get files changed relative to HEAD (staged + unstaged)
    git diff --name-only HEAD -- "${module}/src/main/java/*.java" 2>/dev/null | grep -E '\.java$' || true
    git diff --name-only --cached -- "${module}/src/main/java/*.java" 2>/dev/null | grep -E '\.java$' || true
}

# Compute semantic hash for current module version
compute_current_hash() {
    local module="$1"

    bash "${SCRIPT_DIR}/compute-semantic-hash.sh" "$module" 2>/dev/null || echo "null"
}

# Load cached semantic hash
load_cached_hash() {
    local module="$1"
    local cache_file="${SEMANTIC_CACHE_DIR}/${module}.json"

    if [[ -f "$cache_file" ]]; then
        cat "$cache_file"
    else
        echo "null"
    fi
}

# Compare two semantic hashes and determine change type
analyze_changes() {
    local current="$1"
    local cached="$2"
    local module="$3"

    # Extract hashes
    local current_hash
    local cached_hash

    current_hash=$(echo "$current" | jq -r '.hash // empty' 2>/dev/null) || current_hash=""
    cached_hash=$(echo "$cached" | jq -r '.hash // empty' 2>/dev/null) || cached_hash=""

    # No cached version = must recompile (first run or cache missing)
    if [[ -z "$cached_hash" ]]; then
        cat <<EOF
{
  "module": "$module",
  "change_type": "new",
  "has_semantic_change": true,
  "reason": "no_cached_version",
  "current_hash": "$current_hash",
  "cached_hash": null,
  "file_count_current": $(echo "$current" | jq '.file_count // 0'),
  "file_count_cached": 0,
  "must_recompile": true
}
EOF
        return 0
    fi

    # Hash match = no semantic change (formatting only)
    if [[ "$current_hash" == "$cached_hash" ]]; then
        cat <<EOF
{
  "module": "$module",
  "change_type": "text_only",
  "has_semantic_change": false,
  "reason": "hash_match",
  "current_hash": "$current_hash",
  "cached_hash": "$cached_hash",
  "file_count_current": $(echo "$current" | jq '.file_count // 0'),
  "file_count_cached": $(echo "$cached" | jq '.file_count // 0'),
  "must_recompile": false
}
EOF
        return 0
    fi

    # Hash mismatch = semantic change detected (real code change)
    # Analyze file-level changes
    local file_changes
    file_changes=$(analyze_file_changes "$current" "$cached")

    cat <<EOF
{
  "module": "$module",
  "change_type": "semantic",
  "has_semantic_change": true,
  "reason": "hash_mismatch",
  "current_hash": "$current_hash",
  "cached_hash": "$cached_hash",
  "file_count_current": $(echo "$current" | jq '.file_count // 0'),
  "file_count_cached": $(echo "$cached" | jq '.file_count // 0'),
  "must_recompile": true,
  "file_changes": $file_changes
}
EOF
}

# Analyze file-level changes between two semantic hashes
analyze_file_changes() {
    local current="$1"
    local cached="$2"

    local current_files
    local cached_files
    local file_changes="[]"

    current_files=$(echo "$current" | jq '.files // []')
    cached_files=$(echo "$cached" | jq '.files // []')

    # Build map of current files by path
    local -A current_map
    local -A cached_map

    while IFS= read -r file_entry; do
        local file_path
        local file_hash
        file_path=$(echo "$file_entry" | jq -r '.file')
        file_hash=$(echo "$file_entry" | jq -r '.hash')
        current_map["$file_path"]="$file_hash"
    done < <(echo "$current_files" | jq -c '.[]')

    while IFS= read -r file_entry; do
        local file_path
        local file_hash
        file_path=$(echo "$file_entry" | jq -r '.file')
        file_hash=$(echo "$file_entry" | jq -r '.hash')
        cached_map["$file_path"]="$file_hash"
    done < <(echo "$cached_files" | jq -c '.[]')

    # Detect added files
    for path in "${!current_map[@]}"; do
        if [[ ! -v cached_map["$path"] ]]; then
            file_changes=$(echo "$file_changes" | jq \
                --arg path "$path" \
                --arg status "added" \
                '. += [{"file": $path, "status": $status}]')
        fi
    done

    # Detect removed files
    for path in "${!cached_map[@]}"; do
        if [[ ! -v current_map["$path"] ]]; then
            file_changes=$(echo "$file_changes" | jq \
                --arg path "$path" \
                --arg status "removed" \
                '. += [{"file": $path, "status": $status}]')
        fi
    done

    # Detect modified files
    for path in "${!current_map[@]}"; do
        if [[ -v cached_map["$path"] ]]; then
            if [[ "${current_map[$path]}" != "${cached_map[$path]}" ]]; then
                file_changes=$(echo "$file_changes" | jq \
                    --arg path "$path" \
                    --arg status "modified" \
                    --arg old_hash "${cached_map[$path]}" \
                    --arg new_hash "${current_map[$path]}" \
                    '. += [{"file": $path, "status": $status, "old_hash": $old_hash, "new_hash": $new_hash}]')
            fi
        fi
    done

    echo "$file_changes" | jq -c '.'
}

# Get transitive dependencies for a module (simplified)
get_module_dependencies() {
    local module="$1"

    # Read from pom.xml to find YAWL dependencies
    if [[ -f "${REPO_ROOT}/${module}/pom.xml" ]]; then
        grep -oP '(?<=<artifactId>yawl-[^<]+)' "${REPO_ROOT}/${module}/pom.xml" 2>/dev/null | sort -u || true
    fi
}

# ============================================================================
# Main Execution
# ============================================================================

main() {
    local action="${1:---all}"

    case "$action" in
        --all)
            # Detect changes in all currently modified modules
            printf "${C_CYAN}Detecting semantic changes in modified modules...${C_RESET}\n\n"

            # Get list of modified modules (from dx.sh logic)
            local changed_files
            changed_files=$(git diff --name-only HEAD 2>/dev/null || true)
            changed_files+=$'\n'$(git diff --name-only --cached 2>/dev/null || true)

            local -A modules_to_check
            while IFS= read -r file; do
                [[ -z "$file" ]] && continue
                # Extract module name from path
                local module
                module=$(echo "$file" | sed -E 's|^(yawl-[^/]+)/.*|\1|')
                if [[ -n "$module" && "$module" != "$file" ]]; then
                    modules_to_check["$module"]=1
                fi
            done <<< "$changed_files"

            # Analyze each modified module
            local results="[]"
            local semantic_count=0
            local text_only_count=0

            for module in "${!modules_to_check[@]}"; do
                log "Checking: $module"

                local current
                current=$(compute_current_hash "$module")
                if [[ "$current" == "null" ]]; then
                    log "Module not found or has no Java files: $module"
                    continue
                fi

                local cached
                cached=$(load_cached_hash "$module")

                local analysis
                analysis=$(analyze_changes "$current" "$cached" "$module")

                results=$(echo "$results" | jq \
                    --argjson analysis "$analysis" \
                    '.+=[($analysis)]')

                # Count by type
                local change_type
                change_type=$(echo "$analysis" | jq -r '.change_type')
                case "$change_type" in
                    semantic|new) ((semantic_count++)) ;;
                    text_only) ((text_only_count++)) ;;
                esac
            done

            # Output results
            local summary
            summary=$(cat <<EOF
{
  "semantic_changes": $semantic_count,
  "text_only_changes": $text_only_count,
  "total_checked": $((semantic_count + text_only_count)),
  "modules": $results
}
EOF
)

            echo "$summary" | jq '.'

            # Print summary
            echo ""
            printf "${C_GREEN}Semantic changes: %d module(s)${C_RESET} | ${C_YELLOW}Text-only: %d module(s)${C_RESET}\n" \
                "$semantic_count" "$text_only_count"
            ;;

        --graph)
            # Output impact graph showing which dependencies are affected
            printf "${C_CYAN}Computing semantic change impact graph...${C_RESET}\n\n"
            error "Impact graph feature not yet implemented"
            exit 1
            ;;

        *)
            # Check specific module
            local module="$action"

            if [[ ! -d "${REPO_ROOT}/${module}" ]]; then
                error "Module not found: $module"
                exit 1
            fi

            printf "${C_CYAN}Analyzing semantic changes for: ${module}${C_RESET}\n"

            local current
            current=$(compute_current_hash "$module")
            if [[ "$current" == "null" ]]; then
                error "Cannot compute semantic hash for $module"
                exit 1
            fi

            local cached
            cached=$(load_cached_hash "$module")

            local analysis
            analysis=$(analyze_changes "$current" "$cached" "$module")

            echo ""
            echo "$analysis" | jq '.'

            # Print readable summary
            local change_type
            local must_recompile
            change_type=$(echo "$analysis" | jq -r '.change_type')
            must_recompile=$(echo "$analysis" | jq -r '.must_recompile')

            echo ""
            case "$change_type" in
                new)
                    printf "${C_YELLOW}!${C_RESET} No previous cache for ${module} (first run)\n"
                    printf "   Action: Will recompile\n"
                    ;;
                text_only)
                    printf "${C_GREEN}✓${C_RESET} ${module} has no semantic changes (formatting only)\n"
                    printf "   Action: Can skip recompile (cache hit)\n"
                    ;;
                semantic)
                    printf "${C_RED}✗${C_RESET} ${module} has semantic changes\n"
                    printf "   Action: Must recompile\n"
                    ;;
            esac
            ;;
    esac
}

main "$@"
